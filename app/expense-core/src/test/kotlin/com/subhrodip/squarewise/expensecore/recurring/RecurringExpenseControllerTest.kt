package com.subhrodip.squarewise.expensecore.recurring
import com.subhrodip.squarewise.errors.domain.ApplicationException
import com.subhrodip.squarewise.errors.domain.ErrorCode
import org.junit.jupiter.api.assertThrows
import com.subhrodip.squarewise.expensecore.expenses.api.request.MoneyDto
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.persistence.store.JpaGroupStore
import com.subhrodip.squarewise.expensecore.recurring.api.CreateRecurringScheduleRequestDto
import com.subhrodip.squarewise.expensecore.recurring.api.ExpenseAllocationItemDto
import com.subhrodip.squarewise.expensecore.recurring.api.ExpensePayerDto
import com.subhrodip.squarewise.expensecore.recurring.api.RecurringExpenseController
import com.subhrodip.squarewise.expensecore.recurring.api.UpdateRecurringScheduleRequestDto
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrenceFrequency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID
import java.security.Principal

@SpringBootTest
@Transactional
class RecurringExpenseControllerTest @Autowired constructor(
    private val controller: RecurringExpenseController,
    private val groupStore: JpaGroupStore
) {
    private val alice = Principal { "alice" }

    @Test
    fun `creates, inspects, lists, updates, pauses, and resumes recurring schedule`() {
        val group = groupStore.create("alice", CreateGroupRequest("Penthouse", "HOUSEHOLD", "USD"))
        val aliceId = UUID.nameUUIDFromBytes("alice".toByteArray()).toString()
        val customPayers = listOf(ExpensePayerDto(aliceId, MoneyDto("USD", "250000")))
        val customAllocations = listOf(ExpenseAllocationItemDto(aliceId, MoneyDto("USD", "250000")))

        val createRequest = CreateRecurringScheduleRequestDto(
            description = "Monthly Rent",
            amount = MoneyDto("USD", "250000"),
            frequency = RecurrenceFrequency.MONTHLY,
            dayOfMonth = 1,
            startDate = LocalDate.of(2026, 10, 1),
            payers = customPayers,
            allocations = customAllocations
        )

        // 1. Create schedule
        val created = controller.createSchedule(group.groupId, createRequest, alice)
        assertNotNull(created.scheduleId)
        assertEquals(group.groupId, created.groupId)
        assertEquals("Monthly Rent", created.description)
        assertEquals("USD", created.amount.currency)
        assertEquals("250000", created.amount.minor)
        assertEquals(RecurrenceFrequency.MONTHLY, created.frequency)
        assertEquals(1, created.dayOfMonth)
        assertEquals(LocalDate.of(2026, 10, 1), created.startDate)
        assertEquals(null, created.endDate)
        assertEquals(LocalDate.of(2026, 10, 1), created.nextOccurrenceDate)
        assertNotNull(created.createdAt)
        assertEquals(1L, created.version)
        assertFalse(created.paused)

        // 2. Get schedule
        val retrieved = controller.getSchedule(group.groupId, created.scheduleId, alice)
        assertEquals(created.scheduleId, retrieved.scheduleId)
        assertEquals("Monthly Rent", retrieved.description)

        // 3. List schedules
        val list = controller.listSchedules(group.groupId, alice)
        assertEquals(1, list.size)
        assertEquals(created.scheduleId, list[0].scheduleId)

        // 4. Update schedule
        val updateRequest = UpdateRecurringScheduleRequestDto(
            description = "Monthly Rent & Water",
            amount = MoneyDto("USD", "270000"),
            frequency = RecurrenceFrequency.MONTHLY,
            dayOfMonth = 1,
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 12, 31),
            payers = listOf(ExpensePayerDto(aliceId, MoneyDto("USD", "270000"))),
            allocations = listOf(ExpenseAllocationItemDto(aliceId, MoneyDto("USD", "270000")))
        )
        val updated = controller.updateSchedule(group.groupId, created.scheduleId, updateRequest, alice)
        assertEquals("Monthly Rent & Water", updated.description)
        assertEquals("270000", updated.amount.minor)
        assertEquals(LocalDate.of(2026, 12, 31), updated.endDate)

        // 5. Pause schedule
        val paused = controller.pauseSchedule(group.groupId, created.scheduleId, alice)
        assertTrue(paused.paused)
        val pausedReplay = controller.pauseSchedule(group.groupId, created.scheduleId, alice)
        assertTrue(pausedReplay.paused)

        // 6. Resume schedule
        val resumed = controller.resumeSchedule(group.groupId, created.scheduleId, alice)
        assertFalse(resumed.paused)
        val resumedReplay = controller.resumeSchedule(group.groupId, created.scheduleId, alice)
        assertFalse(resumedReplay.paused)
    }

    /** Verifies update mapping preserves the service contract when either custom side is omitted. */
    @Test
    fun `maps one-sided custom payer and allocation updates`() {
        val group = groupStore.create("alice", CreateGroupRequest("One-sided updates", "HOUSEHOLD", "USD"))
        val aliceId = UUID.nameUUIDFromBytes("alice".toByteArray()).toString()
        val created = controller.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequestDto(
                description = "Shared cost",
                amount = MoneyDto("USD", "1000"),
                frequency = RecurrenceFrequency.MONTHLY,
                dayOfMonth = 1,
                startDate = LocalDate.of(2026, 10, 1)
            ),
            alice
        )

        val allocationOnly = controller.updateSchedule(
            group.groupId,
            created.scheduleId,
            UpdateRecurringScheduleRequestDto(
                description = "Allocation only",
                amount = MoneyDto("USD", "1200"),
                frequency = RecurrenceFrequency.MONTHLY,
                dayOfMonth = 1,
                startDate = LocalDate.of(2026, 10, 1),
                payers = null,
                allocations = listOf(ExpenseAllocationItemDto(aliceId, MoneyDto("USD", "1200")))
            ),
            alice
        )
        assertEquals("Allocation only", allocationOnly.description)
        assertEquals("1200", allocationOnly.amount.minor)

        val payerOnly = controller.updateSchedule(
            group.groupId,
            created.scheduleId,
            UpdateRecurringScheduleRequestDto(
                description = "Payer only",
                amount = MoneyDto("USD", "1300"),
                frequency = RecurrenceFrequency.MONTHLY,
                dayOfMonth = 1,
                startDate = LocalDate.of(2026, 10, 1),
                payers = listOf(ExpensePayerDto(aliceId, MoneyDto("USD", "1300"))),
                allocations = null
            ),
            alice
        )
        assertEquals("Payer only", payerOnly.description)
        assertEquals("1300", payerOnly.amount.minor)
    }

    @Test
    fun `throws 404 for non-existent group or schedule`() {
        val randomGroup = UUID.randomUUID()
        val randomSchedule = UUID.randomUUID()

        val createRequest = CreateRecurringScheduleRequestDto(
            description = "Internet",
            amount = MoneyDto("EUR", "5000"),
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = LocalDate.now()
        )

        val createErr = assertThrows<ApplicationException> {
            controller.createSchedule(randomGroup, createRequest, alice)
        }
        assertEquals(ErrorCode.ERR_05, createErr.errorCode)

        val getErr = assertThrows<ApplicationException> {
            controller.getSchedule(randomGroup, randomSchedule, alice)
        }
        assertEquals(ErrorCode.ERR_05, getErr.errorCode)
    }

    /** Verifies schedule lookup, pause, and resume reject missing and foreign schedules. */
    @Test
    fun `rejects missing and foreign schedules within an authorized group`() {
        val ownerGroup = groupStore.create("alice", CreateGroupRequest("Owner group", "TRIP", "EUR"))
        val otherGroup = groupStore.create("alice", CreateGroupRequest("Other group", "TRIP", "EUR"))
        val schedule = controller.createSchedule(
            ownerGroup.groupId,
            CreateRecurringScheduleRequestDto(
                description = "Shared cost",
                amount = MoneyDto("EUR", "100"),
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = LocalDate.of(2026, 10, 1)
            ),
            alice
        )
        val missingScheduleId = UUID.randomUUID()

        listOf(
            { controller.getSchedule(ownerGroup.groupId, missingScheduleId, alice) },
            { controller.getSchedule(otherGroup.groupId, schedule.scheduleId, alice) },
            { controller.pauseSchedule(ownerGroup.groupId, missingScheduleId, alice) },
            { controller.pauseSchedule(otherGroup.groupId, schedule.scheduleId, alice) },
            { controller.resumeSchedule(ownerGroup.groupId, missingScheduleId, alice) },
            { controller.resumeSchedule(otherGroup.groupId, schedule.scheduleId, alice) }
        ).forEach { operation ->
            val error = assertThrows<ApplicationException> { operation() }
            assertEquals(ErrorCode.ERR_05, error.errorCode)
        }
    }

    /** Verifies recurring transport rejects malformed amounts and unauthorized subjects. */
    @Test
    fun `rejects invalid amount and membership inputs before service mutation`() {
        val group = groupStore.create("alice", CreateGroupRequest("Validation group", "TRIP", "EUR"))
        val validRequest = CreateRecurringScheduleRequestDto(
            description = "Validated schedule",
            amount = MoneyDto("EUR", "100"),
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = LocalDate.of(2026, 10, 1)
        )

        listOf("not-an-integer", "0").forEach { amount ->
            val error = assertThrows<ApplicationException> {
                controller.createSchedule(group.groupId, validRequest.copy(amount = MoneyDto("EUR", amount)), alice)
            }
            assertEquals(ErrorCode.ERR_02, error.errorCode)
        }

        val blankSubject = assertThrows<ApplicationException> {
            controller.createSchedule(group.groupId, validRequest, Principal { "   " })
        }
        assertEquals(ErrorCode.ERR_03, blankSubject.errorCode)

        val nonMember = assertThrows<ApplicationException> {
            controller.createSchedule(group.groupId, validRequest, Principal { "bob" })
        }
        assertEquals(ErrorCode.ERR_05, nonMember.errorCode)
    }

    @Test
    fun `rejects missing principal for an existing group`() {
        val group = groupStore.create("alice", CreateGroupRequest("No Anonymous Schedules", "TRIP", "EUR"))
        val request = CreateRecurringScheduleRequestDto(
            description = "Unauthorized",
            amount = MoneyDto("EUR", "100"),
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = LocalDate.of(2026, 10, 1)
        )

        val error = assertThrows<ApplicationException> {
            controller.createSchedule(group.groupId, request, null)
        }
        assertEquals(ErrorCode.ERR_03, error.errorCode)
    }
}
