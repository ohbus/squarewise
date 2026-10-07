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
import java.util.concurrent.ConcurrentHashMap

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

    /** Per-subscription revocation signals: completing the sink terminates the consumer Flux. */
    private val revocationSignals = ConcurrentHashMap<String, Sinks.One<Void>>()

    private val admissionLock = Any()
    val invalidationSink: Sinks.Many<GroupInvalidation> =
        Sinks.many().multicast().directBestEffort()

    fun emitInvalidation(groupId: String, revision: Long): GroupInvalidation =
        emitInvalidation(groupId, revision, UuidGenerator.next().toString())

    fun emitInvalidation(groupId: String, revision: Long, changeId: String): GroupInvalidation {
        val invalidation = GroupInvalidation(groupId, revision, changeId)
        invalidationSink.tryEmitNext(invalidation)
        return invalidation
    }

    fun invalidations(): Flux<GroupInvalidation> = invalidationSink.asFlux()

    /**
     * Returns a [Mono] that completes (with no item) when this subscription is revoked.
     *
     * GraphQL subscription controllers should compose this with `takeUntilOther` on the
     * event stream so the subscriber's Flux terminates immediately upon membership loss.
     *
     * @param subscriptionId the [LiveSubscription.id] obtained from [subscribe]
     */
    fun revocationSignal(subscriptionId: String): Mono<Void> =
        revocationSignals[subscriptionId]?.asMono() ?: Mono.empty()

    init {
        require(queueCapacity > 0) { "queueCapacity must be positive" }
        require(!subscriptionTtl.isZero && !subscriptionTtl.isNegative) { "subscriptionTtl must be positive" }
        require(maxSubscriptionsPerUser > 0) { "maxSubscriptionsPerUser must be positive" }
    }

    fun subscribe(userId: String, groupId: String): LiveSubscription {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(groupId.isNotBlank()) { "groupId must not be blank" }
        return synchronized(admissionLock) {
            removeExpired()
            if (subscriptions.values.count { it.subscription.userId == userId } >= maxSubscriptionsPerUser) {
                throw BffDomainException(BffErrors.SUBSCRIPTION_LIMIT_EXCEEDED, "subscription limit exceeded")
            }
            val subscription = LiveSubscription(
                UuidGenerator.next().toString(), userId, groupId, Instant.now(clock).plus(subscriptionTtl)
            )
            subscriptions[subscription.id] = Subscriber(subscription, ArrayDeque())
            revocationSignals[subscription.id] = Sinks.one()
            subscription
        }
    }

    fun unsubscribe(subscriptionId: String) {
        subscriptions.remove(subscriptionId)
        revocationSignals.remove(subscriptionId)?.tryEmitEmpty()
    }

    fun revokeUser(userId: String): Int {
        require(userId.isNotBlank()) { "userId must not be blank" }
        var revoked = 0
        subscriptions.entries.removeIf {
            val matches = it.value.subscription.userId == userId
            if (matches) {
                revoked++
                revocationSignals.remove(it.key)?.tryEmitEmpty()
            }
            matches
        }
        return revoked
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
        var revoked = 0
        subscriptions.entries.removeIf {
            val subscription = it.value.subscription
            val matches = subscription.userId == userId && subscription.groupId == groupId
            if (matches) {
                revoked++
                revocationSignals.remove(it.key)?.tryEmitEmpty()
            }
            matches
        }
        return revoked
    }

    fun publish(update: LiveUpdate): Int {
        require(update.groupId.isNotBlank()) { "groupId must not be blank" }
        require(update.revision >= 0) { "revision must not be negative" }
        removeExpired()
        var delivered = 0
        subscriptions.values.forEach { subscriber ->
            if (subscriber.subscription.groupId == update.groupId && subscriber.offer(update, queueCapacity)) {
                delivered++
            }
        }
        return delivered
    }

    fun poll(subscriptionId: String): LiveUpdate? {
        removeExpired()
        return subscriptions[subscriptionId]?.poll()
    }

    fun pendingCount(subscriptionId: String): Int {
        removeExpired()
        return subscriptions[subscriptionId]?.size() ?: 0
    }

    private fun removeExpired() {
        val now = Instant.now(clock)
        subscriptions.entries.removeIf {
            val expired = !now.isBefore(it.value.subscription.expiresAt)
            if (expired) revocationSignals.remove(it.key)?.tryEmitEmpty()
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

    companion object {
        const val DEFAULT_QUEUE_CAPACITY = 64
        const val DEFAULT_MAX_SUBSCRIPTIONS_PER_USER = 20
        val DEFAULT_SUBSCRIPTION_TTL: Duration = Duration.ofMinutes(30)
    }
}
