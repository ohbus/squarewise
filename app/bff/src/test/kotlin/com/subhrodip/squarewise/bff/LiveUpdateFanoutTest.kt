package com.subhrodip.squarewise.bff

import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LiveUpdateFanoutTest {
    @Test
    fun `emits invalidations only to the subscribed group`() {
        val fanout = LiveUpdateFanout()
        val matching = fanout.subscribe("user-1", "group-1")
        fanout.subscribe("user-2", "group-2")
        fanout.emitInvalidation("group-2", 1)
        fanout.emitInvalidation("group-1", 2)

        val result = fanout.invalidations(matching.id).take(1).blockFirst(Duration.ofSeconds(1))!!
        assertThat(result.groupId).isEqualTo("group-1")
        assertThat(result.revision).isEqualTo(2)
    }

    @Test
    fun `expiry signal removes an idle subscription`() {
        val fanout = LiveUpdateFanout(maxSubscriptionsPerUser = 1, subscriptionTtl = Duration.ofMillis(10))
        val subscription = fanout.subscribe("user-1", "group-1")

        fanout.expirySignal(subscription.id).block(Duration.ofSeconds(1))

        fanout.subscribe("user-1", "group-2")
    }

    @Test
    fun `subscribe removes expired entries before enforcing the user limit`() {
        val clock = MutableClock()
        val fanout = LiveUpdateFanout(
            clock = clock,
            subscriptionTtl = Duration.ofSeconds(1),
            maxSubscriptionsPerUser = 1
        )
        val expired = fanout.subscribe("user-1", "group-1")
        clock.advance(Duration.ofSeconds(2))

        val replacement = fanout.subscribe("user-1", "group-2")

        assertThat(replacement.groupId).isEqualTo("group-2")
        assertThat(fanout.revocationSignal(expired.id).block(Duration.ofSeconds(1))).isNull()
    }

    @Test
    fun `shared invalidation stream publishes emitted changes`() {
        val fanout = LiveUpdateFanout()
        val received = mutableListOf<GroupInvalidation>()
        val subscription = fanout.invalidations().subscribe(received::add)

        try {
            val emitted = fanout.emitInvalidation("group-1", 1, "change-1")
            assertThat(received).containsExactly(emitted)
        } finally {
            subscription.dispose()
        }
    }

    @Test
    fun `expired subscriptions are revoked when their group emits an invalidation`() {
        val clock = MutableClock()
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofSeconds(1))
        val subscription = fanout.subscribe("user-1", "group-1")

        clock.advance(Duration.ofSeconds(2))
        fanout.emitInvalidation("group-1", 1, "change-1")

        assertThat(fanout.revocationSignal(subscription.id).block(Duration.ofSeconds(1))).isNull()
    }

    @Test
    fun `revocation removes the subscription and completes its signal`() {
        val fanout = LiveUpdateFanout(maxSubscriptionsPerUser = 1)
        val subscription = fanout.subscribe("user-1", "group-1")

        assertThat(fanout.revokeUserFromGroup("user-1", "group-1")).isEqualTo(1)
        assertThat(fanout.revocationSignal(subscription.id).block(Duration.ofSeconds(1))).isNull()
        fanout.subscribe("user-1", "group-2")
    }

    @Test
    fun `revokeUser removes every active subscription for the user`() {
        val fanout = LiveUpdateFanout()
        val first = fanout.subscribe("user-1", "group-1")
        val second = fanout.subscribe("user-1", "group-2")

        assertThat(fanout.revokeUser("user-1")).isEqualTo(2)
        assertThat(fanout.revocationSignal(first.id).block(Duration.ofSeconds(1))).isNull()
        assertThat(fanout.revocationSignal(second.id).block(Duration.ofSeconds(1))).isNull()
    }

    @Test
    fun `rejects invalid configuration`() {
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(0) }
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(subscriptionTtl = Duration.ZERO) }
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(maxSubscriptionsPerUser = 0) }
    }

    @Test
    fun `rejects blank subscription and revocation identifiers`() {
        val fanout = LiveUpdateFanout()

        assertThrows<IllegalArgumentException> { fanout.subscribe("", "group-1") }
        assertThrows<IllegalArgumentException> { fanout.subscribe("user-1", "") }
        assertThrows<IllegalArgumentException> { fanout.revokeUser("") }
        assertThrows<IllegalArgumentException> { fanout.revokeUserFromGroup("", "group-1") }
        assertThrows<IllegalArgumentException> { fanout.revokeUserFromGroup("user-1", "") }
        assertThat(fanout.expirySignal("missing").block(Duration.ofSeconds(1))).isNull()

        val clock = MutableClock()
        val expiring = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofSeconds(1))
        val subscription = expiring.subscribe("user-1", "group-1")
        clock.advance(Duration.ofSeconds(2))
        expiring.expirySignal(subscription.id).block(Duration.ofSeconds(1))
    }

    @Test
    fun `expired subscriptions are reclaimed by bounded sweeps`() {
        val clock = MutableClock()
        val fanout = LiveUpdateFanout(
            clock = clock,
            subscriptionTtl = Duration.ofSeconds(1),
            maxSubscriptionsPerUser = 2
        )
        val first = fanout.subscribe("user-1", "group-1")
        fanout.subscribe("user-1", "group-2")

        clock.advance(Duration.ofSeconds(2))
        fanout.sweepExpired()

        assertThat(fanout.revocationSignal(first.id).block(Duration.ofSeconds(1))).isNull()
        fanout.subscribe("user-1", "group-3")
    }

    @Test
    fun `expiry sweep releases user and group indexes`() {
        val clock = MutableClock()
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofSeconds(1))
        val expired = fanout.subscribe("user-1", "group-1")

        clock.advance(Duration.ofSeconds(2))
        fanout.sweepExpired()

        assertThat(fanout.revokeUser("user-1")).isZero()
        assertThat(fanout.revokeUserFromGroup("user-1", "group-1")).isZero()

        val active = fanout.subscribe("user-2", "group-1")
        fanout.emitInvalidation("group-1", 1)
        assertThat(fanout.invalidations(active.id).take(1).blockFirst(Duration.ofSeconds(1))?.groupId)
            .isEqualTo("group-1")
        assertThat(fanout.revocationSignal(expired.id).block(Duration.ofSeconds(1))).isNull()
    }

    @Test
    fun `subscription limit admits only one concurrent subscription`() {
        val fanout = LiveUpdateFanout(maxSubscriptionsPerUser = 1)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val attempts = (1..8).map { index ->
                executor.submit<Boolean> {
                    start.await()
                    runCatching { fanout.subscribe("user-1", "group-$index") }.isSuccess
                }
            }
            start.countDown()
            val admitted = attempts.count { it.get(1, TimeUnit.SECONDS) }
            assertThat(admitted).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `expiry queue compacts stale churn before bounded sweep`() {
        val clock = MutableClock()
        val fanout = LiveUpdateFanout(clock = clock, subscriptionTtl = Duration.ofSeconds(1))
        val active = fanout.subscribe("active", "active-group")

        repeat(1_026) { index ->
            val subscription = fanout.subscribe("stale-$index", "group-$index")
            fanout.unsubscribe(subscription.id)
        }

        clock.advance(Duration.ofSeconds(2))
        clock.advance(Duration.ofSeconds(10))
        fanout.sweepExpired()

        assertThat(fanout.revokeUser("active")).isZero()
        assertThat(fanout.revocationSignal(active.id).block(Duration.ofSeconds(1))).isNull()
    }

    @Test
    fun `queue overflow revokes the subscription`() {
        val fanout = LiveUpdateFanout(queueCapacity = 1)
        val subscription = fanout.subscribe("user-1", "group-1")

        fanout.emitInvalidation("group-1", 1)
        fanout.emitInvalidation("group-1", 2)

        assertThat(fanout.revocationSignal(subscription.id).block(Duration.ofSeconds(1))).isNull()
    }

    private class MutableClock(private var current: Instant = Instant.EPOCH) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
