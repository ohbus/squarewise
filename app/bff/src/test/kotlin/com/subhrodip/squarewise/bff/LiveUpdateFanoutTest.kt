package com.subhrodip.squarewise.bff

import com.subhrodip.squarewise.bff.transport.ExpenseCoreGateway
import com.subhrodip.squarewise.bff.transport.AccountsGateway
import com.subhrodip.squarewise.bff.messaging.model.BffEventEnvelope
import com.subhrodip.squarewise.bff.messaging.model.ConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.DuplicateConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.ProcessedConsumptionResult
import com.subhrodip.squarewise.bff.messaging.service.BffEventConsumer
import com.subhrodip.squarewise.bff.messaging.persistence.BffEventDeduplicator
import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import com.subhrodip.squarewise.bff.realtime.LiveUpdate
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException


import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
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
        assertThat(error.definition.legacyCode).isEqualTo("RATE_LIMITED")
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

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = now
    }
}
