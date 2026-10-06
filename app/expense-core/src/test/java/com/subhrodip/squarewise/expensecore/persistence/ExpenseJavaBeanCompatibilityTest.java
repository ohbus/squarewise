package com.subhrodip.squarewise.expensecore.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.BalancePostingEntity;
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseAllocationEntity;
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseEntity;
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseIdempotencyEntity;
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpensePayerEntity;
import com.subhrodip.squarewise.expensecore.groups.domain.GroupAuditEntity;
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity;
import com.subhrodip.squarewise.expensecore.groups.domain.GroupInvitationEntity;
import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity;
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxStatus;
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxEntity;
import com.subhrodip.squarewise.expensecore.settlements.domain.SettlementStatus;
import com.subhrodip.squarewise.expensecore.settlements.persistence.SettlementEntity;
import com.subhrodip.squarewise.expensecore.sync.persistence.SyncChangeEntity;
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseOccurrence;
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule;
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrenceFrequency;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies the JavaBean surface used by JPA for Expense Core persistence entities. */
class ExpenseJavaBeanCompatibilityTest {
    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    void persistence_entities_expose_jpa_getters_and_setters() {
        UUID groupId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        ExpenseEntity expense = new ExpenseEntity();
        expense.setExpenseId(expenseId);
        expense.setGroupId(groupId);
        expense.setDescription("Dinner");
        expense.setCategory("food");
        expense.setCurrency("EUR");
        expense.setAmountMinor(1200);
        expense.setVersion(2);
        expense.setAllocationMode("EQUAL");
        expense.setCreatedAt(NOW);
        expense.setDeleted(false);
        expense.setUpdatedAt(NOW);
        expense.setPayers(java.util.List.of());
        expense.setAllocations(java.util.List.of());
        assertEquals(expenseId, expense.getExpenseId());
        assertEquals(groupId, expense.getGroupId());
        assertEquals("Dinner", expense.getDescription());
        assertEquals("food", expense.getCategory());
        assertEquals("EUR", expense.getCurrency());
        assertEquals(1200, expense.getAmountMinor());
        assertEquals(2, expense.getVersion());
        assertEquals("EQUAL", expense.getAllocationMode());
        assertEquals(NOW, expense.getCreatedAt());
        assertEquals(false, expense.getDeleted());
        assertEquals(NOW, expense.getUpdatedAt());

        ExpensePayerEntity payer = new ExpensePayerEntity();
        payer.setPayerId(UUID.randomUUID());
        payer.setExpense(expense);
        payer.setParticipantId(participantId);
        payer.setAmountMinor(1200);
        assertEquals(expense, payer.getExpense());
        assertEquals(participantId, payer.getParticipantId());
        assertEquals(1200, payer.getAmountMinor());

        ExpenseAllocationEntity allocation = new ExpenseAllocationEntity();
        allocation.setAllocationId(UUID.randomUUID());
        allocation.setExpense(expense);
        allocation.setParticipantId(participantId);
        allocation.setAllocatedMinor(1200);
        assertEquals(expense, allocation.getExpense());
        assertEquals(participantId, allocation.getParticipantId());
        assertEquals(1200, allocation.getAllocatedMinor());

        GroupEntity group = new GroupEntity();
        group.setGroupId(groupId);
        group.setName("Trip");
        group.setKind("TRIP");
        group.setCurrency("EUR");
        group.setStatus("ACTIVE");
        group.setRevision(3);
        assertEquals(groupId, group.getGroupId());
        assertEquals("Trip", group.getName());
        assertEquals("TRIP", group.getKind());
        assertEquals("EUR", group.getCurrency());
        assertEquals("ACTIVE", group.getStatus());
        assertEquals(3, group.getRevision());

        GroupMembershipEntity membership = new GroupMembershipEntity();
        membership.setMembershipId(UUID.randomUUID());
        membership.setGroupId(groupId);
        membership.setSubject("alice");
        membership.setDisplayName("Alice");
        membership.setPlaceholder(false);
        membership.setStatus("ACTIVE");
        assertEquals("alice", membership.getSubject());
        assertEquals("Alice", membership.getDisplayName());
        assertEquals(false, membership.isPlaceholder());
        assertEquals("ACTIVE", membership.getStatus());

        GroupInvitationEntity invitation = new GroupInvitationEntity();
        invitation.setToken("token");
        invitation.setGroupId(groupId);
        invitation.setExpiresAt(NOW);
        invitation.setClaimedAt(NOW);
        invitation.setClaimedBy("alice");
        invitation.setRevokedAt(null);
        invitation.setPlaceholderId(membership.getMembershipId());
        assertEquals("token", invitation.getToken());
        assertEquals(groupId, invitation.getGroupId());
        assertEquals(NOW, invitation.getExpiresAt());
        assertEquals("alice", invitation.getClaimedBy());
        assertEquals(membership.getMembershipId(), invitation.getPlaceholderId());

        GroupAuditEntity audit = new GroupAuditEntity();
        audit.setAuditId(UUID.randomUUID());
        audit.setGroupId(groupId);
        audit.setSubject("alice");
        audit.setAction("group.updated");
        audit.setRevision(3);
        audit.setPayload("{}");
        audit.setOccurredAt(NOW);
        assertEquals(groupId, audit.getGroupId());
        assertEquals("alice", audit.getSubject());
        assertEquals("group.updated", audit.getAction());
        assertEquals(3, audit.getRevision());
        assertEquals("{}", audit.getPayload());

        SettlementEntity settlement = new SettlementEntity();
        settlement.setSettlementId(UUID.randomUUID());
        settlement.setGroupId(groupId);
        settlement.setFromParticipantId(participantId);
        settlement.setToParticipantId(UUID.randomUUID());
        settlement.setAmountMinor(400);
        settlement.setStatus(SettlementStatus.RECORDED);
        settlement.setReversalReason(null);
        assertEquals(groupId, settlement.getGroupId());
        assertEquals(participantId, settlement.getFromParticipantId());
        assertEquals(400, settlement.getAmountMinor());
        assertEquals(SettlementStatus.RECORDED, settlement.getStatus());

        UUID changeId = UUID.randomUUID();
        SyncChangeEntity change = new SyncChangeEntity();
        change.setChangeId(changeId);
        change.setGroupId(groupId.toString());
        change.setRevision(3);
        change.setEntityId(expenseId.toString());
        change.setDeleted(false);
        change.setPayload("{}");
        change.setCreatedAt(NOW);
        assertEquals(changeId, change.getChangeId());
        assertEquals(groupId.toString(), change.getGroupId());
        assertEquals(3, change.getRevision());
        assertEquals(expenseId.toString(), change.getEntityId());
        assertEquals("{}", change.getPayload());

        UUID postingId = UUID.randomUUID();
        BalancePostingEntity posting = new BalancePostingEntity();
        posting.setPostingId(postingId);
        posting.setGroupId(groupId);
        posting.setExpenseId(expenseId);
        posting.setSettlementId(null);
        posting.setParticipantId(participantId);
        posting.setCurrency("EUR");
        posting.setAmountMinor(-400);
        posting.setCreatedAt(NOW);
        assertEquals(postingId, posting.getPostingId());
        assertEquals(groupId, posting.getGroupId());
        assertEquals(expenseId, posting.getExpenseId());
        assertEquals(participantId, posting.getParticipantId());
        assertEquals(-400, posting.getAmountMinor());

        OutboxEntity outbox = new OutboxEntity();
        outbox.setEventId(UUID.randomUUID());
        outbox.setEventType("group.updated");
        outbox.setAggregateId(groupId);
        outbox.setGroupId(groupId);
        outbox.setGroupRevision(3);
        outbox.setOccurredAt(NOW);
        outbox.setPayload("{}");
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(1);
        outbox.setLeaseUntil(null);
        outbox.setAvailableAt(NOW);
        assertEquals("group.updated", outbox.getEventType());
        assertEquals(groupId, outbox.getAggregateId());
        assertEquals(3, outbox.getGroupRevision());
        assertEquals("{}", outbox.getPayload());
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());
        assertEquals(1, outbox.getAttempts());

        UUID scheduleId = UUID.randomUUID();
        RecurringExpenseSchedule schedule = new RecurringExpenseSchedule(
                scheduleId, groupId, "Monthly dinner", 1200, "EUR", RecurrenceFrequency.MONTHLY,
                null, LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 1, 1), false, NOW, 1);
        schedule.setScheduleId(scheduleId);
        schedule.setGroupId(groupId);
        schedule.setCreatedAt(NOW);
        schedule.setPaused(true);
        schedule.setVersion(4);
        assertEquals(scheduleId, schedule.getScheduleId());
        assertEquals(groupId, schedule.getGroupId());
        assertEquals(NOW, schedule.getCreatedAt());
        assertEquals("Monthly dinner", schedule.getDescription());
        assertEquals(RecurrenceFrequency.MONTHLY, schedule.getFrequency());
        assertEquals(true, schedule.getPaused());
        assertEquals(4, schedule.getVersion());

        UUID occurrenceId = UUID.randomUUID();
        RecurringExpenseOccurrence occurrence = new RecurringExpenseOccurrence(
                occurrenceId, schedule.getScheduleId(), LocalDate.of(2026, 1, 1), null, NOW);
        occurrence.setOccurrenceId(occurrenceId);
        occurrence.setScheduleId(schedule.getScheduleId());
        occurrence.setOccurrenceDate(LocalDate.of(2026, 1, 1));
        occurrence.setExpenseId(expenseId);
        occurrence.setCreatedAt(NOW);
        assertEquals(occurrenceId, occurrence.getOccurrenceId());
        assertEquals(schedule.getScheduleId(), occurrence.getScheduleId());
        assertEquals(LocalDate.of(2026, 1, 1), occurrence.getOccurrenceDate());
        assertEquals(expenseId, occurrence.getExpenseId());
        assertEquals(NOW, occurrence.getCreatedAt());
    }

    @Test
    void idempotency_entity_exposes_its_claim_state() {
        ExpenseIdempotencyEntity entity = new ExpenseIdempotencyEntity();
        UUID groupId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        entity.setIdempotencyId(UUID.randomUUID());
        entity.setGroupId(groupId);
        entity.setActorSubject("alice");
        entity.setOperation("CREATE_EXPENSE");
        entity.setIdempotencyKey("request-1");
        entity.setPayloadHash("hash");
        entity.setExpenseId(expenseId);
        entity.setCreatedAt(NOW);
        assertEquals(groupId, entity.getGroupId());
        assertEquals("alice", entity.getActorSubject());
        assertEquals("CREATE_EXPENSE", entity.getOperation());
        assertEquals("request-1", entity.getIdempotencyKey());
        assertEquals("hash", entity.getPayloadHash());
        assertEquals(expenseId, entity.getExpenseId());
        assertEquals(NOW, entity.getCreatedAt());
    }
}
