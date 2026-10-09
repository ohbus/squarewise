package com.subhrodip.squarewise.expensecore.persistence

import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.BalancePostingEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseIdempotencyEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseAllocationEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpensePayerEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupAuditEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupInvitationEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseOccurrence
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxStatus
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxEntity
import com.subhrodip.squarewise.expensecore.settlements.domain.SettlementStatus
import com.subhrodip.squarewise.expensecore.settlements.persistence.SettlementEntity
import com.subhrodip.squarewise.expensecore.sync.persistence.SyncChangeEntity
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/** Verifies durable identity and lifecycle state for financial persistence records. */
class PersistenceEntityStateTest {
    @Test
    fun `settlement and invitation retain lifecycle transitions`() {
        val groupId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val settlement = SettlementEntity(UUID.randomUUID(), groupId, from, to, 1250)
        settlement.status = SettlementStatus.REVERSED
        settlement.reversalReason = "Duplicate external payment"

        val placeholderId = UUID.randomUUID()
        val invitation = GroupInvitationEntity(
            token = "a".repeat(64),
            groupId = groupId,
            expiresAt = Instant.parse("2026-10-03T12:00:00Z"),
            placeholderId = placeholderId
        )
        val claimedAt = Instant.parse("2026-10-02T12:00:00Z")
        val revokedAt = Instant.parse("2026-10-02T13:00:00Z")
        invitation.claimedAt = claimedAt
        invitation.claimedBy = "alice"
        invitation.revokedAt = revokedAt

        assertEquals(groupId, settlement.groupId)
        assertEquals(from, settlement.fromParticipantId)
        assertEquals(to, settlement.toParticipantId)
        assertEquals(1250, settlement.amountMinor)
        assertEquals("EUR", settlement.currency)
        assertEquals(SettlementStatus.REVERSED, settlement.status)
        assertEquals("Duplicate external payment", settlement.reversalReason)
        assertEquals("a".repeat(64), invitation.token)
        assertEquals(groupId, invitation.groupId)
        assertEquals(Instant.parse("2026-10-03T12:00:00Z"), invitation.expiresAt)
        assertEquals(placeholderId, invitation.placeholderId)
        assertEquals(claimedAt, invitation.claimedAt)
        assertEquals("alice", invitation.claimedBy)
        assertEquals(revokedAt, invitation.revokedAt)
    }

    @Test
    fun `ledger posting and idempotency claim retain their owning references`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val settlementId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val createdAt = Instant.parse("2026-10-02T12:00:00Z")
        val posting = BalancePostingEntity(
            postingId = UUID.randomUUID(),
            groupId = groupId,
            expenseId = expenseId,
            settlementId = settlementId,
            participantId = participantId,
            currency = "EUR",
            amountMinor = -1250,
            createdAt = createdAt
        )
        val claim = ExpenseIdempotencyEntity(
            idempotencyId = UUID.randomUUID(),
            groupId = groupId,
            actorSubject = "alice",
            operation = "CREATE_EXPENSE",
            idempotencyKey = "request-1",
            payloadHash = "hash-1",
            expenseId = expenseId,
            createdAt = createdAt
        )

        assertEquals(groupId, posting.groupId)
        assertEquals(expenseId, posting.expenseId)
        assertEquals(settlementId, posting.settlementId)
        assertEquals(participantId, posting.participantId)
        assertEquals("EUR", posting.currency)
        assertEquals(-1250, posting.amountMinor)
        assertEquals(createdAt, posting.createdAt)
        assertEquals(groupId, claim.groupId)
        assertEquals("alice", claim.actorSubject)
        assertEquals("CREATE_EXPENSE", claim.operation)
        assertEquals("request-1", claim.idempotencyKey)
        assertEquals("hash-1", claim.payloadHash)
        assertEquals(expenseId, claim.expenseId)
        assertEquals(createdAt, claim.createdAt)
    }

    @Test
    fun `recurring occurrence links a schedule date before and after expense creation`() {
        val scheduleId = UUID.randomUUID()
        val occurrence = RecurringExpenseOccurrence(
            occurrenceId = UUID.randomUUID(),
            scheduleId = scheduleId,
            occurrenceDate = LocalDate.parse("2026-10-02")
        )
        val expenseId = UUID.randomUUID()
        occurrence.expenseId = expenseId

        assertEquals(scheduleId, occurrence.scheduleId)
        assertEquals(LocalDate.parse("2026-10-02"), occurrence.occurrenceDate)
        assertEquals(expenseId, occurrence.expenseId)
        assertEquals(false, occurrence.createdAt.isAfter(Instant.now()))
    }

    @Test
    fun `payer and allocation records retain expense participant amounts`() {
        val expense = mock(ExpenseEntity::class.java)
        val participantId = UUID.randomUUID()
        val payerId = UUID.randomUUID()
        val allocationId = UUID.randomUUID()
        val payer = ExpensePayerEntity(payerId, expense, participantId, 900)
        val allocation = ExpenseAllocationEntity(allocationId, expense, participantId, 900)
        payer.amountMinor = 1000
        allocation.allocatedMinor = 1000

        assertEquals(expense, payer.expense)
        assertEquals(payerId, payer.payerId)
        assertEquals(participantId, payer.participantId)
        assertEquals(1000, payer.amountMinor)
        assertEquals(expense, allocation.expense)
        assertEquals(allocationId, allocation.allocationId)
        assertEquals(participantId, allocation.participantId)
        assertEquals(1000, allocation.allocatedMinor)
    }

    @Test
    fun `sync change and audit records expose revision and event state`() {
        val createdAt = Instant.parse("2026-10-02T12:00:00Z")
        val change = SyncChangeEntity(
            changeId = UUID.randomUUID(),
            groupId = "group-1",
            revision = 4,
            entityId = "expense-1",
            deleted = true,
            payload = null,
            createdAt = createdAt
        )
        val auditId = UUID.randomUUID()
        val auditGroupId = UUID.randomUUID()
        val audit = GroupAuditEntity(
            auditId = auditId,
            groupId = auditGroupId,
            subject = "alice",
            action = "group.renamed",
            revision = 4,
            payload = "{\"name\":\"Trip\"}",
            occurredAt = createdAt
        )
        change.payload = "{\"kind\":\"updated\"}"
        change.deleted = false
        audit.subject = "bob"
        audit.action = "group.updated"
        audit.payload = "{\"name\":\"Holiday\"}"

        assertEquals("group-1", change.groupId)
        assertEquals(4, change.revision)
        assertEquals("expense-1", change.entityId)
        assertEquals(false, change.deleted)
        assertEquals("{\"kind\":\"updated\"}", change.payload)
        assertEquals(createdAt, change.createdAt)
        assertEquals("bob", audit.subject)
        assertEquals(auditId, audit.auditId)
        assertEquals(auditGroupId, audit.groupId)
        assertEquals("group.updated", audit.action)
        assertEquals(4, audit.revision)
        assertEquals("{\"name\":\"Holiday\"}", audit.payload)
        assertEquals(createdAt, audit.occurredAt)
    }

    @Test
    fun `mutable persistence entities retain ORM-updated state`() {
        val replacementGroupId = UUID.randomUUID()
        val replacementExpenseId = UUID.randomUUID()
        val replacementTime = Instant.parse("2026-10-03T12:00:00Z")

        val expense = ExpenseEntity(
            expenseId = UUID.randomUUID(),
            groupId = UUID.randomUUID(),
            description = "Original",
            currency = "EUR",
            amountMinor = 100,
            allocationMode = "EQUAL"
        )
        expense.expenseId = replacementExpenseId
        expense.groupId = replacementGroupId
        expense.description = "Updated"
        expense.category = "travel"
        expense.currency = "USD"
        expense.amountMinor = 250
        expense.version = 2
        expense.allocationMode = "CUSTOM"
        expense.createdAt = replacementTime
        expense.deleted = true
        expense.updatedAt = replacementTime
        expense.payers = mutableListOf()
        expense.allocations = mutableListOf()

        val membership = GroupMembershipEntity(UUID.randomUUID(), UUID.randomUUID())
        membership.membershipId = UUID.randomUUID()
        membership.groupId = replacementGroupId
        membership.subject = "updated-subject"
        membership.displayName = "Updated member"
        membership.isPlaceholder = true
        membership.status = "REMOVED"

        val group = GroupEntity(UUID.randomUUID(), "Original", "TRIP", "EUR")
        group.groupId = replacementGroupId
        group.name = "Updated group"
        group.kind = "HOUSEHOLD"
        group.currency = "USD"
        group.status = "ARCHIVED"
        group.revision = 4

        val outbox = OutboxEntity(
            eventId = UUID.randomUUID(),
            eventType = "group.created",
            aggregateId = UUID.randomUUID(),
            groupId = UUID.randomUUID(),
            groupRevision = 1,
            occurredAt = Instant.EPOCH,
            payload = "{}"
        )
        outbox.eventId = UUID.randomUUID()
        outbox.eventType = "group.updated"
        outbox.aggregateId = replacementExpenseId
        outbox.groupId = replacementGroupId
        outbox.groupRevision = 4
        outbox.occurredAt = replacementTime
        outbox.payload = "{\"name\":\"Updated group\"}"
        outbox.status = OutboxStatus.PARKED
        outbox.attempts = 2
        outbox.leaseUntil = replacementTime
        outbox.availableAt = replacementTime

        assertEquals(replacementExpenseId, expense.expenseId)
        assertEquals(replacementGroupId, expense.groupId)
        assertEquals("Updated", expense.description)
        assertEquals("travel", expense.category)
        assertEquals("USD", expense.currency)
        assertEquals(250, expense.amountMinor)
        assertEquals(2, expense.version)
        assertEquals("CUSTOM", expense.allocationMode)
        assertEquals(replacementTime, expense.createdAt)
        assertEquals(true, expense.deleted)
        assertEquals(replacementTime, expense.updatedAt)
        assertEquals(replacementGroupId, membership.groupId)
        assertEquals("updated-subject", membership.subject)
        assertEquals("Updated member", membership.displayName)
        assertEquals(true, membership.isPlaceholder)
        assertEquals("REMOVED", membership.status)
        assertEquals("Updated group", group.name)
        assertEquals("HOUSEHOLD", group.kind)
        assertEquals("USD", group.currency)
        assertEquals("ARCHIVED", group.status)
        assertEquals(4, group.revision)
        assertEquals("group.updated", outbox.eventType)
        assertEquals(replacementExpenseId, outbox.aggregateId)
        assertEquals(replacementGroupId, outbox.groupId)
        assertEquals(4, outbox.groupRevision)
        assertEquals(replacementTime, outbox.occurredAt)
        assertEquals("{\"name\":\"Updated group\"}", outbox.payload)
        assertEquals(OutboxStatus.PARKED, outbox.status)
        assertEquals(2, outbox.attempts)
        assertEquals(replacementTime, outbox.leaseUntil)
        assertEquals(replacementTime, outbox.availableAt)
    }
}
