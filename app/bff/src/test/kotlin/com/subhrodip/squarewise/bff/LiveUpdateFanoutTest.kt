package com.subhrodip.squarewise.bff

import com.subhrodip.squarewise.errors.code.CategoryCode

import com.subhrodip.squarewise.bff.transport.ExpenseCoreGateway
import com.subhrodip.squarewise.bff.transport.AccountsGateway
import com.subhrodip.squarewise.bff.messaging.model.BffEventEnvelope
import com.subhrodip.squarewise.bff.messaging.model.ConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.DuplicateConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.ProcessedConsumptionResult
import com.subhrodip.squarewise.bff.messaging.service.BffEventConsumer
import com.subhrodip.squarewise.bff.messaging.persistence.BffEventDeduplicator
import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import com.subhrodip.squarewise.bff.realtime.LiveSubscription
import com.subhrodip.squarewise.bff.realtime.LiveUpdate
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import java.time.ZoneId


import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class LiveUpdateFanoutTest {
    @Test
    fun `exposes the invalidation stream and documented defaults`() {
        val fanout = LiveUpdateFanout()

        assertThat(fanout.invalidationSink).isNotNull
        assertThat(LiveUpdateFanout.DEFAULT_QUEUE_CAPACITY).isEqualTo(64)
        assertThat(LiveUpdateFanout.DEFAULT_MAX_SUBSCRIPTIONS_PER_USER).isEqualTo(20)
        assertThat(LiveUpdateFanout.DEFAULT_SUBSCRIPTION_TTL).isEqualTo(Duration.ofMinutes(30))
    }

    @Test
    fun `bounds subscriptions per user`() {
        val fanout = LiveUpdateFanout(maxSubscriptionsPerUser = 1)
        fanout.subscribe("user-1", "group-1")

        val error = assertThrows<SquarewiseException> { fanout.subscribe("user-1", "group-2") }
        assertThat(error.definition.category).isEqualTo(CategoryCode.RATE_LIMIT_EXCEEDED)
    }

    @Test
    fun `concurrent subscription admission cannot exceed user limit`() {
        val fanout = LiveUpdateFanout(maxSubscriptionsPerUser = 1)
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val attempts = (1..8).map {
                pool.submit<Boolean> {
                    start.await()
                    runCatching { fanout.subscribe("user-1", "group-$it") }.isSuccess
                }
            }
            start.countDown()

            assertThat(attempts.count { it.get() }).isEqualTo(1)
        } finally {
            pool.shutdownNow()
        }
    }
    private val update = LiveUpdate("group-1", 7)

    @Test
    fun `delivers update only to subscriptions for the group`() {
        val fanout = LiveUpdateFanout()
        val matching = fanout.subscribe("user-1", "group-1")
        val other = fanout.subscribe("user-2", "group-2")

        assertThat(fanout.publish(update)).isEqualTo(1)
        assertThat(fanout.poll(matching.id)).isEqualTo(update)
        assertThat(fanout.poll(other.id)).isNull()
    }

    @Test
    fun `bounds slow subscriber queue and preserves queued order`() {
        val fanout = LiveUpdateFanout(queueCapacity = 2)
        val subscription = fanout.subscribe("user-1", "group-1")

        assertThat(fanout.publish(update)).isEqualTo(1)
        assertThat(fanout.publish(update.copy(revision = 8))).isEqualTo(1)
        assertThat(fanout.publish(update.copy(revision = 9))).isZero()
        assertThat(fanout.pendingCount(subscription.id)).isEqualTo(2)
        assertThat(fanout.poll(subscription.id)?.revision).isEqualTo(7)
        assertThat(fanout.poll(subscription.id)?.revision).isEqualTo(8)
    }

    @Test
    fun `unsubscribe drops future delivery`() {
        val fanout = LiveUpdateFanout()
        val subscription = fanout.subscribe("user-1", "group-1")
        fanout.unsubscribe(subscription.id)

        assertThat(fanout.publish(update)).isZero()
        assertThat(fanout.poll(subscription.id)).isNull()
    }

    @Test
    fun `unknown subscriptions expose empty streams and unsubscribe is idempotent`() {
        val fanout = LiveUpdateFanout()

        assertThat(fanout.invalidations("unknown-subscription").collectList().block()).isEmpty()
        fanout.unsubscribe("unknown-subscription")
        fanout.unsubscribe("unknown-subscription")
    }

    @Test
    fun `rejects invalid inputs`() {
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(0) }
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(subscriptionTtl = Duration.ZERO) }
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(subscriptionTtl = Duration.ofSeconds(-1)) }
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(maxSubscriptionsPerUser = 0) }
        val fanout = LiveUpdateFanout()
        assertThrows<IllegalArgumentException> { fanout.subscribe("", "group-1") }
        assertThrows<IllegalArgumentException> { fanout.subscribe("user-1", "") }
        assertThrows<IllegalArgumentException> { fanout.publish(LiveUpdate("group-1", -1)) }
        assertThrows<IllegalArgumentException> { fanout.publish(LiveUpdate("", 1)) }
        assertThrows<IllegalArgumentException> { fanout.revokeUserFromGroup("", "group-1") }
        assertThrows<IllegalArgumentException> { fanout.revokeUserFromGroup("user-1", "") }
    }

    @Test
    fun `expires subscriptions before delivery`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofSeconds(1))
        val subscription = fanout.subscribe("user-1", "group-1")

        assertThat(subscription.expiresAt).isEqualTo(Instant.parse("2026-01-01T00:00:01Z"))
        assertThat(fanout.publish(update)).isEqualTo(1)

        var terminated = false
        fanout.revocationSignal(subscription.id).doOnTerminate { terminated = true }.subscribe()
        clock.now = subscription.expiresAt

        assertThat(fanout.publish(update)).isZero()
        assertThat(fanout.pendingCount(subscription.id)).isZero()
        assertThat(terminated).isTrue()
    }

    @Test
    fun `expires reactive subscriptions before invalidation delivery`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofSeconds(1))
        val subscription = fanout.subscribe("user-1", "group-1")
        val events = mutableListOf<GroupInvalidation>()
        var terminated = false

        fanout.invalidations(subscription.id).subscribe { events.add(it) }
        fanout.revocationSignal(subscription.id)
            .doOnTerminate { terminated = true }
            .subscribe()
        clock.now = subscription.expiresAt

        fanout.emitInvalidation("group-1", 8L, "expired")

        assertThat(events).isEmpty()
        assertThat(terminated).isTrue()
    }

    @Test
    fun `expires a subscription if it reaches its deadline during invalidation delivery`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val expiry = start.plusSeconds(1)
        val clock = SequenceClock(start, expiry)
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofSeconds(1))
        val subscription = fanout.subscribe("user-1", "group-1")
        var terminated = false

        fanout.revocationSignal(subscription.id)
            .doOnTerminate { terminated = true }
            .subscribe()

        fanout.emitInvalidation("group-1", 8L, "expired-during-delivery")

        assertThat(terminated).isTrue()
        assertThat(fanout.invalidations(subscription.id).collectList().block()).isEmpty()
    }

    @Test
    fun `terminates a subscription when its snapshot buffer overflows`() {
        val fanout = LiveUpdateFanout(queueCapacity = 1)
        val subscription = fanout.subscribe("user-1", "group-1")
        var terminated = false

        fanout.revocationSignal(subscription.id)
            .doOnTerminate { terminated = true }
            .subscribe()

        fanout.emitInvalidation("group-1", 1L, "first")
        fanout.emitInvalidation("group-1", 2L, "overflow")

        assertThat(terminated).isTrue()
        assertThat(fanout.invalidations(subscription.id).collectList().block()).isEmpty()
    }

    @Test
    fun `terminates a subscription when its snapshot stream is cancelled`() {
        val fanout = LiveUpdateFanout()
        val subscription = fanout.subscribe("user-1", "group-1")
        val snapshotStream = fanout.invalidations(subscription.id).subscribe()
        var terminated = false

        fanout.revocationSignal(subscription.id)
            .doOnTerminate { terminated = true }
            .subscribe()
        snapshotStream.dispose()

        fanout.emitInvalidation("group-1", 1L, "cancelled")

        assertThat(terminated).isTrue()
        assertThat(fanout.invalidations(subscription.id).collectList().block()).isEmpty()
    }

    @Test
    fun `revokes every subscription for a user`() {
        val fanout = LiveUpdateFanout()
        fanout.subscribe("user-1", "group-1")
        fanout.subscribe("user-1", "group-2")
        fanout.subscribe("user-2", "group-1")

        assertThat(fanout.revokeUser("user-1")).isEqualTo(2)
        assertThat(fanout.publish(update)).isEqualTo(1)
        assertThrows<IllegalArgumentException> { fanout.revokeUser(" ") }
    }

    @Test
    fun `unknown subscription has no pending updates`() {
        assertThat(LiveUpdateFanout().pendingCount("unknown-subscription")).isZero()
    }

    @Test
    fun `emitInvalidation publishes to reactive invalidations flux`() {
        val fanout = LiveUpdateFanout()
        val events = mutableListOf<GroupInvalidation>()
        val disposable = fanout.invalidations().subscribe { events.add(it) }
        try {
            val invalidation = fanout.emitInvalidation("group-1", 10L, "change-abc")
            assertThat(invalidation.groupId).isEqualTo("group-1")
            assertThat(invalidation.revision).isEqualTo(10L)
            assertThat(invalidation.changeId).isEqualTo("change-abc")
            assertThat(events).hasSize(1)
            assertThat(events[0]).isEqualTo(invalidation)
        } finally {
            disposable.dispose()
        }
    }

    @Test
    fun `does not replay an invalidation emitted before a shared subscriber attaches`() {
        val fanout = LiveUpdateFanout()
        fanout.emitInvalidation("group-1", 11L, "change-before-subscribe")
        val events = mutableListOf<GroupInvalidation>()
        val disposable = fanout.invalidations().subscribe { events.add(it) }
        try {
            assertThat(events).isEmpty()

            val invalidation = fanout.emitInvalidation("group-1", 12L, "change-after-subscribe")
            assertThat(events).containsExactly(invalidation)
        } finally {
            disposable.dispose()
        }
    }

    @Test
    fun `subscription stream replays only events admitted after subscription`() {
        val fanout = LiveUpdateFanout()
        fanout.emitInvalidation("group-1", 11L, "change-before-subscribe")
        val subscription = fanout.subscribe("user-1", "group-1")
        fanout.emitInvalidation("group-1", 12L, "change-during-snapshot")

        assertThat(fanout.invalidations(subscription.id).blockFirst()).isEqualTo(
            GroupInvalidation("group-1", 12L, "change-during-snapshot")
        )
        fanout.unsubscribe(subscription.id)
    }

    @Test
    fun `subscription buffer ignores invalidations for other groups`() {
        val fanout = LiveUpdateFanout(queueCapacity = 1)
        val subscription = fanout.subscribe("user-1", "group-1")
        fanout.emitInvalidation("group-2", 11L, "other-group")
        fanout.emitInvalidation("group-1", 12L, "target-group")

        assertThat(fanout.invalidations(subscription.id).blockFirst()).isEqualTo(
            GroupInvalidation("group-1", 12L, "target-group")
        )
        fanout.unsubscribe(subscription.id)
    }

    // ---------------------------------------------------------------------------
    // Revocation signal tests (SEC-003)
    // ---------------------------------------------------------------------------

    @Test
    fun `revokeUserFromGroup completes per-subscription revocation signal`() {
        val fanout = LiveUpdateFanout()
        val subscription = fanout.subscribe("user-1", "group-1")

        var terminated = false
        fanout.revocationSignal(subscription.id)
            .doOnTerminate { terminated = true }
            .subscribe()

        assertThat(terminated).isFalse()
        fanout.revokeUserFromGroup("user-1", "group-1")
        assertThat(terminated).isTrue()
    }

    @Test
    fun `revokeUser completes revocation signals for all revoked subscriptions`() {
        val fanout = LiveUpdateFanout()
        val s1 = fanout.subscribe("user-1", "group-1")
        val s2 = fanout.subscribe("user-1", "group-2")
        val s3 = fanout.subscribe("user-2", "group-1")

        var t1 = false; var t2 = false; var t3 = false
        fanout.revocationSignal(s1.id).doOnTerminate { t1 = true }.subscribe()
        fanout.revocationSignal(s2.id).doOnTerminate { t2 = true }.subscribe()
        fanout.revocationSignal(s3.id).doOnTerminate { t3 = true }.subscribe()

        fanout.revokeUser("user-1")

        assertThat(t1).isTrue()
        assertThat(t2).isTrue()
        assertThat(t3).isFalse()
    }

    @Test
    fun `unsubscribe completes revocation signal`() {
        val fanout = LiveUpdateFanout()
        val subscription = fanout.subscribe("user-1", "group-1")

        var terminated = false
        fanout.revocationSignal(subscription.id)
            .doOnTerminate { terminated = true }
            .subscribe()

        fanout.unsubscribe(subscription.id)
        assertThat(terminated).isTrue()
    }

    @Test
    fun `revocationSignal returns empty mono for unknown subscription id`() {
        val fanout = LiveUpdateFanout()
        var completed = false
        fanout.revocationSignal("nonexistent-id")
            .doOnTerminate { completed = true }
            .subscribe()
        // Mono.empty() completes immediately
        assertThat(completed).isTrue()
    }

    @Test
    fun `revokeUserFromGroup only signals subscriptions matching user and group`() {
        val fanout = LiveUpdateFanout()
        val target = fanout.subscribe("user-1", "group-1")
        val otherGroup = fanout.subscribe("user-1", "group-2")
        val otherUser = fanout.subscribe("user-2", "group-1")

        var tTarget = false; var tOtherGroup = false; var tOtherUser = false
        fanout.revocationSignal(target.id).doOnTerminate { tTarget = true }.subscribe()
        fanout.revocationSignal(otherGroup.id).doOnTerminate { tOtherGroup = true }.subscribe()
        fanout.revocationSignal(otherUser.id).doOnTerminate { tOtherUser = true }.subscribe()

        assertThat(fanout.revokeUserFromGroup("user-1", "group-1")).isEqualTo(1)

        assertThat(tTarget).isTrue()
        assertThat(tOtherGroup).isFalse()
        assertThat(tOtherUser).isFalse()
    }

    @Test
    fun `poll and pendingCount return null and zero when subscription has expired`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val clock = MutableClock(start)
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofMinutes(5))

        val sub1 = fanout.subscribe("user-exp-1", "group-exp-1")
        val sub2 = fanout.subscribe("user-exp-2", "group-exp-2")
        fanout.publish(LiveUpdate("group-exp-1", 1))
        fanout.publish(LiveUpdate("group-exp-2", 1))

        assertThat(fanout.pendingCount(sub1.id)).isEqualTo(1)
        assertThat(fanout.pendingCount(sub2.id)).isEqualTo(1)

        // Advance past expiration
        clock.now = start.plus(Duration.ofMinutes(10))

        // sub1 triggers expiration via pendingCount
        assertThat(fanout.pendingCount(sub1.id)).isEqualTo(0)

        // sub2 triggers expiration via poll
        assertThat(fanout.poll(sub2.id)).isNull()
    }

    @Test
    fun `removeExpired removes all expired subscriptions and completes signals`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val clock = MutableClock(start)
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofMinutes(5))

        val sub1 = fanout.subscribe("user-exp-1", "group-exp-1")
        val sub2 = fanout.subscribe("user-exp-2", "group-exp-2")

        var sub1Revoked = false
        fanout.revocationSignal(sub1.id).doOnTerminate { sub1Revoked = true }.subscribe()

        // Advance time so sub1 and sub2 expire
        clock.now = start.plus(Duration.ofMinutes(10))

        fanout.removeExpired()

        assertThat(sub1Revoked).isTrue()
        assertThat(fanout.poll(sub1.id)).isNull()
        assertThat(fanout.poll(sub2.id)).isNull()
    }

    @Test
    fun `subscribe evicts expired subscriptions for user when admitting new subscription`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val clock = MutableClock(start)
        val fanout = LiveUpdateFanout(clock = clock, maxSubscriptionsPerUser = 1, subscriptionTtl = Duration.ofMinutes(5))

        val sub1 = fanout.subscribe("user-cap", "group-1")
        assertThat(sub1).isNotNull

        // Advance time so sub1 expires
        clock.now = start.plus(Duration.ofMinutes(10))

        // Subscribing now should evict sub1 instead of throwing limit exceeded
        val sub2 = fanout.subscribe("user-cap", "group-2")
        assertThat(sub2).isNotNull
        assertThat(fanout.poll(sub1.id)).isNull()
    }

    @Test
    fun `emitInvalidation drops and unsubscribes expired subscription`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val clock = MutableClock(start)
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofMinutes(5))

        val sub = fanout.subscribe("user-1", "group-1")
        clock.now = start.plus(Duration.ofMinutes(10))

        fanout.emitInvalidation("group-1", 1)
        assertThat(fanout.poll(sub.id)).isNull()
    }

    @Test
    fun `emitInvalidation unsubscribes when subscriber invalidation queue overflows`() {
        val fanout = LiveUpdateFanout(queueCapacity = 1)
        val sub = fanout.subscribe("user-1", "group-1")

        // First emit is admitted
        fanout.emitInvalidation("group-1", 1)
        // Second emit without consumer overflows the bounded sink buffer, evicting the subscription
        fanout.emitInvalidation("group-1", 2)
        assertThat(fanout.poll(sub.id)).isNull()
    }

    @Test
    fun `publish drops update and unsubscribes expired subscriber`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val clock = MutableClock(start)
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofMinutes(5))

        val sub = fanout.subscribe("user-1", "group-1")
        clock.now = start.plus(Duration.ofMinutes(10))

        val delivered = fanout.publish(LiveUpdate("group-1", 1))
        assertThat(delivered).isZero()
        assertThat(fanout.poll(sub.id)).isNull()
    }

    @Test
    fun `revokeUserFromGroup only revokes subscriptions for the specified group`() {
        val fanout = LiveUpdateFanout()
        val sub1 = fanout.subscribe("user-1", "group-1")
        val sub2 = fanout.subscribe("user-1", "group-2")

        val revoked = fanout.revokeUserFromGroup("user-1", "group-1")
        assertThat(revoked).isEqualTo(1)
        assertThat(fanout.poll(sub1.id)).isNull()
        assertThat(fanout.pendingCount(sub2.id)).isZero()
    }

    @Test
    fun `unsubscribe preserves other subscriptions for the same user and group`() {
        val fanout = LiveUpdateFanout(maxSubscriptionsPerUser = 5)
        val sub1 = fanout.subscribe("user-1", "group-1")
        val sub2 = fanout.subscribe("user-1", "group-1")

        fanout.unsubscribe(sub1.id)
        assertThat(fanout.publish(LiveUpdate("group-1", 1))).isEqualTo(1)
        assertThat(fanout.poll(sub2.id)).isNotNull()
    }

    @Test
    fun `unsubscribe on unknown subscription is safe no-op`() {
        val fanout = LiveUpdateFanout()
        fanout.unsubscribe("non-existent-sub")
    }

    @Test
    fun `invalidations and revocationSignal return empty reactive streams for unknown subscription`() {
        val fanout = LiveUpdateFanout()
        assertThat(fanout.invalidations("unknown").blockFirst(Duration.ofMillis(50))).isNull()
        assertThat(fanout.revocationSignal("unknown").block(Duration.ofMillis(50))).isNull()
    }

    @Test
    fun `emitInvalidation unexpired and expired subscriber handling`() {
        val clock = MutableClock(Instant.parse("2026-10-13T10:00:00Z"))
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofMinutes(10), queueCapacity = 1)
        val sub1 = fanout.subscribe("user-1", "group-1")
        fanout.subscribe("user-2", "group-1")

        // Advance clock so sub1 expires
        clock.now = Instant.parse("2026-10-13T10:15:00Z")
        fanout.emitInvalidation("group-1", 1L)

        // sub1 was expired so unsubscribed
        assertThat(fanout.poll(sub1.id)).isNull()
    }

    @Test
    fun `emitInvalidation drops subscriber when invalidation channel overflows`() {
        val clock = MutableClock(Instant.parse("2026-10-13T10:00:00Z"))
        val fanout = LiveUpdateFanout(clock = clock, queueCapacity = 1)
        val sub = fanout.subscribe("user-1", "group-1")

        // Fill channel
        fanout.emitInvalidation("group-1", 1L)
        // Second invalidation exceeds capacity and triggers forced unsubscribe
        fanout.emitInvalidation("group-1", 2L)

        assertThat(fanout.pendingCount(sub.id)).isZero()
    }

    @Test
    fun `subscribe evicts expired subscriptions of the user during admission`() {
        val clock = MutableClock(Instant.parse("2026-10-13T10:00:00Z"))
        val fanout = LiveUpdateFanout(clock = clock, maxSubscriptionsPerUser = 2, subscriptionTtl = Duration.ofMinutes(10))
        val sub1 = fanout.subscribe("user-1", "group-1")

        // Advance clock past ttl
        clock.now = Instant.parse("2026-10-13T10:15:00Z")
        val sub2 = fanout.subscribe("user-1", "group-2")

        assertThat(sub2).isNotNull
        // sub1 should have been cleaned up during subscribe
        assertThat(fanout.pendingCount(sub1.id)).isZero()
    }

    @Test
    fun `revokeUser and revokeUserFromGroup handle non-existent and mixed group subscriptions`() {
        val fanout = LiveUpdateFanout(maxSubscriptionsPerUser = 5)
        assertThat(fanout.revokeUser("missing-user")).isZero()
        assertThat(fanout.revokeUserFromGroup("missing-user", "group-1")).isZero()

        val sub1 = fanout.subscribe("user-1", "group-1")
        val sub2 = fanout.subscribe("user-1", "group-2")

        // Revoke only from group-1
        val revoked = fanout.revokeUserFromGroup("user-1", "group-1")
        assertThat(revoked).isEqualTo(1)
        assertThat(fanout.pendingCount(sub1.id)).isZero()
        assertThat(fanout.publish(LiveUpdate("group-2", 5))).isEqualTo(1)
    }

    @Test
    fun `removeExpired cleanly sweeps expired while preserving unexpired across users and groups`() {
        val clock = MutableClock(Instant.parse("2026-10-13T10:00:00Z"))
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofMinutes(10))
        fanout.subscribe("user-1", "group-1")

        // Advance clock by 5 minutes, add second sub
        clock.now = Instant.parse("2026-10-13T10:05:00Z")
        fanout.subscribe("user-1", "group-1")
        fanout.subscribe("user-2", "group-2")

        // Advance clock to 10:12:00 -> subExpired expires, but active subs are still valid
        clock.now = Instant.parse("2026-10-13T10:12:00Z")
        fanout.removeExpired()

        assertThat(fanout.publish(LiveUpdate("group-1", 1))).isEqualTo(1)
        assertThat(fanout.publish(LiveUpdate("group-2", 1))).isEqualTo(1)
    }

    @Test
    fun `pendingCount on nonexistent subscription returns 0`() {
        val fanout = LiveUpdateFanout()
        assertThat(fanout.pendingCount("nonexistent")).isZero()
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `gracefully ignores phantom or dangling subscription entries in internal indexes`() {
        val fanout = LiveUpdateFanout(maxSubscriptionsPerUser = 5)
        val userSubsField = LiveUpdateFanout::class.java.getDeclaredField("userSubscriptions").apply { isAccessible = true }
        val userSubs = userSubsField.get(fanout) as ConcurrentHashMap<String, MutableSet<String>>
        userSubs.computeIfAbsent("user-phantom") { ConcurrentHashMap.newKeySet() }.add("phantom-id")

        val groupSubsField = LiveUpdateFanout::class.java.getDeclaredField("groupSubscriptions").apply { isAccessible = true }
        val groupSubs = groupSubsField.get(fanout) as ConcurrentHashMap<String, MutableSet<String>>
        groupSubs.computeIfAbsent("group-phantom") { ConcurrentHashMap.newKeySet() }.add("phantom-id")

        // emitInvalidation continues past null subscriber
        fanout.emitInvalidation("group-phantom", 1L)

        // revokeUser handles phantom id where subscriptions does not contain it
        assertThat(fanout.revokeUser("user-phantom")).isZero()

        // revokeUserFromGroup handles phantom id where subscriptions[id] is null
        userSubs.computeIfAbsent("user-phantom") { ConcurrentHashMap.newKeySet() }.add("phantom-id")
        assertThat(fanout.revokeUserFromGroup("user-phantom", "group-phantom")).isZero()

        // subscribe handles existing unexpired and phantom sub IDs in userSubscriptions
        userSubs.computeIfAbsent("user-phantom") { ConcurrentHashMap.newKeySet() }.add("phantom-id")
        val newSub = fanout.subscribe("user-phantom", "group-1")
        assertThat(newSub).isNotNull

        // calling subscribe again while previous is unexpired covers the false condition of !now.isBefore
        val secondSub = fanout.subscribe("user-phantom", "group-2")
        assertThat(secondSub).isNotNull
    }

    @Test
    fun `concurrent revokeUser and subscribe leaves no live subscriptions`() {
        val fanout = LiveUpdateFanout()
        val userId = "concurrent-user"
        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val subFuture = executor.submit<LiveSubscription> {
                startLatch.await()
                fanout.subscribe(userId, "group-1")
            }
            val revokeFuture = executor.submit {
                startLatch.await()
                while (!subFuture.isDone) {
                    fanout.revokeUser(userId)
                    Thread.yield()
                }
                fanout.revokeUser(userId)
            }
            startLatch.countDown()
            val sub = subFuture.get()
            revokeFuture.get()
            fanout.revokeUser(userId)

            assertThat(fanout.pendingCount(sub.id)).isZero()
            assertThat(fanout.poll(sub.id)).isNull()
            assertThat(fanout.revokeUser(userId)).isZero()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `validates constructor parameters`() {
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(queueCapacity = 0) }
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(subscriptionTtl = Duration.ZERO) }
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(subscriptionTtl = Duration.ofSeconds(-1)) }
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(maxSubscriptionsPerUser = 0) }
    }


    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private class SequenceClock(private vararg val instants: Instant) : Clock() {
        private var index = 0

        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instants.getOrElse(index++) { instants.last() }
    }
}
