package com.subhrodip.squarewise.notifications.delivery

import com.subhrodip.squarewise.notifications.delivery.model.DeliveryChannel
import com.subhrodip.squarewise.notifications.delivery.model.DeliveryOutcome
import com.subhrodip.squarewise.notifications.delivery.model.RetryDecision
import com.subhrodip.squarewise.notifications.delivery.persistence.EventDeduplicator
import com.subhrodip.squarewise.notifications.delivery.persistence.InboxDeduplicator
import com.subhrodip.squarewise.notifications.delivery.policy.DeliveryPolicy
import com.subhrodip.squarewise.notifications.delivery.policy.RetryPolicy
import com.subhrodip.squarewise.notifications.delivery.rate.DeliveryRateLimiter

import com.subhrodip.squarewise.notifications.preferences.persistence.InMemoryPreferenceStore
import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class DeliveryPolicyTest {
    @Test
    fun `honors preferences and suppresses duplicate event`() {
        val store = InMemoryPreferenceStore()
        store.put("alice", NotificationPreferences(emailEnabled = false, pushEnabled = true))
        val policy = DeliveryPolicy(InboxDeduplicator(), store)
        val event = UUID.randomUUID()
        val decision = policy.decide(event, "alice")!!
        assertEquals(event, decision.eventId)
        assertEquals("alice", decision.subject)
        assertEquals(setOf(DeliveryChannel.PUSH), decision.channels)
        assertNull(policy.decide(event, "alice"))
    }

    @Test
    fun `selects both channels and suppresses delivery when both are disabled`() {
        val store = InMemoryPreferenceStore()
        val bothEnabled = UUID.randomUUID()
        val neitherEnabled = UUID.randomUUID()
        store.put("both", NotificationPreferences(emailEnabled = true, pushEnabled = true))
        store.put("neither", NotificationPreferences(emailEnabled = false, pushEnabled = false))
        val policy = DeliveryPolicy(InboxDeduplicator(), store)

        assertEquals(setOf(DeliveryChannel.EMAIL, DeliveryChannel.PUSH), policy.decide(bothEnabled, "both")!!.channels)
        assertEquals(emptySet<DeliveryChannel>(), policy.decide(neitherEnabled, "neither")!!.channels)
    }
}
