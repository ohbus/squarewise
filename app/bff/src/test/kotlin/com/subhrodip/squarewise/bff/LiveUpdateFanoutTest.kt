package com.subhrodip.squarewise.bff

import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class LiveUpdateFanoutTest {
    @Test
    fun `emits invalidations only to the subscribed group`() {
        val fanout = LiveUpdateFanout()
        val matching = fanout.subscribe("user-1", "group-1")
        fanout.subscribe("user-2", "group-2")
        fanout.emitInvalidation("group-2", 1)
        fanout.emitInvalidation("group-1", 2)

        val result = fanout.invalidations(matching.id).take(1).blockFirst()!!
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
    fun `revocation removes the subscription and completes its signal`() {
        val fanout = LiveUpdateFanout(maxSubscriptionsPerUser = 1)
        val subscription = fanout.subscribe("user-1", "group-1")

        assertThat(fanout.revokeUserFromGroup("user-1", "group-1")).isEqualTo(1)
        assertThat(fanout.revocationSignal(subscription.id).block(Duration.ofSeconds(1))).isNull()
        fanout.subscribe("user-1", "group-2")
    }

    @Test
    fun `rejects invalid configuration`() {
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(0) }
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(subscriptionTtl = Duration.ZERO) }
        assertThrows<IllegalArgumentException> { LiveUpdateFanout(maxSubscriptionsPerUser = 0) }
    }
}
