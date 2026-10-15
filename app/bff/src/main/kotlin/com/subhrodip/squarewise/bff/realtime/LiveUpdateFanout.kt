package com.subhrodip.squarewise.bff.realtime

import com.subhrodip.squarewise.bff.errors.BffDomainException
import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import com.subhrodip.squarewise.bff.realtime.LiveSubscription
import com.subhrodip.squarewise.bff.realtime.LiveUpdate

import com.subhrodip.squarewise.ids.generation.UuidGenerator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-process fanout hub for group real-time invalidations.
 *
 * Manages the lifecycle of [LiveSubscription] slots per user/group pair, enforces
 * per-user subscription limits, and publishes [GroupInvalidation] events to a reactive
 * multicast sink that active GraphQL subscriptions consume.
 *
 * SEC-003: Each subscription receives an independent [Sinks.One] revocation signal.
 * When [revokeUser] or [revokeUserFromGroup] removes a subscription entry, it completes
 * the corresponding signal so that any downstream [Flux] using [revocationSignal] will
 * terminate automatically via `takeUntilOther`.
 */
class LiveUpdateFanout(
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val clock: Clock = Clock.systemUTC(),
    private val subscriptionTtl: Duration = DEFAULT_SUBSCRIPTION_TTL,
    private val maxSubscriptionsPerUser: Int = DEFAULT_MAX_SUBSCRIPTIONS_PER_USER
) {
    private val subscriptions = ConcurrentHashMap<String, Subscriber>()
    private val groupSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()
    private val userSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()

    /** Per-subscription revocation signals: completing the sink terminates the consumer Flux. */
    private val revocationSignals = ConcurrentHashMap<String, Sinks.One<Unit>>()
    private val subscriptionInvalidations = ConcurrentHashMap<String, SubscriptionInvalidationChannel>()

    /** Delivers only new invalidations to subscribers of this shared stream. */
    val invalidationSink: Sinks.Many<GroupInvalidation> =
        Sinks.many().multicast().directAllOrNothing()

    fun emitInvalidation(groupId: String, revision: Long): GroupInvalidation =
        emitInvalidation(groupId, revision, UuidGenerator.next().toString())

    fun emitInvalidation(groupId: String, revision: Long, changeId: String): GroupInvalidation {
        val invalidation = GroupInvalidation(groupId, revision, changeId)
        invalidationSink.tryEmitNext(invalidation)
        val now = Instant.now(clock)
        val targetSubIds = groupSubscriptions[groupId]?.toList().orEmpty()
        for (subscriptionId in targetSubIds) {
            val subscriber = subscriptions[subscriptionId] ?: continue
            if (!now.isBefore(subscriber.subscription.expiresAt)) {
                unsubscribe(subscriptionId)
            } else {
                val admitted = subscriptionInvalidations[subscriptionId]?.emit(invalidation) == true
                if (!admitted) {
                    // The snapshot is no longer a safe baseline; force a fresh subscription snapshot.
                    unsubscribe(subscriptionId)
                }
            }
        }
        return invalidation
    }

    fun invalidations(): Flux<GroupInvalidation> = invalidationSink.asFlux()

    /**
     * Returns the bounded event window admitted for one active subscription.
     * Events emitted before [subscriptionId] was admitted are never replayed. If
     * the snapshot cannot keep up with the bounded window, the subscription ends
     * so the client must establish a fresh snapshot.
     */
    fun invalidations(subscriptionId: String): Flux<GroupInvalidation> =
        subscriptionInvalidations[subscriptionId]?.flux() ?: Flux.empty()

    /**
     * Returns a [Mono] that completes (with no item) when this subscription is revoked.
     *
     * GraphQL subscription controllers should compose this with `takeUntilOther` on the
     * event stream so the subscriber's Flux terminates immediately upon membership loss.
     *
     * @param subscriptionId the [LiveSubscription.id] obtained from [subscribe]
     */
    fun revocationSignal(subscriptionId: String): Mono<Unit> =
        revocationSignals[subscriptionId]?.asMono() ?: Mono.empty()

    init {
        require(queueCapacity > 0) { "queueCapacity must be positive" }
        require(!subscriptionTtl.isZero && !subscriptionTtl.isNegative) { "subscriptionTtl must be positive" }
        require(maxSubscriptionsPerUser > 0) { "maxSubscriptionsPerUser must be positive" }
    }

    private val userLocks = Array(64) { Any() }
    private fun userLock(userId: String): Any = userLocks[Math.floorMod(userId.hashCode(), userLocks.size)]

    fun subscribe(userId: String, groupId: String): LiveSubscription {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(groupId.isNotBlank()) { "groupId must not be blank" }
        val now = Instant.now(clock)
        return synchronized(userLock(userId)) {
            for (subId in userSubscriptions[userId]?.toList().orEmpty()) {
                val sub = subscriptions[subId]
                if (sub != null && !now.isBefore(sub.subscription.expiresAt)) {
                    unsubscribe(subId)
                }
            }
            val currentSubs = userSubscriptions[userId]
            if ((currentSubs?.size ?: 0) >= maxSubscriptionsPerUser) {
                throw BffDomainException(BffErrors.SUBSCRIPTION_LIMIT_EXCEEDED, "subscription limit exceeded")
            }
            val subscription = LiveSubscription(
                UuidGenerator.next().toString(), userId, groupId, now.plus(subscriptionTtl)
            )
            subscriptionInvalidations[subscription.id] =
                SubscriptionInvalidationChannel(queueCapacity)
            subscriptions[subscription.id] = Subscriber(subscription, ArrayDeque())
            revocationSignals[subscription.id] = Sinks.one()
            userSubscriptions.compute(userId) { _, set ->
                (set ?: ConcurrentHashMap.newKeySet()).apply { add(subscription.id) }
            }
            groupSubscriptions.compute(groupId) { _, set ->
                (set ?: ConcurrentHashMap.newKeySet()).apply { add(subscription.id) }
            }
            subscription
        }
    }

    fun unsubscribe(subscriptionId: String) {
        val subscriber = subscriptions.remove(subscriptionId)
        revocationSignals.remove(subscriptionId)?.tryEmitEmpty()
        subscriptionInvalidations.remove(subscriptionId)?.complete()
        if (subscriber != null) {
            val sub = subscriber.subscription
            userSubscriptions.computeIfPresent(sub.userId) { _, set ->
                set.remove(subscriptionId)
                if (set.isEmpty()) null else set
            }
            groupSubscriptions.computeIfPresent(sub.groupId) { _, set ->
                set.remove(subscriptionId)
                if (set.isEmpty()) null else set
            }
        }
    }

    fun revokeUser(userId: String): Int {
        require(userId.isNotBlank()) { "userId must not be blank" }
        return synchronized(userLock(userId)) {
            val targetIds = userSubscriptions[userId]?.toList().orEmpty()
            var revoked = 0
            for (id in targetIds) {
                if (subscriptions.containsKey(id)) {
                    unsubscribe(id)
                    revoked++
                }
            }
            revoked
        }
    }

    /**
     * Revokes only the removed subject's subscriptions for the affected group.
     *
     * Completes the corresponding revocation signal for each removed subscription so
     * that any active GraphQL subscription stream terminates immediately.
     *
     * @param userId  the subject identifier of the removed member
     * @param groupId the group from which the member was removed
     * @return the number of subscription slots that were revoked
     */
    fun revokeUserFromGroup(userId: String, groupId: String): Int {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(groupId.isNotBlank()) { "groupId must not be blank" }
        return synchronized(userLock(userId)) {
            val targetIds = userSubscriptions[userId]?.toList().orEmpty()
            var revoked = 0
            for (id in targetIds) {
                val subscriber = subscriptions[id]
                if (subscriber != null && subscriber.subscription.groupId == groupId) {
                    unsubscribe(id)
                    revoked++
                }
            }
            revoked
        }
    }

    fun publish(update: LiveUpdate): Int {
        require(update.groupId.isNotBlank()) { "groupId must not be blank" }
        require(update.revision >= 0) { "revision must not be negative" }
        val now = Instant.now(clock)
        var delivered = 0
        val targetIds = groupSubscriptions[update.groupId]?.toList().orEmpty()
        for (id in targetIds) {
            val subscriber = subscriptions[id] ?: continue
            if (!now.isBefore(subscriber.subscription.expiresAt)) {
                unsubscribe(id)
                continue
            }
            if (subscriber.offer(update, queueCapacity)) {
                delivered++
            }
        }
        return delivered
    }

    fun poll(subscriptionId: String): LiveUpdate? {
        val subscriber = subscriptions[subscriptionId] ?: return null
        if (!Instant.now(clock).isBefore(subscriber.subscription.expiresAt)) {
            unsubscribe(subscriptionId)
            return null
        }
        return subscriber.poll()
    }

    fun pendingCount(subscriptionId: String): Int {
        val subscriber = subscriptions[subscriptionId] ?: return 0
        if (!Instant.now(clock).isBefore(subscriber.subscription.expiresAt)) {
            unsubscribe(subscriptionId)
            return 0
        }
        return subscriber.size()
    }

    /** Sweeps all expired subscriptions from the global index. */
    fun removeExpired() {
        val now = Instant.now(clock)
        subscriptions.entries.removeIf { entry ->
            val expired = !now.isBefore(entry.value.subscription.expiresAt)
            if (expired) {
                val subId = entry.key
                val sub = entry.value.subscription
                revocationSignals.remove(subId)?.tryEmitEmpty()
                subscriptionInvalidations.remove(subId)?.complete()
                userSubscriptions.computeIfPresent(sub.userId) { _, set ->
                    set.remove(subId)
                    if (set.isEmpty()) null else set
                }
                groupSubscriptions.computeIfPresent(sub.groupId) { _, set ->
                    set.remove(subId)
                    if (set.isEmpty()) null else set
                }
            }
            expired
        }
    }

    private class Subscriber(val subscription: LiveSubscription, private val queue: ArrayDeque<LiveUpdate>) {
        @Synchronized
        fun offer(update: LiveUpdate, capacity: Int): Boolean {
            if (queue.size >= capacity) return false
            queue.addLast(update)
            return true
        }

        @Synchronized
        fun poll(): LiveUpdate? = if (queue.isEmpty()) null else queue.removeFirst()

        @Synchronized
        fun size(): Int = queue.size
    }

    /** Bounded per-subscription channel that exposes overflow before a subscriber attaches. */
    private class SubscriptionInvalidationChannel(private val capacity: Int) {
        private val pending = AtomicInteger()
        private val sink: Sinks.Many<GroupInvalidation> =
            Sinks.many().unicast().onBackpressureBuffer(ArrayBlockingQueue(capacity))

        fun flux(): Flux<GroupInvalidation> = sink.asFlux().doOnNext { pending.decrementAndGet() }

        fun emit(invalidation: GroupInvalidation): Boolean {
            if (!reserve()) return false
            return when (sink.tryEmitNext(invalidation)) {
                Sinks.EmitResult.OK, Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER -> true
                else -> {
                    pending.decrementAndGet()
                    false
                }
            }
        }

        fun complete() {
            sink.tryEmitComplete()
        }

        private fun reserve(): Boolean {
            while (true) {
                val current = pending.get()
                if (current >= capacity) return false
                if (pending.compareAndSet(current, current + 1)) return true
            }
        }
    }

    companion object {
        const val DEFAULT_QUEUE_CAPACITY = 64
        const val DEFAULT_MAX_SUBSCRIPTIONS_PER_USER = 20
        val DEFAULT_SUBSCRIPTION_TTL: Duration = Duration.ofMinutes(30)
    }
}
