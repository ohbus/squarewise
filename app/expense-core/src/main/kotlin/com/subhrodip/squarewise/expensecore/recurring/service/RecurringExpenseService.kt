package com.subhrodip.squarewise.expensecore.recurring.service

import com.subhrodip.squarewise.expensecore.expenses.domain.AllocationCalculator
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.ExpenseStore
import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore
import com.subhrodip.squarewise.expensecore.recurring.api.CreateRecurringScheduleRequest
import com.subhrodip.squarewise.expensecore.recurring.api.UpdateRecurringScheduleRequest
import com.subhrodip.squarewise.expensecore.recurring.domain.OccurrenceIdentity
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrenceFrequency
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrencePolicy
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseOccurrence
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule
import com.subhrodip.squarewise.expensecore.recurring.persistence.RecurringExpenseOccurrenceRepository
import com.subhrodip.squarewise.expensecore.recurring.persistence.RecurringExpenseScheduleRepository
import com.subhrodip.squarewise.ids.generation.UuidGenerator
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service managing database-backed recurring expense schedule lifecycle operations,
 * due occurrence generation, bounded worker catch-up execution, and pause notification outbox events.
 */
@Service
class RecurringExpenseService(
    private val scheduleRepository: RecurringExpenseScheduleRepository,
    private val occurrenceRepository: RecurringExpenseOccurrenceRepository,
    private val expenseStore: ExpenseStore,
    private val groupRepository: GroupRepository,
    @PersistenceContext private val entityManager: EntityManager,
    @Autowired(required = false)
    private val outboxStore: OutboxStore? = null
) : RecurringCommandStore, RecurringQueryStore {
    private val customSpecifications = ConcurrentHashMap<UUID, Pair<List<ExpensePayer>, List<ExpenseAllocation>>>()

    @Transactional
    override fun createSchedule(groupId: UUID, request: CreateRecurringScheduleRequest): RecurringExpenseSchedule {
        validateScheduleParameters(
            description = request.description,
            amountMinor = request.amountMinor,
            currency = request.currency,
            dayOfMonth = request.dayOfMonth,
            frequency = request.frequency,
            startDate = request.startDate,
            endDate = request.endDate,
            payers = request.payers,
            allocations = request.allocations
        )

        if (!groupRepository.existsById(groupId)) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Group $groupId not found")
        }

        val scheduleId = request.scheduleId ?: UuidGenerator.next()
        val schedule = RecurringExpenseSchedule(
            scheduleId = scheduleId,
            groupId = groupId,
            description = request.description.trim(),
            amountMinor = request.amountMinor,
            currency = request.currency.uppercase(),
            frequency = request.frequency,
            dayOfMonth = request.dayOfMonth,
            startDate = request.startDate,
            endDate = request.endDate,
            nextOccurrenceDate = request.startDate,
            paused = false,
            createdAt = Instant.now(),
            version = 1
        )

        if (request.payers != null || request.allocations != null) {
            customSpecifications[scheduleId] = (request.payers ?: emptyList()) to (request.allocations ?: emptyList())
        }

        return scheduleRepository.save(schedule)
    }

    @Transactional
    override fun updateSchedule(
        groupId: UUID,
        scheduleId: UUID,
        request: UpdateRecurringScheduleRequest
    ): RecurringExpenseSchedule {
        val schedule = scheduleRepository.findById(scheduleId).orElseThrow {
            ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Schedule $scheduleId not found")
        }
        if (schedule.groupId != groupId) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Schedule $scheduleId not in group $groupId")
        }

        validateScheduleParameters(
            description = request.description,
            amountMinor = request.amountMinor,
            currency = request.currency,
            dayOfMonth = request.dayOfMonth,
            frequency = request.frequency,
            startDate = request.startDate,
            endDate = request.endDate,
            payers = request.payers,
            allocations = request.allocations
        )

        schedule.description = request.description.trim()
        schedule.amountMinor = request.amountMinor
        schedule.currency = request.currency.uppercase()
        schedule.frequency = request.frequency
        schedule.dayOfMonth = request.dayOfMonth
        schedule.startDate = request.startDate
        schedule.endDate = request.endDate

        if (request.payers != null || request.allocations != null) {
            customSpecifications[scheduleId] = (request.payers ?: emptyList()) to (request.allocations ?: emptyList())
        } else {
            customSpecifications.remove(scheduleId)
        }

        return scheduleRepository.save(schedule)
    }

    @Transactional
    override fun pauseSchedule(scheduleId: UUID): RecurringExpenseSchedule {
        val schedule = scheduleRepository.findById(scheduleId).orElseThrow {
            ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Schedule $scheduleId not found")
        }
        schedule.paused = true
        return scheduleRepository.save(schedule)
    }

    @Transactional
    override fun resumeSchedule(scheduleId: UUID): RecurringExpenseSchedule {
        val schedule = scheduleRepository.findById(scheduleId).orElseThrow {
            ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Schedule $scheduleId not found")
        }
        schedule.paused = false
        return scheduleRepository.save(schedule)
    }

    @Transactional
    fun processDueOccurrences(): Int = processDueOccurrences(LocalDate.now(), 12)

    override fun processDueOccurrences(
        asOfDate: LocalDate,
        maxCatchUpOccurrences: Int
    ): Int {
        val dueSchedules = scheduleRepository.findDueSchedules(asOfDate)
        var count = 0
        for (schedule in dueSchedules) {
            count += processScheduleOccurrences(schedule, asOfDate, maxCatchUpOccurrences)
        }
        return count
    }

    @Transactional(readOnly = true)
    override fun getSchedule(scheduleId: UUID): RecurringExpenseSchedule? =
        scheduleRepository.findById(scheduleId).orElse(null)

    @Transactional(readOnly = true)
    override fun listSchedules(groupId: UUID): List<RecurringExpenseSchedule> =
        scheduleRepository.findByGroupId(groupId)

    @Transactional(readOnly = true)
    override fun getOccurrences(scheduleId: UUID): List<RecurringExpenseOccurrence> =
        occurrenceRepository.findByScheduleId(scheduleId)

    private fun processScheduleOccurrences(
        schedule: RecurringExpenseSchedule,
        asOfDate: LocalDate,
        maxCatchUpOccurrences: Int
    ): Int {
        var generated = 0
        while (!schedule.paused && !schedule.nextOccurrenceDate.isAfter(asOfDate) && generated < maxCatchUpOccurrences) {
            if (schedule.endDate != null && schedule.nextOccurrenceDate.isAfter(schedule.endDate)) {
                break
            }

            val members = getGroupMembers(schedule.groupId)
            if (members.isEmpty() || !validateMembership(schedule, members)) {
                schedule.paused = true
                scheduleRepository.save(schedule)
                emitSchedulePausedNotification(schedule, "invalid_membership")
                break
            }

            val occurrenceDate = schedule.nextOccurrenceDate
            val occurrenceId = OccurrenceIdentity.id(schedule.scheduleId, occurrenceDate)

            val alreadyExists = occurrenceRepository.existsById(occurrenceId) ||
                occurrenceRepository.existsByScheduleIdAndOccurrenceDate(schedule.scheduleId, occurrenceDate)

            if (!alreadyExists) {
                try {
                    val expenseRecord = buildExpenseRecord(schedule, occurrenceId, members)
                    expenseStore.create(schedule.groupId, expenseRecord, occurrenceId.toString())

                    val occurrence = RecurringExpenseOccurrence(
                        occurrenceId = occurrenceId,
                        scheduleId = schedule.scheduleId,
                        occurrenceDate = occurrenceDate,
                        expenseId = expenseRecord.expenseId,
                        createdAt = Instant.now()
                    )
                    occurrenceRepository.save(occurrence)
                    generated++
                } catch (e: Exception) {
                    schedule.paused = true
                    scheduleRepository.save(schedule)
                    emitSchedulePausedNotification(schedule, "generation_error")
                    break
                }
            }

            val nextDate = RecurrencePolicy.nextAfter(occurrenceDate, schedule)
            schedule.nextOccurrenceDate = nextDate
            scheduleRepository.save(schedule)
        }
        return generated
    }

    private fun validateMembership(schedule: RecurringExpenseSchedule, members: List<UUID>): Boolean {
        if (members.isEmpty()) return false
        val memberSet = members.toSet()
        val custom = customSpecifications[schedule.scheduleId] ?: return true
        val (payers, allocations) = custom
        if (payers.isNotEmpty() && payers.any { it.participantId !in memberSet }) {
            return false
        }
        if (allocations.isNotEmpty() && allocations.any { it.participantId !in memberSet }) {
            return false
        }
        return true
    }

    private fun emitSchedulePausedNotification(schedule: RecurringExpenseSchedule, reason: String) {
        val outbox = outboxStore ?: return
        val eventId = UuidGenerator.next()
        val message = "Recurring schedule '${schedule.description}' in group ${schedule.groupId} paused due to $reason."
        val payload = mapOf<String, Any?>(
            "scheduleId" to schedule.scheduleId.toString(),
            "groupId" to schedule.groupId.toString(),
            "reason" to reason,
            "subject" to schedule.groupId.toString(),
            "message" to message,
            "description" to message
        )
        outbox.append(
            OutboxMessage(
                eventId = eventId,
                eventType = "recurring.schedule.paused",
                aggregateId = schedule.scheduleId,
                groupId = schedule.groupId,
                groupRevision = 1L,
                occurredAt = Instant.now(),
                payload = payload
            )
        )
    }

    private fun buildExpenseRecord(
        schedule: RecurringExpenseSchedule,
        occurrenceId: UUID,
        members: List<UUID>
    ): ExpenseRecord {
        val custom = customSpecifications[schedule.scheduleId]
        val (payers, allocations) = if (custom != null && (custom.first.isNotEmpty() || custom.second.isNotEmpty())) {
            val customPayers = custom.first.ifEmpty {
                val payerId = members.firstOrNull() ?: schedule.groupId
                listOf(ExpensePayer(payerId, schedule.amountMinor))
            }
            val customAllocations = custom.second.ifEmpty {
                splitEqually(schedule.amountMinor, members.ifEmpty { listOf(schedule.groupId) })
            }
            customPayers to customAllocations
        } else {
            val participantIds = members.ifEmpty { listOf(schedule.groupId) }
            val payers = listOf(ExpensePayer(participantIds.first(), schedule.amountMinor))
            val allocations = splitEqually(schedule.amountMinor, participantIds)
            payers to allocations
        }

        return ExpenseRecord(
            expenseId = occurrenceId,
            groupId = schedule.groupId,
            description = schedule.description,
            category = "recurring",
            currency = schedule.currency,
            amountMinor = schedule.amountMinor,
            version = 1,
            allocationMode = "EQUAL",
            createdAt = Instant.now(),
            payers = payers,
            allocations = allocations
        )
    }

    private fun splitEqually(amountMinor: Long, participantIds: List<UUID>): List<ExpenseAllocation> {
        val equalMap = AllocationCalculator.equal(amountMinor, participantIds.map { it.toString() })
        return equalMap.map { (pidStr, minor) ->
            ExpenseAllocation(UUID.fromString(pidStr), minor)
        }
    }

    private fun getGroupMembers(groupId: UUID): List<UUID> {
        val rows = entityManager.createQuery(
            "SELECT m.subject, m.membershipId FROM GroupMembershipEntity m WHERE m.groupId = :groupId ORDER BY m.membershipId ASC",
            Array<Any>::class.java
        ).setParameter("groupId", groupId).resultList

        return rows.map { row ->
            val subject = row[0] as String
            try {
                UUID.fromString(subject)
            } catch (_: IllegalArgumentException) {
                UUID.nameUUIDFromBytes(subject.toByteArray(StandardCharsets.UTF_8))
            }
        }
    }

    private fun validateScheduleParameters(
        description: String,
        amountMinor: Long,
        currency: String,
        dayOfMonth: Int?,
        frequency: RecurrenceFrequency,
        startDate: LocalDate,
        endDate: LocalDate?,
        payers: List<ExpensePayer>?,
        allocations: List<ExpenseAllocation>?
    ) {
        require(description.isNotBlank()) { "description must not be blank" }
        require(amountMinor > 0) { "amountMinor must be positive" }
        require(currency.matches(Regex("^[A-Z]{3}$"))) { "currency must be 3 uppercase letters" }
        require(dayOfMonth == null || dayOfMonth in 1..31) {
            "dayOfMonth must be between 1 and 31"
        }
        require(frequency == RecurrenceFrequency.MONTHLY || dayOfMonth == null) {
            "dayOfMonth is only supported for monthly schedules"
        }
        if (endDate != null) {
            require(!endDate.isBefore(startDate)) {
                "endDate must not be before startDate"
            }
        }
        if (payers != null) {
            require(payers.isNotEmpty()) { "payers must not be empty if provided" }
            require(payers.sumOf { it.amountMinor } == amountMinor) {
                "sum of payer amounts must equal schedule amount"
            }
        }
        if (allocations != null) {
            require(allocations.isNotEmpty()) { "allocations must not be empty if provided" }
            require(allocations.sumOf { it.allocatedMinor } == amountMinor) {
                "sum of allocation amounts must equal schedule amount"
            }
        }
    }
}

