package com.subhrodip.squarewise.expensecore.recurring

import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.ExpenseStore
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreateInviteRequest
import com.subhrodip.squarewise.expensecore.groups.persistence.store.JpaGroupStore
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore
import com.subhrodip.squarewise.expensecore.recurring.api.CreateRecurringScheduleRequest
import com.subhrodip.squarewise.expensecore.recurring.api.UpdateRecurringScheduleRequest
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseOccurrence
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrenceFrequency
import com.subhrodip.squarewise.expensecore.recurring.persistence.RecurringExpenseOccurrenceRepository
import com.subhrodip.squarewise.expensecore.recurring.persistence.RecurringExpenseScheduleRepository
import com.subhrodip.squarewise.expensecore.recurring.service.RecurringExpenseService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@Transactional
class RecurringExpenseServiceTest @Autowired constructor(
    private val service: RecurringExpenseService,
    private val groupStore: JpaGroupStore,
    private val expenseStore: ExpenseStore,
    private val scheduleRepository: RecurringExpenseScheduleRepository,
    private val occurrenceRepository: RecurringExpenseOccurrenceRepository,
    private val outboxStore: OutboxStore
) {

    @Test
    fun `default due-occurrence entrypoint uses current date and bounded catch-up`() {
        assertEquals(0, service.processDueOccurrences())
    }

    @Test
    fun `creates schedule successfully with weekly frequency and initial next occurrence date`() {
        val group = groupStore.create("alice", CreateGroupRequest("Apartment 4B", "HOUSEHOLD", "EUR"))
        val startDate = LocalDate.of(2026, 9, 1)

        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Weekly Cleaning",
                amountMinor = 5000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        assertNotNull(schedule.scheduleId)
        assertEquals(group.groupId, schedule.groupId)
        assertEquals("Weekly Cleaning", schedule.description)
        assertEquals(5000, schedule.amountMinor)
        assertEquals("EUR", schedule.currency)
        assertEquals(RecurrenceFrequency.WEEKLY, schedule.frequency)
        assertEquals(startDate, schedule.startDate)
        assertEquals(startDate, schedule.nextOccurrenceDate)
        assertFalse(schedule.paused)

        val retrieved = service.getSchedule(schedule.scheduleId)
        assertNotNull(retrieved)
        assertEquals(schedule.scheduleId, retrieved?.scheduleId)

        val list = service.listSchedules(group.groupId)
        assertEquals(1, list.size)
        assertEquals(schedule.scheduleId, list[0].scheduleId)
    }

    @Test
    fun `creates monthly schedule with explicit identifier and valid day of month`() {
        val group = groupStore.create("alice", CreateGroupRequest("Monthly bills", "HOUSEHOLD", "EUR"))
        val scheduleId = UUID.randomUUID()

        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                scheduleId = scheduleId,
                description = "Rent",
                amountMinor = 120000,
                currency = "EUR",
                frequency = RecurrenceFrequency.MONTHLY,
                dayOfMonth = 31,
                startDate = LocalDate.of(2026, 9, 1)
            )
        )

        assertEquals(scheduleId, schedule.scheduleId)
        assertEquals(31, schedule.dayOfMonth)
        assertEquals(scheduleId, scheduleRepository.findById(scheduleId).orElseThrow().scheduleId)

        val firstOfMonth = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Insurance",
                amountMinor = 4500,
                currency = "EUR",
                frequency = RecurrenceFrequency.MONTHLY,
                dayOfMonth = 1,
                startDate = LocalDate.of(2026, 9, 1)
            )
        )
        assertEquals(1, firstOfMonth.dayOfMonth)
    }

    @Test
    fun `updates schedule properties successfully`() {
        val group = groupStore.create("alice", CreateGroupRequest("Apartment 4B", "HOUSEHOLD", "EUR"))
        val startDate = LocalDate.of(2026, 9, 1)

        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Weekly Cleaning",
                amountMinor = 5000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        val updated = service.updateSchedule(
            group.groupId,
            schedule.scheduleId,
            UpdateRecurringScheduleRequest(
                description = "Biweekly Deep Clean",
                amountMinor = 8000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        assertEquals("Biweekly Deep Clean", updated.description)
        assertEquals(8000, updated.amountMinor)
    }

    @Test
    fun `updates schedule with valid monthly bounds and custom participants`() {
        val group = groupStore.create("alice", CreateGroupRequest("Monthly update", "HOUSEHOLD", "EUR"))
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Original schedule",
                amountMinor = 8000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = LocalDate.of(2026, 9, 1)
            )
        )
        val participantId = UUID.randomUUID()

        val updated = service.updateSchedule(
            group.groupId,
            schedule.scheduleId,
            UpdateRecurringScheduleRequest(
                description = "Month-end schedule",
                amountMinor = 8000,
                currency = "EUR",
                frequency = RecurrenceFrequency.MONTHLY,
                dayOfMonth = 31,
                startDate = LocalDate.of(2026, 9, 1),
                endDate = LocalDate.of(2026, 12, 31),
                payers = listOf(ExpensePayer(participantId, 8000)),
                allocations = listOf(ExpenseAllocation(participantId, 8000))
            )
        )

        assertEquals("Month-end schedule", updated.description)
        assertEquals(RecurrenceFrequency.MONTHLY, updated.frequency)
        assertEquals(31, updated.dayOfMonth)
        assertEquals(LocalDate.of(2026, 12, 31), updated.endDate)
    }

    /** Verifies update-time one-sided custom specifications preserve the derived side. */
    @Test
    fun `updates schedules with allocation-only and payer-only specifications`() {
        val group = groupStore.create("alice", CreateGroupRequest("One-sided updates", "HOUSEHOLD", "EUR"))
        val invite = groupStore.invite(group.groupId, "alice", CreateInviteRequest(24))
        groupStore.claim(invite.token, "bob")
        val members = groupStore.listMembers(group.groupId, "alice")
        val aliceId = members.first { it.subject == "alice" }.membershipId
        val bobId = members.first { it.subject == "bob" }.membershipId
        val startDate = LocalDate.of(2026, 9, 1)

        val allocationOnly = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Allocation-only original",
                amountMinor = 6000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )
        service.updateSchedule(
            group.groupId,
            allocationOnly.scheduleId,
            UpdateRecurringScheduleRequest(
                description = "Allocation-only updated",
                amountMinor = 6000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate,
                allocations = listOf(ExpenseAllocation(bobId, 6000))
            )
        )

        val payerOnly = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Payer-only original",
                amountMinor = 6000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )
        service.updateSchedule(
            group.groupId,
            payerOnly.scheduleId,
            UpdateRecurringScheduleRequest(
                description = "Payer-only updated",
                amountMinor = 6000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate,
                payers = listOf(ExpensePayer(aliceId, 6000))
            )
        )

        assertEquals(2, service.processDueOccurrences(asOfDate = startDate))
        val allocationOnlyExpense = expenseStore.findById(
            service.getOccurrences(allocationOnly.scheduleId).single().expenseId!!
        )
        val payerOnlyExpense = expenseStore.findById(
            service.getOccurrences(payerOnly.scheduleId).single().expenseId!!
        )

        assertEquals(listOf(ExpenseAllocation(bobId, 6000)), allocationOnlyExpense?.allocations)
        assertEquals(6000, allocationOnlyExpense?.payers?.single()?.amountMinor)
        assertEquals(listOf(ExpensePayer(aliceId, 6000)), payerOnlyExpense?.payers)
        assertEquals(2, payerOnlyExpense?.allocations?.size)
    }

    @Test
    fun `pauses and resumes schedule`() {
        val group = groupStore.create("alice", CreateGroupRequest("Cabin", "TRIP", "EUR"))
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Weekly Supplies",
                amountMinor = 2000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = LocalDate.of(2026, 9, 1)
            )
        )

        val paused = service.pauseSchedule(schedule.scheduleId)
        assertTrue(paused.paused)

        val retrievedPaused = service.getSchedule(schedule.scheduleId)
        assertTrue(retrievedPaused?.paused == true)

        val resumed = service.resumeSchedule(schedule.scheduleId)
        assertFalse(resumed.paused)

        val retrievedResumed = service.getSchedule(schedule.scheduleId)
        assertFalse(retrievedResumed?.paused == true)
    }

    @Test
    fun `pause and resume throw 404 for non-existent schedule`() {
        val missingId = UUID.randomUUID()
        val pauseErr = assertThrows(SquarewiseException::class.java) {
            service.pauseSchedule(missingId)
        }
        assertEquals(CategoryCode.NOT_FOUND, pauseErr.definition.category)

        val resumeErr = assertThrows(SquarewiseException::class.java) {
            service.resumeSchedule(missingId)
        }
        assertEquals(CategoryCode.NOT_FOUND, resumeErr.definition.category)
    }

    @Test
    fun `processes due weekly occurrences, advances next date, and creates expense split equally`() {
        val group = groupStore.create("alice", CreateGroupRequest("Shared Flat", "HOUSEHOLD", "EUR"))
        val invite = groupStore.invite(group.groupId, "alice", CreateInviteRequest(24))
        groupStore.claim(invite.token, "bob")

        val startDate = LocalDate.of(2026, 9, 1)
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Internet bill",
                amountMinor = 4000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        val processed = service.processDueOccurrences(asOfDate = startDate)
        assertEquals(1, processed)

        val occurrences = service.getOccurrences(schedule.scheduleId)
        assertEquals(1, occurrences.size)
        val occurrence = occurrences[0]
        assertEquals(startDate, occurrence.occurrenceDate)
        assertNotNull(occurrence.expenseId)

        val expense = expenseStore.findById(occurrence.expenseId!!)
        assertNotNull(expense)
        assertEquals("Internet bill", expense?.description)
        assertEquals(4000, expense?.amountMinor)
        assertEquals("EUR", expense?.currency)
        assertEquals(2, expense?.allocations?.size)
        assertEquals(listOf(2000L, 2000L), expense?.allocations?.map { it.allocatedMinor }?.sorted())

        val updatedSchedule = service.getSchedule(schedule.scheduleId)
        assertEquals(LocalDate.of(2026, 9, 8), updatedSchedule?.nextOccurrenceDate)

        val processedAgain = service.processDueOccurrences(asOfDate = startDate)
        assertEquals(0, processedAgain)
    }

    @Test
    fun `processes multiple missed occurrences up to asOfDate`() {
        val group = groupStore.create("alice", CreateGroupRequest("Road Trip", "TRIP", "EUR"))
        val startDate = LocalDate.of(2026, 9, 1)

        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Vehicle maintenance",
                amountMinor = 3000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        val processed = service.processDueOccurrences(asOfDate = LocalDate.of(2026, 9, 15))
        assertEquals(3, processed)

        val occurrences = service.getOccurrences(schedule.scheduleId)
        assertEquals(3, occurrences.size)
        val dates = occurrences.map { it.occurrenceDate }.sorted()
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 15)
            ),
            dates
        )

        val updatedSchedule = service.getSchedule(schedule.scheduleId)
        assertEquals(LocalDate.of(2026, 9, 22), updatedSchedule?.nextOccurrenceDate)
    }

    @Test
    fun `bounds worker catch-up occurrences to max limit`() {
        val group = groupStore.create("alice", CreateGroupRequest("Long Trip", "TRIP", "EUR"))
        val startDate = LocalDate.of(2026, 1, 1)

        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Weekly Fuel",
                amountMinor = 3000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        // As of 20 weeks later, but maxCatchUpOccurrences set to 5
        val processed = service.processDueOccurrences(asOfDate = LocalDate.of(2026, 5, 20), maxCatchUpOccurrences = 5)
        assertEquals(5, processed)

        val occurrences = service.getOccurrences(schedule.scheduleId)
        assertEquals(5, occurrences.size)
    }

    /** Verifies a zero worker budget leaves due schedule state untouched. */
    @Test
    fun `does not process due occurrences when catch-up budget is zero`() {
        val group = groupStore.create("zero-budget-owner", CreateGroupRequest("Zero budget", "TRIP", "EUR"))
        val startDate = LocalDate.of(2026, 1, 1)
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Zero budget schedule",
                amountMinor = 3000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        assertEquals(
            0,
            service.processDueOccurrences(
                asOfDate = startDate.plusWeeks(4),
                maxCatchUpOccurrences = 0
            )
        )
        assertTrue(service.getOccurrences(schedule.scheduleId).isEmpty())
        assertEquals(startDate, service.getSchedule(schedule.scheduleId)?.nextOccurrenceDate)
    }

    @Test
    fun `pauses schedule and emits outbox notification on invalid membership`() {
        val group = groupStore.create("alice", CreateGroupRequest("Private Flat", "HOUSEHOLD", "EUR"))
        val aliceId = groupStore.listMembers(group.groupId, "alice").first().membershipId
        val nonMemberId = UUID.randomUUID()
        val startDate = LocalDate.of(2026, 9, 1)

        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Custom Split Invalid Member",
                amountMinor = 6000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate,
                payers = listOf(ExpensePayer(aliceId, 6000)),
                allocations = listOf(ExpenseAllocation(aliceId, 3000), ExpenseAllocation(nonMemberId, 3000))
            )
        )

        val processed = service.processDueOccurrences(asOfDate = startDate)
        assertEquals(0, processed)

        val updated = service.getSchedule(schedule.scheduleId)
        assertTrue(updated?.paused == true)

        val outboxMessages = outboxStore.snapshot()
        val notificationMsg = outboxMessages.find { it.eventType == "recurring.schedule.paused" }
        assertNotNull(notificationMsg)
        assertEquals(schedule.scheduleId, notificationMsg?.aggregateId)
        assertEquals("invalid_membership", notificationMsg?.payload?.get("reason"))
    }

    /** Verifies a payer outside the group cannot authorize recurring expense generation. */
    @Test
    fun `pauses recurring schedule when a custom payer is not a group member`() {
        val group = groupStore.create("alice", CreateGroupRequest("Payer membership", "HOUSEHOLD", "EUR"))
        val aliceId = UUID.nameUUIDFromBytes("alice".toByteArray(StandardCharsets.UTF_8))
        val nonMemberId = UUID.randomUUID()
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Unauthorized payer",
                amountMinor = 6000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = LocalDate.of(2026, 9, 1),
                payers = listOf(ExpensePayer(nonMemberId, 6000)),
                allocations = listOf(ExpenseAllocation(aliceId, 6000))
            )
        )

        assertEquals(0, service.processDueOccurrences(asOfDate = schedule.startDate))
        assertTrue(service.getSchedule(schedule.scheduleId)?.paused == true)
        assertEquals(
            "invalid_membership",
            outboxStore.snapshot().single { it.aggregateId == schedule.scheduleId }.payload["reason"]
        )
        assertTrue(service.getOccurrences(schedule.scheduleId).isEmpty())
    }

    @Test
    fun `does not process paused schedules`() {
        val group = groupStore.create("alice", CreateGroupRequest("Ski House", "HOUSEHOLD", "EUR"))
        val startDate = LocalDate.of(2026, 9, 1)

        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Firewood",
                amountMinor = 1500,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        service.pauseSchedule(schedule.scheduleId)

        val processed = service.processDueOccurrences(asOfDate = LocalDate.of(2026, 9, 15))
        assertEquals(0, processed)

        val occurrences = service.getOccurrences(schedule.scheduleId)
        assertEquals(0, occurrences.size)

        val updatedSchedule = service.getSchedule(schedule.scheduleId)
        assertEquals(startDate, updatedSchedule?.nextOccurrenceDate)
    }

    @Test
    fun `respects end date and stops generating occurrences`() {
        val group = groupStore.create("alice", CreateGroupRequest("Summer Rent", "HOUSEHOLD", "EUR"))
        val startDate = LocalDate.of(2026, 9, 1)
        val endDate = LocalDate.of(2026, 9, 8)

        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Weekly rent",
                amountMinor = 10000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate,
                endDate = endDate
            )
        )

        val processed = service.processDueOccurrences(asOfDate = LocalDate.of(2026, 9, 20))
        assertEquals(2, processed)

        val occurrences = service.getOccurrences(schedule.scheduleId)
        assertEquals(2, occurrences.size)
        assertEquals(listOf(startDate, endDate), occurrences.map { it.occurrenceDate }.sorted())

        val processedAgain = service.processDueOccurrences(asOfDate = LocalDate.of(2026, 9, 25))
        assertEquals(0, processedAgain)
    }

    @Test
    fun `handles monthly schedule recurrence and month end clamping`() {
        val group = groupStore.create("alice", CreateGroupRequest("Home", "HOUSEHOLD", "USD"))
        val startDate = LocalDate.of(2026, 1, 31)

        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Monthly gym",
                amountMinor = 5000,
                currency = "USD",
                frequency = RecurrenceFrequency.MONTHLY,
                dayOfMonth = 31,
                startDate = startDate
            )
        )

        val processed = service.processDueOccurrences(asOfDate = startDate)
        assertEquals(1, processed)

        val updated = service.getSchedule(schedule.scheduleId)
        assertEquals(LocalDate.of(2026, 2, 28), updated?.nextOccurrenceDate)
    }

    @Test
    fun `supports specified custom payers and allocations`() {
        val group = groupStore.create("alice", CreateGroupRequest("Studio", "HOUSEHOLD", "EUR"))
        val invite = groupStore.invite(group.groupId, "alice", CreateInviteRequest(24))
        groupStore.claim(invite.token, "bob")

        val members = groupStore.listMembers(group.groupId, "alice")
        val aliceId = members.first { it.subject == "alice" }.membershipId
        val bobId = members.first { it.subject == "bob" }.membershipId
        val startDate = LocalDate.of(2026, 9, 1)

        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Custom Split Power",
                amountMinor = 6000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate,
                payers = listOf(ExpensePayer(aliceId, 6000)),
                allocations = listOf(ExpenseAllocation(aliceId, 4000), ExpenseAllocation(bobId, 2000))
            )
        )

        val processed = service.processDueOccurrences(asOfDate = startDate)
        assertEquals(1, processed)

        val occurrences = service.getOccurrences(schedule.scheduleId)
        val expense = expenseStore.findById(occurrences[0].expenseId!!)
        assertNotNull(expense)
        assertEquals(listOf(ExpensePayer(aliceId, 6000)), expense?.payers)
        assertEquals(
            listOf(ExpenseAllocation(aliceId, 4000), ExpenseAllocation(bobId, 2000)),
            expense?.allocations
        )
    }

    @Test
    fun `fills only the omitted side of a custom recurring specification`() {
        val group = groupStore.create("alice", CreateGroupRequest("One-sided custom specs", "HOUSEHOLD", "EUR"))
        val invite = groupStore.invite(group.groupId, "alice", CreateInviteRequest(24))
        groupStore.claim(invite.token, "bob")

        val members = groupStore.listMembers(group.groupId, "alice")
        val aliceId = members.first { it.subject == "alice" }.membershipId
        val bobId = members.first { it.subject == "bob" }.membershipId
        val startDate = LocalDate.of(2026, 9, 1)

        val payersOnly = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Payer only",
                amountMinor = 6000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate,
                payers = listOf(ExpensePayer(bobId, 6000))
            )
        )
        val allocationsOnly = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Allocation only",
                amountMinor = 6000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate,
                allocations = listOf(
                    ExpenseAllocation(aliceId, 4000),
                    ExpenseAllocation(bobId, 2000)
                )
            )
        )

        assertEquals(2, service.processDueOccurrences(asOfDate = startDate))

        val payerOnlyExpense = expenseStore.findById(
            service.getOccurrences(payersOnly.scheduleId).single().expenseId!!
        )
        assertEquals(listOf(ExpensePayer(bobId, 6000)), payerOnlyExpense?.payers)
        assertEquals(listOf(3000L, 3000L), payerOnlyExpense?.allocations?.map { it.allocatedMinor }?.sorted())

        val allocationOnlyExpense = expenseStore.findById(
            service.getOccurrences(allocationsOnly.scheduleId).single().expenseId!!
        )
        assertEquals(listOf(ExpensePayer(aliceId, 6000)), allocationOnlyExpense?.payers)
        assertEquals(
            listOf(
                ExpenseAllocation(aliceId, 4000),
                ExpenseAllocation(bobId, 2000)
            ),
            allocationOnlyExpense?.allocations
        )
    }

    @Test
    fun `idempotency skips already generated occurrence`() {
        val group = groupStore.create("alice", CreateGroupRequest("Dorm", "HOUSEHOLD", "EUR"))
        val startDate = LocalDate.of(2026, 9, 1)

        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Wifi",
                amountMinor = 2000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        val processed = service.processDueOccurrences(asOfDate = startDate)
        assertEquals(1, processed)

        schedule.nextOccurrenceDate = startDate
        scheduleRepository.save(schedule)

        val processedDuplicate = service.processDueOccurrences(asOfDate = startDate)
        assertEquals(0, processedDuplicate)

        val occurrences = service.getOccurrences(schedule.scheduleId)
        assertEquals(1, occurrences.size)
    }

    /** Verifies the schedule/date uniqueness guard independently of occurrence-ID equality. */
    @Test
    fun `idempotency skips an existing occurrence with a different occurrence id`() {
        val group = groupStore.create("alice", CreateGroupRequest("Legacy occurrence", "HOUSEHOLD", "EUR"))
        val startDate = LocalDate.of(2026, 9, 1)
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Internet",
                amountMinor = 2000,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        service.processDueOccurrences(asOfDate = startDate)
        val generated = occurrenceRepository.findByScheduleIdAndOccurrenceDate(schedule.scheduleId, startDate)!!
        val generatedExpenseId = generated.expenseId
        occurrenceRepository.delete(generated)
        occurrenceRepository.flush()
        occurrenceRepository.saveAndFlush(
            RecurringExpenseOccurrence(
                occurrenceId = UUID.randomUUID(),
                scheduleId = schedule.scheduleId,
                occurrenceDate = startDate,
                expenseId = generatedExpenseId,
                createdAt = Instant.parse("2026-09-01T00:00:00Z")
            )
        )

        schedule.nextOccurrenceDate = startDate
        scheduleRepository.saveAndFlush(schedule)

        assertEquals(0, service.processDueOccurrences(asOfDate = startDate))
        assertEquals(1, service.getOccurrences(schedule.scheduleId).size)
        assertNotNull(generatedExpenseId)
        assertNotNull(expenseStore.findById(generatedExpenseId!!))
    }

    @Test
    fun `validation rejects invalid inputs`() {
        val group = groupStore.create("alice", CreateGroupRequest("Test", "HOUSEHOLD", "EUR"))

        assertThrows(IllegalArgumentException::class.java) {
            service.createSchedule(
                group.groupId,
                CreateRecurringScheduleRequest(
                    description = " ",
                    amountMinor = 1000,
                    currency = "EUR",
                    frequency = RecurrenceFrequency.WEEKLY,
                    startDate = LocalDate.now()
                )
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            service.createSchedule(
                group.groupId,
                CreateRecurringScheduleRequest(
                    description = "Sub",
                    amountMinor = 0,
                    currency = "EUR",
                    frequency = RecurrenceFrequency.WEEKLY,
                    startDate = LocalDate.now()
                )
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            service.createSchedule(
                group.groupId,
                CreateRecurringScheduleRequest(
                    description = "Sub",
                    amountMinor = 1000,
                    currency = "INVALID",
                    frequency = RecurrenceFrequency.WEEKLY,
                    startDate = LocalDate.now()
                )
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            service.createSchedule(
                group.groupId,
                CreateRecurringScheduleRequest(
                    description = "Sub",
                    amountMinor = 1000,
                    currency = "EUR",
                    frequency = RecurrenceFrequency.WEEKLY,
                    dayOfMonth = 15,
                    startDate = LocalDate.now()
                )
            )
        }

        assertThrows(SquarewiseException::class.java) {
            service.createSchedule(
                UUID.randomUUID(),
                CreateRecurringScheduleRequest(
                    description = "Sub",
                    amountMinor = 1000,
                    currency = "EUR",
                    frequency = RecurrenceFrequency.WEEKLY,
                    startDate = LocalDate.now()
                )
            )
        }
    }

    @Test
    fun `update validation rejects invalid schedule state before saving`() {
        val group = groupStore.create("alice", CreateGroupRequest("Update validation", "HOUSEHOLD", "EUR"))
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Valid schedule",
                amountMinor = 1000,
                currency = "EUR",
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 9, 1)
            )
        )
        val valid = UpdateRecurringScheduleRequest(
            description = "Updated schedule",
            amountMinor = 1000,
            currency = "EUR",
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 9, 1)
        )

        assertThrows(SquarewiseException::class.java) {
            service.updateSchedule(UUID.randomUUID(), schedule.scheduleId, valid)
        }
        assertThrows(SquarewiseException::class.java) {
            service.updateSchedule(group.groupId, UUID.randomUUID(), valid)
        }
        assertThrows(IllegalArgumentException::class.java) { service.updateSchedule(group.groupId, schedule.scheduleId, valid.copy(description = " ")) }
        assertThrows(IllegalArgumentException::class.java) { service.updateSchedule(group.groupId, schedule.scheduleId, valid.copy(amountMinor = 0)) }
        assertThrows(IllegalArgumentException::class.java) { service.updateSchedule(group.groupId, schedule.scheduleId, valid.copy(currency = "eur")) }
        assertThrows(IllegalArgumentException::class.java) { service.updateSchedule(group.groupId, schedule.scheduleId, valid.copy(dayOfMonth = 0)) }
        assertThrows(IllegalArgumentException::class.java) { service.updateSchedule(group.groupId, schedule.scheduleId, valid.copy(frequency = RecurrenceFrequency.WEEKLY, dayOfMonth = 1)) }
        assertThrows(IllegalArgumentException::class.java) {
            service.updateSchedule(group.groupId, schedule.scheduleId, valid.copy(endDate = LocalDate.of(2026, 8, 31)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.updateSchedule(group.groupId, schedule.scheduleId, valid.copy(payers = emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.updateSchedule(group.groupId, schedule.scheduleId, valid.copy(payers = listOf(ExpensePayer(UUID.randomUUID(), 999))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.updateSchedule(group.groupId, schedule.scheduleId, valid.copy(allocations = emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.updateSchedule(group.groupId, schedule.scheduleId, valid.copy(allocations = listOf(ExpenseAllocation(UUID.randomUUID(), 999))))
        }

        assertEquals("Valid schedule", service.getSchedule(schedule.scheduleId)?.description)
    }

    @Test
    fun `create validation rejects date and participant invariants`() {
        val group = groupStore.create("alice", CreateGroupRequest("Create validation", "HOUSEHOLD", "EUR"))
        val valid = CreateRecurringScheduleRequest(
            description = "Valid schedule",
            amountMinor = 1000,
            currency = "EUR",
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 9, 1)
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.createSchedule(group.groupId, valid.copy(endDate = LocalDate.of(2026, 8, 31)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.createSchedule(group.groupId, valid.copy(dayOfMonth = 32))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.createSchedule(group.groupId, valid.copy(payers = emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.createSchedule(group.groupId, valid.copy(payers = listOf(ExpensePayer(UUID.randomUUID(), 999))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.createSchedule(group.groupId, valid.copy(allocations = emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.createSchedule(group.groupId, valid.copy(allocations = listOf(ExpenseAllocation(UUID.randomUUID(), 999))))
        }

        assertEquals(0, scheduleRepository.count())
    }

    @Test
    fun `generates recurring allocations for legacy non-UUID subjects`() {
        val legacySubject = "legacy-user"
        val group = groupStore.create(legacySubject, CreateGroupRequest("Legacy members", "HOUSEHOLD", "EUR"))
        val member = groupStore.listMembers(group.groupId, legacySubject).single()
        val startDate = LocalDate.of(2026, 10, 1)
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Legacy subscription",
                amountMinor = 1200,
                currency = "EUR",
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = startDate
            )
        )

        assertEquals(1, service.processDueOccurrences(asOfDate = startDate))

        val expenseId = service.getOccurrences(schedule.scheduleId).single().expenseId
        val expense = expenseStore.findById(expenseId!!)
        assertEquals(listOf(member.membershipId), expense?.allocations?.map { it.participantId })
    }

    @Test
    fun `preserves UUID-shaped member subjects when generating recurring allocations`() {
        val memberId = UUID.randomUUID()
        val group = groupStore.create(memberId.toString(), CreateGroupRequest("UUID members", "HOUSEHOLD", "EUR"))
        val member = groupStore.listMembers(group.groupId, memberId.toString()).single()
        val startDate = LocalDate.of(2026, 11, 1)
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "UUID subscription",
                amountMinor = 1200,
                currency = "EUR",
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = startDate
            )
        )

        assertEquals(1, service.processDueOccurrences(asOfDate = startDate))

        val expenseId = service.getOccurrences(schedule.scheduleId).single().expenseId
        val expense = expenseStore.findById(expenseId!!)
        assertEquals(listOf(member.membershipId), expense?.allocations?.map { it.participantId })
    }
}
