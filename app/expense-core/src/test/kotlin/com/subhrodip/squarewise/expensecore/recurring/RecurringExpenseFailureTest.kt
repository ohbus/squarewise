package com.subhrodip.squarewise.expensecore.recurring

import com.subhrodip.squarewise.expensecore.expenses.persistence.store.ExpenseStore
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.persistence.store.JpaGroupStore
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore
import com.subhrodip.squarewise.expensecore.recurring.api.CreateRecurringScheduleRequest
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrenceFrequency
import com.subhrodip.squarewise.expensecore.recurring.service.RecurringExpenseService
import java.time.LocalDate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional

/** Verifies recurring generation pauses safely when the downstream expense store fails. */
@SpringBootTest
@Transactional
class RecurringExpenseFailureTest @Autowired constructor(
    private val service: RecurringExpenseService,
    private val groupStore: JpaGroupStore,
    private val outboxStore: OutboxStore
) {
    @MockitoBean
    private lateinit var expenseStore: ExpenseStore

    @Test
    fun `pauses schedule and emits generation error when expense creation fails`() {
        doThrow(IllegalStateException("expense store unavailable"))
            .`when`(expenseStore)
            .create(
                any(UUID::class.java) ?: UUID(0, 0),
                any(ExpenseRecord::class.java) ?: ExpenseRecord(
                    UUID(0, 0), UUID(0, 0), "", "", "", 0, 1, "", Instant.EPOCH, emptyList(), emptyList()
                ),
                any(String::class.java) ?: "test-idempotency",
                isNull(String::class.java)
            )

        val group = groupStore.create("recurring-failure-owner", CreateGroupRequest("Failure group", "TRIP", "EUR"))
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Failed recurring expense",
                amountMinor = 1200,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = LocalDate.of(2026, 10, 1)
            )
        )

        assertEquals(0, service.processDueOccurrences(LocalDate.of(2026, 10, 1)))
        assertTrue(service.getSchedule(schedule.scheduleId)?.paused == true)
        assertTrue(service.getOccurrences(schedule.scheduleId).isEmpty())
        assertEquals(
            "generation_error",
            outboxStore.snapshot().single { it.aggregateId == schedule.scheduleId }.payload["reason"]
        )
    }

    @Test
    fun `rethrows data access failure so the due batch cannot continue`() {
        doThrow(DataIntegrityViolationException("database unavailable"))
            .`when`(expenseStore)
            .create(
                any(UUID::class.java) ?: UUID(0, 0),
                any(ExpenseRecord::class.java) ?: ExpenseRecord(
                    UUID(0, 0), UUID(0, 0), "", "", "", 0, 1, "", Instant.EPOCH, emptyList(), emptyList()
                ),
                any(String::class.java) ?: "test-idempotency",
                isNull(String::class.java)
            )

        val group = groupStore.create("recurring-data-access", CreateGroupRequest("Data access group", "TRIP", "EUR"))
        service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Database failure",
                amountMinor = 1200,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = LocalDate.of(2026, 10, 1)
            )
        )

        assertThrows(DataIntegrityViolationException::class.java) {
            service.processDueOccurrences(LocalDate.of(2026, 10, 1))
        }
    }

    @Test
    fun `leaves schedule due when expense creation times out`() {
        doAnswer { throw TimeoutException("expense store timed out") }
            .`when`(expenseStore)
            .create(
                any(UUID::class.java) ?: UUID(0, 0),
                any(ExpenseRecord::class.java) ?: ExpenseRecord(
                    UUID(0, 0), UUID(0, 0), "", "", "", 0, 1, "", Instant.EPOCH, emptyList(), emptyList()
                ),
                any(String::class.java) ?: "test-idempotency",
                isNull(String::class.java)
            )

        val group = groupStore.create("recurring-timeout", CreateGroupRequest("Timeout group", "TRIP", "EUR"))
        val startDate = LocalDate.of(2026, 10, 1)
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Timed out recurring expense",
                amountMinor = 1200,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        assertEquals(0, service.processDueOccurrences(startDate))
        assertTrue(service.getSchedule(schedule.scheduleId)?.paused == false)
        assertEquals(startDate, service.getSchedule(schedule.scheduleId)?.nextOccurrenceDate)
    }

    @Test
    fun `rethrows cancellation without pausing schedule`() {
        doThrow(CancellationException("worker cancelled"))
            .`when`(expenseStore)
            .create(
                any(UUID::class.java) ?: UUID(0, 0),
                any(ExpenseRecord::class.java) ?: ExpenseRecord(
                    UUID(0, 0), UUID(0, 0), "", "", "", 0, 1, "", Instant.EPOCH, emptyList(), emptyList()
                ),
                any(String::class.java) ?: "test-idempotency",
                isNull(String::class.java)
            )

        val group = groupStore.create("recurring-cancel", CreateGroupRequest("Cancellation group", "TRIP", "EUR"))
        val startDate = LocalDate.of(2026, 10, 1)
        val schedule = service.createSchedule(
            group.groupId,
            CreateRecurringScheduleRequest(
                description = "Cancelled recurring expense",
                amountMinor = 1200,
                currency = "EUR",
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = startDate
            )
        )

        assertThrows(CancellationException::class.java) {
            service.processDueOccurrences(startDate)
        }
        assertTrue(service.getSchedule(schedule.scheduleId)?.paused == false)
    }
}
