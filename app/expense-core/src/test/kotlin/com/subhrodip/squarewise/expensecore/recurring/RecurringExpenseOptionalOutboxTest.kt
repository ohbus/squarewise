package com.subhrodip.squarewise.expensecore.recurring

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.ExpenseStore
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.validation.EntityReferenceValidator
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrenceFrequency
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule
import com.subhrodip.squarewise.expensecore.recurring.persistence.RecurringExpenseOccurrenceRepository
import com.subhrodip.squarewise.expensecore.recurring.persistence.RecurringExpenseScheduleRepository
import com.subhrodip.squarewise.expensecore.recurring.service.RecurringExpenseService
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.dao.DataIntegrityViolationException
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager

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
        `when`(query.setMaxResults(101)).thenReturn(query)
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
            outboxStore = null,
            referenceValidator = EntityReferenceValidator(groupRepository, mock(GroupMembershipRepository::class.java))
        )

        inActiveTransaction { assertEquals(0, service.processDueOccurrences(occurrenceDate)) }
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
        `when`(query.setMaxResults(101)).thenReturn(query)
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

        val service = RecurringExpenseService(
            scheduleRepository = scheduleRepository,
            occurrenceRepository = occurrenceRepository,
            expenseStore = expenseStore,
            groupRepository = groupRepository,
            entityManager = entityManager,
            referenceValidator = EntityReferenceValidator(groupRepository, mock(GroupMembershipRepository::class.java))
        )

        inActiveTransaction { assertEquals(1, service.processDueOccurrences(occurrenceDate)) }
        assertEquals(listOf(ExpensePayer(payerId, 1200)), created?.payers)
        assertEquals(
            listOf(ExpenseAllocation(payerId, 600), ExpenseAllocation(otherId, 600)),
            created?.allocations
        )
    }

    /** Verifies the bounded member query still accepts an explicit custom participant on page 101. */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `accepts custom recurring participants when the bounded member query overflows`() {
        val scheduleRepository = mock(RecurringExpenseScheduleRepository::class.java)
        val occurrenceRepository = mock(RecurringExpenseOccurrenceRepository::class.java)
        val expenseStore = mock(ExpenseStore::class.java)
        val groupRepository = mock(GroupRepository::class.java)
        val entityManager = mock(EntityManager::class.java)
        val query = mock(TypedQuery::class.java) as TypedQuery<UUID>
        val groupId = UUID.randomUUID()
        val participantIds = (1..101).map { UUID.randomUUID() }
        val occurrenceDate = LocalDate.of(2026, 11, 1)
        val schedule = RecurringExpenseSchedule(
            scheduleId = UUID.randomUUID(),
            groupId = groupId,
            description = "Large group custom split",
            amountMinor = 1200,
            currency = "EUR",
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = occurrenceDate,
            nextOccurrenceDate = occurrenceDate,
            createdAt = Instant.EPOCH
        ).apply {
            customSpecification = """{"payers":[{"participantId":"${participantIds.first()}","amountMinor":1200}],"allocations":[{"participantId":"${participantIds.first()}","allocatedMinor":1200}]}"""
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
        `when`(query.setMaxResults(101)).thenReturn(query)
        `when`(query.resultList).thenReturn(participantIds)
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

        val service = RecurringExpenseService(
            scheduleRepository = scheduleRepository,
            occurrenceRepository = occurrenceRepository,
            expenseStore = expenseStore,
            groupRepository = groupRepository,
            entityManager = entityManager,
            referenceValidator = EntityReferenceValidator(groupRepository, mock(GroupMembershipRepository::class.java))
        )

        inActiveTransaction { assertEquals(1, service.processDueOccurrences(occurrenceDate)) }
        assertEquals(listOf(ExpensePayer(participantIds.first(), 1200)), created?.payers)
        assertEquals(listOf(ExpenseAllocation(participantIds.first(), 1200)), created?.allocations)
    }

    @Test
    fun `requires a transaction manager outside an active transaction`() {
        val scheduleRepository = mock(RecurringExpenseScheduleRepository::class.java)
        val service = RecurringExpenseService(
            scheduleRepository = scheduleRepository,
            occurrenceRepository = mock(RecurringExpenseOccurrenceRepository::class.java),
            expenseStore = mock(ExpenseStore::class.java),
            groupRepository = mock(GroupRepository::class.java),
            entityManager = mock(EntityManager::class.java),
            outboxStore = null,
            referenceValidator = EntityReferenceValidator(mock(GroupRepository::class.java), mock(GroupMembershipRepository::class.java))
        )
        val asOfDate = LocalDate.of(2026, 12, 1)
        `when`(scheduleRepository.findDueSchedules(asOfDate)).thenReturn(emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            service.processDueOccurrences(asOfDate)
        }
    }

    @Test
    fun `processes due schedules through independent transaction rechecks`() {
        val scheduleRepository = mock(RecurringExpenseScheduleRepository::class.java)
        val occurrenceRepository = mock(RecurringExpenseOccurrenceRepository::class.java)
        val expenseStore = mock(ExpenseStore::class.java)
        val groupRepository = mock(GroupRepository::class.java)
        val entityManager = mock(EntityManager::class.java)
        val query = mock(TypedQuery::class.java) as TypedQuery<UUID>
        val groupId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val asOfDate = LocalDate.of(2026, 12, 1)
        val missingId = UUID.randomUUID()
        val pausedId = UUID.randomUUID()
        val staleId = UUID.randomUUID()
        val failingId = UUID.randomUUID()
        val schedules = listOf(
            testSchedule(missingId, groupId, asOfDate),
            testSchedule(pausedId, groupId, asOfDate).apply { paused = true },
            testSchedule(staleId, groupId, asOfDate.plusDays(1)),
            testSchedule(failingId, groupId, asOfDate)
        )

        `when`(scheduleRepository.findDueSchedules(asOfDate)).thenReturn(schedules)
        doAnswer {
            when (it.arguments[0] as UUID) {
                missingId -> null
                pausedId -> schedules[1]
                staleId -> schedules[2]
                failingId -> throw DataIntegrityViolationException("claim failed")
                else -> error("unexpected schedule claim")
            }
        }.`when`(scheduleRepository).findForUpdate(any(UUID::class.java) ?: UUID(0, 0))
        `when`(
            entityManager.createQuery(anyString(), any(Class::class.java) ?: UUID::class.java)
        ).thenReturn(query)
        `when`(query.setParameter("groupId", groupId)).thenReturn(query)
        `when`(query.setMaxResults(101)).thenReturn(query)
        `when`(query.resultList).thenReturn(listOf(memberId))

        val service = RecurringExpenseService(
            scheduleRepository = scheduleRepository,
            occurrenceRepository = occurrenceRepository,
            expenseStore = expenseStore,
            groupRepository = groupRepository,
            entityManager = entityManager,
            outboxStore = null,
            transactionManager = TestTransactionManager(),
            referenceValidator = EntityReferenceValidator(groupRepository, mock(GroupMembershipRepository::class.java))
        )

        assertEquals(0, service.processDueOccurrences(asOfDate))
        assertEquals(asOfDate, schedules[0].nextOccurrenceDate)
        assertEquals(asOfDate, schedules[1].nextOccurrenceDate)
        assertEquals(asOfDate.plusDays(1), schedules[2].nextOccurrenceDate)
        assertEquals(asOfDate, schedules[3].nextOccurrenceDate)
        verify(scheduleRepository).findForUpdate(failingId)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `skips invalid timezone in active transaction and continues later schedules`() {
        val scheduleRepository = mock(RecurringExpenseScheduleRepository::class.java)
        val occurrenceRepository = mock(RecurringExpenseOccurrenceRepository::class.java)
        val expenseStore = mock(ExpenseStore::class.java)
        val groupRepository = mock(GroupRepository::class.java)
        val entityManager = mock(EntityManager::class.java)
        val query = mock(TypedQuery::class.java) as TypedQuery<UUID>
        val groupId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val asOfDate = LocalDate.now(ZoneOffset.UTC).plusDays(1)
        val occurrenceDate = asOfDate.minusDays(1)
        val invalid = testSchedule(UUID.randomUUID(), groupId, occurrenceDate).apply { timezone = "Invalid/Zone" }
        val later = testSchedule(UUID.randomUUID(), groupId, occurrenceDate)

        `when`(scheduleRepository.findDueSchedules(asOfDate)).thenReturn(listOf(invalid, later))
        `when`(scheduleRepository.save(any(RecurringExpenseSchedule::class.java))).thenAnswer { it.arguments[0] }
        `when`(occurrenceRepository.existsById(any(UUID::class.java) ?: UUID(0, 0))).thenReturn(false)
        `when`(
            occurrenceRepository.existsByScheduleIdAndOccurrenceDate(
                any(UUID::class.java) ?: UUID(0, 0),
                any(LocalDate::class.java) ?: asOfDate
            )
        ).thenReturn(false)
        `when`(entityManager.createQuery(anyString(), any(Class::class.java) ?: UUID::class.java)).thenReturn(query)
        `when`(query.setParameter("groupId", groupId)).thenReturn(query)
        `when`(query.setMaxResults(101)).thenReturn(query)
        `when`(query.resultList).thenReturn(listOf(memberId))

        val service = RecurringExpenseService(
            scheduleRepository = scheduleRepository,
            occurrenceRepository = occurrenceRepository,
            expenseStore = expenseStore,
            groupRepository = groupRepository,
            entityManager = entityManager,
            referenceValidator = EntityReferenceValidator(groupRepository, mock(GroupMembershipRepository::class.java))
        )

        inActiveTransaction { assertEquals(1, service.processDueOccurrences(12)) }

        assertEquals(occurrenceDate, invalid.nextOccurrenceDate)
        assertEquals(occurrenceDate.plusWeeks(1), later.nextOccurrenceDate)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `skips invalid timezone in independent transaction and continues later schedules`() {
        val scheduleRepository = mock(RecurringExpenseScheduleRepository::class.java)
        val occurrenceRepository = mock(RecurringExpenseOccurrenceRepository::class.java)
        val expenseStore = mock(ExpenseStore::class.java)
        val groupRepository = mock(GroupRepository::class.java)
        val entityManager = mock(EntityManager::class.java)
        val query = mock(TypedQuery::class.java) as TypedQuery<UUID>
        val groupId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val asOfDate = LocalDate.now(ZoneOffset.UTC).plusDays(1)
        val occurrenceDate = asOfDate.minusDays(1)
        val invalid = testSchedule(UUID.randomUUID(), groupId, occurrenceDate).apply { timezone = "Invalid/Zone" }
        val later = testSchedule(UUID.randomUUID(), groupId, occurrenceDate)

        `when`(scheduleRepository.findDueSchedules(asOfDate)).thenReturn(listOf(invalid, later))
        `when`(scheduleRepository.findForUpdate(invalid.scheduleId)).thenReturn(invalid)
        `when`(scheduleRepository.findForUpdate(later.scheduleId)).thenReturn(later)
        `when`(scheduleRepository.save(any(RecurringExpenseSchedule::class.java))).thenAnswer { it.arguments[0] }
        `when`(occurrenceRepository.existsById(any(UUID::class.java) ?: UUID(0, 0))).thenReturn(false)
        `when`(
            occurrenceRepository.existsByScheduleIdAndOccurrenceDate(
                any(UUID::class.java) ?: UUID(0, 0),
                any(LocalDate::class.java) ?: asOfDate
            )
        ).thenReturn(false)
        `when`(entityManager.createQuery(anyString(), any(Class::class.java) ?: UUID::class.java)).thenReturn(query)
        `when`(query.setParameter("groupId", groupId)).thenReturn(query)
        `when`(query.setMaxResults(101)).thenReturn(query)
        `when`(query.resultList).thenReturn(listOf(memberId))

        val service = RecurringExpenseService(
            scheduleRepository = scheduleRepository,
            occurrenceRepository = occurrenceRepository,
            expenseStore = expenseStore,
            groupRepository = groupRepository,
            entityManager = entityManager,
            transactionManager = TestTransactionManager(),
            referenceValidator = EntityReferenceValidator(groupRepository, mock(GroupMembershipRepository::class.java))
        )

        assertEquals(1, service.processDueOccurrences(12))

        assertEquals(occurrenceDate, invalid.nextOccurrenceDate)
        assertEquals(occurrenceDate.plusWeeks(1), later.nextOccurrenceDate)
        verify(scheduleRepository).findForUpdate(invalid.scheduleId)
    }

    private fun testSchedule(
        scheduleId: UUID,
        groupId: UUID,
        nextOccurrenceDate: LocalDate
    ): RecurringExpenseSchedule = RecurringExpenseSchedule(
        scheduleId = scheduleId,
        groupId = groupId,
        description = "Transaction boundary",
        amountMinor = 1200,
        currency = "EUR",
        frequency = RecurrenceFrequency.WEEKLY,
        startDate = nextOccurrenceDate,
        nextOccurrenceDate = nextOccurrenceDate,
        createdAt = Instant.EPOCH
    )

    private class TestTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()

        override fun commit(status: TransactionStatus) = Unit

        override fun rollback(status: TransactionStatus) = Unit
    }

    private fun <T> inActiveTransaction(action: () -> T): T {
        TransactionSynchronizationManager.setActualTransactionActive(true)
        return try {
            action()
        } finally {
            TransactionSynchronizationManager.clear()
        }
    }
}
