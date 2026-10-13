package com.subhrodip.squarewise.expensecore.recurring

import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
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

import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.recurring.api.CreateRecurringScheduleRequest
import com.subhrodip.squarewise.expensecore.recurring.service.RecurringExpenseService
import com.subhrodip.squarewise.expensecore.recurring.persistence.RecurringExpenseScheduleRepository
import com.subhrodip.squarewise.expensecore.recurring.persistence.RecurringExpenseOccurrenceRepository
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.ExpenseStore
import com.subhrodip.squarewise.expensecore.validation.EntityReferenceValidator
import jakarta.persistence.EntityManager

@SpringBootTest
@Transactional
class RecurringExpenseControllerTest @Autowired constructor(
    private val controller: RecurringExpenseController,
    private val groupStore: JpaGroupStore,
    private val recurringService: RecurringExpenseService,
    private val groupRepository: GroupRepository,
    private val membershipRepository: GroupMembershipRepository,
    private val scheduleRepository: RecurringExpenseScheduleRepository,
    private val occurrenceRepository: RecurringExpenseOccurrenceRepository,
    private val expenseStore: ExpenseStore,
    private val entityManager: EntityManager
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
            timezone = "Europe/Vienna",
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
        assertEquals("Europe/Vienna", created.timezone)
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
        assertEquals("Europe/Vienna", updated.timezone)

        val explicitlyRetimed = controller.updateSchedule(
            group.groupId,
            created.scheduleId,
            updateRequest.copy(timezone = "UTC"),
            alice
        )
        assertEquals("UTC", explicitlyRetimed.timezone)

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

        val createErr = assertThrows<SquarewiseException> {
            controller.createSchedule(randomGroup, createRequest, alice)
        }
        assertEquals(CategoryCode.NOT_FOUND, createErr.definition.category)

        val getErr = assertThrows<SquarewiseException> {
            controller.getSchedule(randomGroup, randomSchedule, alice)
        }
        assertEquals(CategoryCode.NOT_FOUND, getErr.definition.category)
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
            val error = assertThrows<SquarewiseException> { operation() }
            assertEquals(CategoryCode.NOT_FOUND, error.definition.category)
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
            val error = assertThrows<SquarewiseException> {
                controller.createSchedule(group.groupId, validRequest.copy(amount = MoneyDto("EUR", amount)), alice)
            }
            assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
        }

        val blankSubject = assertThrows<SquarewiseException> {
            controller.createSchedule(group.groupId, validRequest, Principal { "   " })
        }
        assertEquals(CategoryCode.AUTHENTICATION_ERROR, blankSubject.definition.category)

        val nonMember = assertThrows<SquarewiseException> {
            controller.createSchedule(group.groupId, validRequest, Principal { "bob" })
        }
        assertEquals(CategoryCode.NOT_FOUND, nonMember.definition.category)
    }

    @Test
    fun `rejects currency mismatch between schedule and custom payers or allocations`() {
        val group = groupStore.create("alice", CreateGroupRequest("Currency mismatch group", "TRIP", "EUR"))
        val aliceId = groupStore.listMembers(group.groupId, "alice").first().membershipId.toString()
        val validRequest = CreateRecurringScheduleRequestDto(
            description = "EUR schedule",
            amount = MoneyDto("EUR", "1000"),
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = LocalDate.of(2026, 10, 1)
        )

        val payerCurrencyMismatch = validRequest.copy(
            payers = listOf(ExpensePayerDto(aliceId, MoneyDto("USD", "1000")))
        )
        val errorPayer = assertThrows<SquarewiseException> {
            controller.createSchedule(group.groupId, payerCurrencyMismatch, alice)
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, errorPayer.definition.category)

        val allocationCurrencyMismatch = validRequest.copy(
            allocations = listOf(ExpenseAllocationItemDto(aliceId, MoneyDto("USD", "1000")))
        )
        val errorAlloc = assertThrows<SquarewiseException> {
            controller.createSchedule(group.groupId, allocationCurrencyMismatch, alice)
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, errorAlloc.definition.category)
    }

    @Test
    fun `rejects non-positive custom payer or allocation amounts`() {
        val group = groupStore.create("alice", CreateGroupRequest("Non positive group", "TRIP", "EUR"))
        val aliceId = groupStore.listMembers(group.groupId, "alice").first().membershipId.toString()
        val validRequest = CreateRecurringScheduleRequestDto(
            description = "EUR schedule",
            amount = MoneyDto("EUR", "1000"),
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = LocalDate.of(2026, 10, 1)
        )

        val zeroPayer = validRequest.copy(
            payers = listOf(ExpensePayerDto(aliceId, MoneyDto("EUR", "0")), ExpensePayerDto(aliceId, MoneyDto("EUR", "1000")))
        )
        val errorPayer = assertThrows<SquarewiseException> {
            controller.createSchedule(group.groupId, zeroPayer, alice)
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, errorPayer.definition.category)

        val zeroAlloc = validRequest.copy(
            allocations = listOf(ExpenseAllocationItemDto(aliceId, MoneyDto("EUR", "0")), ExpenseAllocationItemDto(aliceId, MoneyDto("EUR", "1000")))
        )
        val errorAlloc = assertThrows<SquarewiseException> {
            controller.createSchedule(group.groupId, zeroAlloc, alice)
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, errorAlloc.definition.category)
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

        val error = assertThrows<SquarewiseException> {
            controller.createSchedule(group.groupId, request, null)
        }
        assertEquals(CategoryCode.AUTHENTICATION_ERROR, error.definition.category)
    }

    @Test
    fun `listSchedules succeeds and reuses group access cache`() {
        val group = groupStore.create("alice", CreateGroupRequest("Cached Access", "TRIP", "USD"))
        val list1 = controller.listSchedules(group.groupId, alice)
        val list2 = controller.listSchedules(group.groupId, alice)
        assertEquals(list1.size, list2.size)
    }

    @Test
    fun `rejects blank principal name`() {
        val group = groupStore.create("alice", CreateGroupRequest("Blank Principal", "TRIP", "EUR"))
        val request = CreateRecurringScheduleRequestDto(
            description = "Blank user",
            amount = MoneyDto("EUR", "100"),
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = LocalDate.of(2026, 10, 1)
        )
        val error = assertThrows<SquarewiseException> {
            controller.createSchedule(group.groupId, request, Principal { "   " })
        }
        assertEquals(CategoryCode.AUTHENTICATION_ERROR, error.definition.category)
    }

    @Test
    fun `rejects non-existent group`() {
        val request = CreateRecurringScheduleRequestDto(
            description = "Missing group",
            amount = MoneyDto("EUR", "100"),
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = LocalDate.of(2026, 10, 1)
        )
        val error = assertThrows<SquarewiseException> {
            controller.createSchedule(UUID.randomUUID(), request, alice)
        }
        assertEquals(CategoryCode.NOT_FOUND, error.definition.category)
    }

    @Test
    fun `rejects non-member with access hidden`() {
        val group = groupStore.create("alice", CreateGroupRequest("Bob Excluded", "TRIP", "EUR"))
        val request = CreateRecurringScheduleRequestDto(
            description = "Bob schedule",
            amount = MoneyDto("EUR", "100"),
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = LocalDate.of(2026, 10, 1)
        )
        val error = assertThrows<SquarewiseException> {
            controller.createSchedule(group.groupId, request, Principal { "bob" })
        }
        assertEquals(CategoryCode.NOT_FOUND, error.definition.category)
    }

    @Test
    fun `controller without group access cache functions correctly`() {
        val uncachedController = RecurringExpenseController(
            recurringService = recurringService,
            groupRepository = groupRepository,
            membershipRepository = membershipRepository,
            groupAccessCache = null
        )
        val group = groupStore.create("alice", CreateGroupRequest("Uncached Controller", "TRIP", "EUR"))
        val schedules = uncachedController.listSchedules(group.groupId, alice)
        assertEquals(0, schedules.size)
    }

    @Test
    fun `RecurringExpenseService rejects non-existent group on schedule creation`() {
        val serviceWithValidator = RecurringExpenseService(
            scheduleRepository = scheduleRepository,
            occurrenceRepository = occurrenceRepository,
            expenseStore = expenseStore,
            groupRepository = groupRepository,
            entityManager = entityManager,
            referenceValidator = EntityReferenceValidator(groupRepository, membershipRepository)
        )
        val missingGroupId = UUID.randomUUID()
        val request = CreateRecurringScheduleRequest(
            description = "Missing group schedule",
            amountMinor = 1000,
            currency = "EUR",
            frequency = RecurrenceFrequency.MONTHLY,
            dayOfMonth = 1,
            startDate = LocalDate.of(2026, 10, 1),
            timezone = "Europe/Vienna"
        )
        val error = assertThrows<SquarewiseException> {
            serviceWithValidator.createSchedule(missingGroupId, request)
        }
        assertEquals(CategoryCode.NOT_FOUND, error.definition.category)
    }
}
