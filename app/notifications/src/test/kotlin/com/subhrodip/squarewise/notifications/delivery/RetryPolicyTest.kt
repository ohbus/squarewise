package com.subhrodip.squarewise.notifications.delivery

import com.subhrodip.squarewise.notifications.delivery.model.DeliveryChannel
import com.subhrodip.squarewise.notifications.delivery.model.DeliveryOutcome
import com.subhrodip.squarewise.notifications.delivery.model.RetryDecision
import com.subhrodip.squarewise.notifications.delivery.persistence.EventDeduplicator
import com.subhrodip.squarewise.notifications.delivery.persistence.InboxDeduplicator
import com.subhrodip.squarewise.notifications.delivery.policy.DeliveryPolicy
import com.subhrodip.squarewise.notifications.delivery.policy.RetryPolicy
import com.subhrodip.squarewise.notifications.delivery.rate.DeliveryRateLimiter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration

class RetryPolicyTest {
    @Test
    fun `backs off retryable failures and parks after limit`() {
        val policy = RetryPolicy(3)
        assertEquals(Duration.ofSeconds(1), policy.decide(1, DeliveryOutcome.RETRYABLE_FAILURE).delay)
        assertEquals(Duration.ofSeconds(2), policy.decide(2, DeliveryOutcome.RETRYABLE_FAILURE).delay)
        assertEquals(true, policy.decide(3, DeliveryOutcome.RETRYABLE_FAILURE).parked)
        assertEquals(true, policy.decide(1, DeliveryOutcome.PERMANENT_FAILURE).parked)
        assertFalse(policy.decide(1, DeliveryOutcome.SUCCESS).retry)
        assertEquals(Duration.ZERO, policy.decide(1, DeliveryOutcome.SUCCESS).delay)
    }

    @Test
    fun `rejects non-positive attempts and policy limits`() {
        assertThrows(IllegalArgumentException::class.java) { RetryPolicy(0) }
        val policy = RetryPolicy(3)

        assertThrows(IllegalArgumentException::class.java) {
            policy.decide(0, DeliveryOutcome.SUCCESS)
        }
    }

    @Test
    fun `default policy caps exponential backoff at the documented bound`() {
        val policy = RetryPolicy(20)

        assertEquals(Duration.ofSeconds(1L shl 9), policy.decide(10, DeliveryOutcome.RETRYABLE_FAILURE).delay)
        assertEquals(Duration.ofSeconds(1L shl 10), policy.decide(11, DeliveryOutcome.RETRYABLE_FAILURE).delay)
    }
}
