package com.subhrodip.squarewise.bff.transport

import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import com.subhrodip.squarewise.bff.messaging.model.BffEventEnvelope
import com.subhrodip.squarewise.bff.transport.model.auth.AccountsTokenResponse
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginStartRequest
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginStartResponse
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginVerifyRequest
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserSessionResponse
import com.subhrodip.squarewise.bff.transport.model.input.AllocationItemInput
import com.subhrodip.squarewise.bff.transport.model.input.MoneyInput
import com.subhrodip.squarewise.bff.transport.model.input.RepaymentInput
import com.subhrodip.squarewise.bff.transport.model.output.BffAllocation
import com.subhrodip.squarewise.bff.transport.model.output.BffBalance
import com.subhrodip.squarewise.bff.transport.model.output.BffBalancesResponse
import com.subhrodip.squarewise.bff.transport.model.output.BffExpense
import com.subhrodip.squarewise.bff.transport.model.output.BffGroup
import com.subhrodip.squarewise.bff.transport.model.output.BffMember
import com.subhrodip.squarewise.bff.transport.model.output.BffMoney
import com.subhrodip.squarewise.bff.transport.model.output.BffSettlement
import com.subhrodip.squarewise.bff.transport.model.output.BffSuggestedSettlement
import com.subhrodip.squarewise.bff.transport.model.upstream.UpstreamExpense
import com.subhrodip.squarewise.bff.transport.model.upstream.UpstreamGroup
import com.subhrodip.squarewise.bff.transport.model.upstream.UpstreamSettlement
import java.util.UUID
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

/** Verifies BFF transport models preserve upstream mapping and public response shape. */
class BffTransportModelTest {
    @Test
    fun `upstream models map optional fields and public aliases`() {
        val money = BffMoney("EUR", "1250")
        val allocation = BffAllocation("member-1", money)
        val member = BffMember("membership-1", "subject-1", "Alice", false)
        val balance = BffBalance("member-1", money)
        val expense = UpstreamExpense("expense-1", description = null, amount = money, allocations = listOf(allocation))
            .toBffExpense("Fallback description")
        val group = UpstreamGroup("group-1", "Trip", "TRIP", "ACTIVE", 3).toBffGroup()
        val settlement = UpstreamSettlement("settlement-1", "from", "to", 1250, "EUR").toBffSettlement()
        val response = BffBalancesResponse("group-1", listOf(balance))
        val bffGroup = BffGroup(group.groupId, group.name, group.kind, group.status, group.revision, listOf(balance), listOf(expense), listOf(member))
        val suggested = BffSuggestedSettlement("from", "to", 1250, "EUR")

        assertEquals("expense-1", expense.id)
        assertEquals("Fallback description", expense.description)
        assertEquals("group-1", group.id)
        assertEquals("settlement-1", settlement.id)
        assertEquals(money, settlement.amount)
        assertEquals(money, suggested.amount)
        assertEquals("group-1", response.groupId)
        assertEquals("group-1", bffGroup.id)
        assertEquals("member-1", balance.participantId)
    }

    @Test
    fun `browser and GraphQL transport inputs retain their wire fields`() {
        val start = BrowserLoginStartRequest("alice@example.test", "EMAIL")
        val accepted = BrowserLoginStartResponse("ACCEPTED", 5)
        val verify = BrowserLoginVerifyRequest("credential")
        val token = AccountsTokenResponse("access", "Bearer", 600, "refresh")
        val session = BrowserSessionResponse(expiresIn = 600)
        val allocation = AllocationItemInput("member-1", "500")
        val repayment = RepaymentInput("group-1", "from", "to", MoneyInput("EUR", "500"), "Dinner", "repayment-key-0001")
        val invalidation = GroupInvalidation("group-1", 3, UUID.randomUUID().toString())

        assertEquals("alice@example.test", start.email)
        assertEquals("EMAIL", start.channel)
        assertEquals(5, accepted.retryAfterSeconds)
        assertEquals("credential", verify.credential)
        assertEquals("Bearer", token.tokenType)
        assertEquals("AUTHENTICATED", session.status)
        assertEquals("500", allocation.value)
        assertEquals("EUR", repayment.amount.currency)
        assertEquals("Dinner", repayment.reason)
        assertEquals(3, invalidation.revision)
    }

    @Test
    fun `browser transport defaults preserve the public optional-field contract`() {
        val start = BrowserLoginStartRequest("alice@example.test")
        val accepted = BrowserLoginStartResponse("ACCEPTED")
        val session = BrowserSessionResponse(expiresIn = 600)
        val group = UpstreamGroup("group-1", "Trip").toBffGroup()

        assertEquals(null, start.channel)
        assertEquals(null, accepted.retryAfterSeconds)
        assertEquals("AUTHENTICATED", session.status)
        assertEquals(null, group.kind)
        assertEquals(null, group.status)
        assertEquals(0, group.revision)
    }

    @Test
    fun `upstream group serialization preserves its wire properties`() {
        val group = UpstreamGroup("group-1", "Trip", "TRIP", "ACTIVE", 3)
        val json = ObjectMapper().writeValueAsString(group)

        assertEquals(true, json.contains("\"groupId\":\"group-1\""))
        assertEquals(true, json.contains("\"name\":\"Trip\""))
        assertEquals(true, json.contains("\"kind\":\"TRIP\""))
        assertEquals(true, json.contains("\"status\":\"ACTIVE\""))
        assertEquals(true, json.contains("\"revision\":3"))
    }

    @Test
    fun `event envelope preserves the event identity and timing fields`() {
        val eventId = UUID.randomUUID()
        val aggregateId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val occurredAt = Instant.parse("2026-01-01T00:00:00Z")

        val envelope = BffEventEnvelope(
            eventId = eventId,
            eventType = "group.changed",
            schemaVersion = 1,
            aggregateId = aggregateId,
            groupId = groupId,
            groupRevision = 4,
            occurredAt = occurredAt,
            payload = mapOf("change" to "updated"),
        )

        assertEquals(eventId, envelope.eventId)
        assertEquals("group.changed", envelope.eventType)
        assertEquals(1, envelope.schemaVersion)
        assertEquals(aggregateId, envelope.aggregateId)
        assertEquals(groupId, envelope.groupId)
        assertEquals(4, envelope.groupRevision)
        assertEquals(occurredAt, envelope.occurredAt)
        assertEquals("updated", envelope.payload["change"])
    }
}
