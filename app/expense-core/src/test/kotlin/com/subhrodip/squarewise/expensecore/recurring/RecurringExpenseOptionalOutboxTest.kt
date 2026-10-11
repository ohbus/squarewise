package com.subhrodip.squarewise.expensecore.recurring

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.ExpenseStore
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrenceFrequency
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule
import com.subhrodip.squarewise.expensecore.recurring.persistence.RecurringExpenseOccurrenceRepository
import com.subhrodip.squarewise.expensecore.recurring.persistence.RecurringExpenseScheduleRepository
import com.subhrodip.squarewise.expensecore.recurring.service.RecurringExpenseService
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies recurring failure handling remains safe when the optional outbox is absent. */
class RecurringExpenseOptionalOutboxTest {
    /**
     * Confirms a failed generated expense pauses the schedule without requiring
     * an optional outbox adapter or advancing the due occurrence.
     */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `pauses failed generation without throwing when no outbox is configured`() {
        val scheduleRepository = mock(RecurringExpenseScheduleRepository::class.java)
        val occurrenceRepository = mock(RecurringExpenseOccurrenceRepository::class.java)
        val expenseStore = mock(ExpenseStore::class.java)
        val groupRepository = mock(GroupRepository::class.java)
        val entityManager = mock(EntityManager::class.java)
        val query = mock(TypedQuery::class.java) as TypedQuery<Array<Any>>
        val groupId = UUID.randomUUID()
        val scheduleId = UUID.randomUUID()
        val occurrenceDate = LocalDate.of(2026, 10, 1)
        val schedule = RecurringExpenseSchedule(
            scheduleId = scheduleId,
            groupId = groupId,
            description = "Optional outbox failure",
            amountMinor = 1200,
            currency = "EUR",
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = occurrenceDate,
            nextOccurrenceDate = occurrenceDate,
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )

        `when`(scheduleRepository.findDueSchedules(occurrenceDate)).thenReturn(listOf(schedule))
        `when`(scheduleRepository.save(any(RecurringExpenseSchedule::class.java))).thenAnswer { it.arguments[0] }
        `when`(occurrenceRepository.existsById(any(UUID::class.java) ?: UUID(0, 0))).thenReturn(false)
        `when`(
            occurrenceRepository.existsByScheduleIdAndOccurrenceDate(
                any(UUID::class.java) ?: UUID(0, 0),
                any(LocalDate::class.java) ?: occurrenceDate
            )
        )
            .thenReturn(false)
        `when`(
            entityManager.createQuery(
                anyString(),
                any(Class::class.java) ?: Array<Any>::class.java
            )
        ).thenReturn(query)
        `when`(query.setParameter("groupId", groupId)).thenReturn(query)
        `when`(query.resultList).thenReturn(listOf(arrayOf("alice", UUID.randomUUID())))
        doThrow(IllegalStateException("expense store unavailable"))
            .`when`(expenseStore)
            .create(
                any(UUID::class.java) ?: UUID(0, 0),
                any(ExpenseRecord::class.java) ?: ExpenseRecord(
                    UUID(0, 0),
                    UUID(0, 0),
                    "",
                    "",
                    "EUR",
                    0,
                    1,
                    "EQUAL",
                    Instant.EPOCH,
                    emptyList(),
                    emptyList()
                ),
                anyString(),
                isNull()
            )

        val service = RecurringExpenseService(
            scheduleRepository = scheduleRepository,
            occurrenceRepository = occurrenceRepository,
            expenseStore = expenseStore,
            groupRepository = groupRepository,
            entityManager = entityManager,
            outboxStore = null
        )

        assertEquals(0, service.processDueOccurrences(occurrenceDate))
        assertTrue(schedule.paused)
        assertEquals(occurrenceDate, schedule.nextOccurrenceDate)
    }

    /** Verifies the direct-construction fallback mapper preserves Kotlin data classes. */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `default mapper round trips custom recurring split`() {
        val scheduleRepository = mock(RecurringExpenseScheduleRepository::class.java)
        val occurrenceRepository = mock(RecurringExpenseOccurrenceRepository::class.java)
        val expenseStore = mock(ExpenseStore::class.java)
        val groupRepository = mock(GroupRepository::class.java)
        val entityManager = mock(EntityManager::class.java)
        val query = mock(TypedQuery::class.java) as TypedQuery<UUID>
        val groupId = UUID.randomUUID()
        val payerId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        val occurrenceDate = LocalDate.of(2026, 10, 1)
        val schedule = RecurringExpenseSchedule(
            scheduleId = UUID.randomUUID(),
            groupId = groupId,
            description = "Custom split",
            amountMinor = 1200,
            currency = "EUR",
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = occurrenceDate,
            nextOccurrenceDate = occurrenceDate,
            createdAt = Instant.EPOCH
        ).apply {
            customSpecification = """{"payers":[{"participantId":"$payerId","amountMinor":1200}],"allocations":[{"participantId":"$payerId","allocatedMinor":600},{"participantId":"$otherId","allocatedMinor":600}]}"""
        }

        `when`(scheduleRepository.findDueSchedules(occurrenceDate)).thenReturn(listOf(schedule))
        `when`(scheduleRepository.save(any(RecurringExpenseSchedule::class.java))).thenAnswer { it.arguments[0] }
        `when`(occurrenceRepository.existsById(any(UUID::class.java) ?: UUID(0, 0))).thenReturn(false)
        `when`(
            occurrenceRepository.existsByScheduleIdAndOccurrenceDate(
                any(UUID::class.java) ?: UUID(0, 0),
                any(LocalDate::class.java) ?: occurrenceDate
            )
        ).thenReturn(false)
        `when`(entityManager.createQuery(anyString(), any(Class::class.java) ?: UUID::class.java)).thenReturn(query)
        `when`(query.setParameter("groupId", groupId)).thenReturn(query)
        `when`(query.resultList).thenReturn(listOf(payerId, otherId))
        var created: ExpenseRecord? = null
        doAnswer {
            created = it.arguments[1] as ExpenseRecord
            created
        }.`when`(expenseStore).create(
            any(UUID::class.java) ?: UUID(0, 0),
            any(ExpenseRecord::class.java) ?: ExpenseRecord(
                UUID(0, 0), UUID(0, 0), "", "", "EUR", 0, 1, "EQUAL", Instant.EPOCH, emptyList(), emptyList()
            ),
            anyString(),
            isNull()
        )

        val service = RecurringExpenseService(scheduleRepository, occurrenceRepository, expenseStore, groupRepository, entityManager)

        assertEquals(1, service.processDueOccurrences(occurrenceDate))
        assertEquals(listOf(ExpensePayer(payerId, 1200)), created?.payers)
        assertEquals(
            listOf(ExpenseAllocation(payerId, 600), ExpenseAllocation(otherId, 600)),
            created?.allocations
        )
    }
}
