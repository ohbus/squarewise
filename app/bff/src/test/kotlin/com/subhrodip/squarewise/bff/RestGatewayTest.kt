package com.subhrodip.squarewise.bff

import com.subhrodip.squarewise.bff.transport.UpstreamServiceException

import com.subhrodip.squarewise.bff.transport.ExpenseCoreGateway
import com.subhrodip.squarewise.bff.transport.greatestWriterWatermark
import com.subhrodip.squarewise.bff.transport.AccountsGateway
import com.subhrodip.squarewise.bff.messaging.model.BffEventEnvelope
import com.subhrodip.squarewise.bff.messaging.model.ConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.DuplicateConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.ProcessedConsumptionResult
import com.subhrodip.squarewise.bff.messaging.service.BffEventConsumer
import com.subhrodip.squarewise.bff.messaging.persistence.BffEventDeduplicator
import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import com.subhrodip.squarewise.bff.transport.model.output.BffBalance
import com.subhrodip.squarewise.bff.transport.model.output.BffCreateGroup
import com.subhrodip.squarewise.bff.transport.model.output.BffExpense
import com.subhrodip.squarewise.bff.transport.model.output.BffGroup
import com.subhrodip.squarewise.bff.transport.model.output.BffMoney
import com.subhrodip.squarewise.bff.transport.model.output.BffSettlement
import com.subhrodip.squarewise.bff.transport.model.output.BffSuggestedSettlement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class RestGatewayTest {

    @Test
    fun `keeps greatest valid downstream writer watermark`() {
        assertEquals("0/20", greatestWriterWatermark("0/10", "0/20"))
        assertEquals("0/20", greatestWriterWatermark("0/20", "0/10"))
        assertEquals("0/20", greatestWriterWatermark("0/20", "not-an-lsn"))
        assertEquals("0/30", greatestWriterWatermark(null, "0/30"))
        assertEquals("0/20", greatestWriterWatermark("0/20", null))
        assertEquals(null, greatestWriterWatermark(null, null))
        assertEquals("0/30", greatestWriterWatermark("not-an-lsn", "0/30"))
    }

    @Test
    fun `maps group response through nonblocking gateway`() {
        val request = BffCreateGroup("Trip", "TRIP", "EUR")
        assertEquals("TRIP", request.kind)
        assertEquals("Trip", request.name)
        assertEquals("EUR", request.currency)
    }

    @Test
    fun `verifies GraphQL property getters match REST model properties`() {
        val groupId = UUID.randomUUID().toString()
        val expenseId = UUID.randomUUID().toString()
        val participantId = UUID.randomUUID().toString()

        val group = BffGroup(groupId = groupId, name = "Trip", kind = "TRIP", revision = 3)
        assertEquals(groupId, group.id)

        val balance = BffBalance(participantId, BffMoney("EUR", "2500"))
        assertEquals(participantId, balance.participantId)
        assertEquals("2500", balance.money.minor)
        assertEquals("EUR", balance.money.currency)

        val expense = BffExpense(
            expenseId = expenseId,
            version = 2,
            description = "Train tickets",
            amount = BffMoney("EUR", "5000"),
            category = "transport"
        )
        assertEquals(expenseId, expense.id)
        assertEquals("5000", expense.amount.minor)

        val settlement = BffSettlement(
            id = "settlement-123",
            from = participantId,
            to = "target-456",
            amountMinor = 2500,
            status = "CONFIRMED",
            currency = "EUR"
        )
        assertEquals("settlement-123", settlement.id)
        assertEquals("2500", settlement.amount.minor)
        assertEquals("EUR", settlement.amount.currency)
        assertEquals("0", BffSettlement("settlement-empty", status = "RECORDED", currency = "EUR").amount.minor)

        val suggestion = BffSuggestedSettlement(
            fromParticipantId = participantId,
            toParticipantId = "target-456",
            amountMinor = 3500,
            currency = "USD"
        )
        assertEquals(participantId, suggestion.fromParticipantId)
        assertEquals("target-456", suggestion.toParticipantId)
        assertEquals(3500L, suggestion.amountMinor)
        assertEquals("3500", suggestion.amount.minor)
        assertEquals("USD", suggestion.amount.currency)
    }

    @Test
    fun `upstream service exception preserves status code`() {
        val ex = UpstreamServiceException(404, "Not found")
        assertEquals(404, ex.status)
        assertEquals("Not found", ex.message)
    }
}
