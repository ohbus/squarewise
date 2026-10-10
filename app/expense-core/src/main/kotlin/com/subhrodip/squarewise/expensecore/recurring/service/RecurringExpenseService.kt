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
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSpecification
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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import com.subhrodip.squarewise.errors.exceptions.FatalErrorClassifier
import org.springframework.amqp.AmqpException
import org.springframework.dao.DataAccessException
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper

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
    private val outboxStore: OutboxStore? = null,
    private val objectMapper: ObjectMapper = ObjectMapper()
) : RecurringCommandStore, RecurringQueryStore {
    private val log = LoggerFactory.getLogger(RecurringExpenseService::class.java)

    @Transactional
    override fun createSchedule(groupId: UUID, request: CreateRecurringScheduleRequest): RecurringExpenseSchedule {
        validateSchedule(request)

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

        schedule.customSpecification = encodeSpecification(request.payers, request.allocations)

        return scheduleRepository.save(schedule)
    }

    @Transactional
    override fun updateSchedule(
        groupId: UUID,
        scheduleId: UUID,
        request: UpdateRecurringScheduleRequest
    ): RecurringExpenseSchedule {
        val schedule = scheduleRepository.findById(scheduleId).orElseThrow {
            ExpenseDomainException(ExpenseErrors.SCHEDULE_NOT_FOUND, "Schedule $scheduleId not found")
        }
        if (schedule.groupId != groupId) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Schedule $scheduleId not in group $groupId")
        }

        validateSchedule(request)

        schedule.description = request.description.trim()
        schedule.amountMinor = request.amountMinor
        schedule.currency = request.currency.uppercase()
        schedule.frequency = request.frequency
        schedule.dayOfMonth = request.dayOfMonth
        schedule.startDate = request.startDate
        schedule.endDate = request.endDate

        schedule.customSpecification = encodeSpecification(request.payers, request.allocations)

        return scheduleRepository.save(schedule)
    }

    @Transactional
    override fun pauseSchedule(scheduleId: UUID): RecurringExpenseSchedule {
        val schedule = scheduleRepository.findById(scheduleId).orElseThrow {
            ExpenseDomainException(ExpenseErrors.SCHEDULE_NOT_FOUND, "Schedule $scheduleId not found")
        }
        schedule.paused = true
        return scheduleRepository.save(schedule)
    }

    @Transactional
    override fun resumeSchedule(scheduleId: UUID): RecurringExpenseSchedule {
        val schedule = scheduleRepository.findById(scheduleId).orElseThrow {
            ExpenseDomainException(ExpenseErrors.SCHEDULE_NOT_FOUND, "Schedule $scheduleId not found")
        }
        schedule.paused = false
        return scheduleRepository.save(schedule)
    }

    @Transactional
    fun processDueOccurrences(): Int = processDueOccurrences(LocalDate.now(), 12)

    @Transactional
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
                    if (e is CancellationException || FatalErrorClassifier.isFatal(e)) throw e
                    if (e is DataAccessException || e is AmqpException || e is TimeoutException) {
                        log.warn(
                            "Transient recurring expense generation failure; retrying scheduleId={} occurrenceId={} errorType={}",
                            schedule.scheduleId,
                            occurrenceId,
                            e::class.simpleName
                        )
                        break
                    }
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
        val custom = readSpecification(schedule) ?: return true
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
        val custom = readSpecification(schedule)
        val (payers, allocations) = if (custom != null && (custom.payers.isNotEmpty() || custom.allocations.isNotEmpty())) {
            val customPayers = custom.payers.ifEmpty {
                val payerId = members.firstOrNull() ?: schedule.groupId
                listOf(ExpensePayer(payerId, schedule.amountMinor))
            }
            val customAllocations = custom.allocations.ifEmpty {
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

    private fun encodeSpecification(
        payers: List<ExpensePayer>?,
        allocations: List<ExpenseAllocation>?
    ): String? = if (payers == null && allocations == null) {
        null
    } else {
        objectMapper.writeValueAsString(
            RecurringExpenseSpecification(
                payers = payers ?: emptyList(),
                allocations = allocations ?: emptyList()
            )
        )
    }

    private fun readSpecification(schedule: RecurringExpenseSchedule): RecurringExpenseSpecification? =
        schedule.customSpecification?.let { encoded ->
            try {
                objectMapper.readValue(encoded, RecurringExpenseSpecification::class.java)
            } catch (error: Exception) {
                throw ExpenseDomainException(
                    ExpenseErrors.EXPENSE_REQUEST_INVALID,
                    "Recurring expense specification is malformed",
                    error
                )
            }
        }

    private fun splitEqually(amountMinor: Long, participantIds: List<UUID>): List<ExpenseAllocation> {
        val equalMap = AllocationCalculator.equal(amountMinor, participantIds.map { it.toString() })
        return equalMap.map { (pidStr, minor) ->
            ExpenseAllocation(UUID.fromString(pidStr), minor)
        }
    }

    private fun getGroupMembers(groupId: UUID): List<UUID> {
        return entityManager.createQuery(
            "SELECT m.membershipId FROM GroupMembershipEntity m WHERE m.groupId = :groupId AND m.status = 'ACTIVE' ORDER BY m.membershipId ASC",
            UUID::class.java
        ).setParameter("groupId", groupId).resultList
    }

    private fun validateSchedule(request: CreateRecurringScheduleRequest) {
        validateScheduleValues(
            description = request.description,
            amountMinor = request.amountMinor,
            currency = request.currency,
            frequency = request.frequency,
            dayOfMonth = request.dayOfMonth,
            startDate = request.startDate,
            endDate = request.endDate
        )
        validateCustomSpecifications(request.amountMinor, request.payers, request.allocations)
    }

    private fun validateSchedule(request: UpdateRecurringScheduleRequest) {
        validateScheduleValues(
            description = request.description,
            amountMinor = request.amountMinor,
            currency = request.currency,
            frequency = request.frequency,
            dayOfMonth = request.dayOfMonth,
            startDate = request.startDate,
            endDate = request.endDate
        )
        validateCustomSpecifications(request.amountMinor, request.payers, request.allocations)
    }

    private fun validateScheduleValues(
        description: String,
        amountMinor: Long,
        currency: String,
        frequency: RecurrenceFrequency,
        dayOfMonth: Int?,
        startDate: LocalDate,
        endDate: LocalDate?
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
    }

    private fun validateCustomSpecifications(
        amountMinor: Long,
        payers: List<ExpensePayer>?,
        allocations: List<ExpenseAllocation>?
    ) {
        if (payers != null) {
            require(payers.isNotEmpty()) { "payers must not be empty if provided" }
            require(payers.all { it.amountMinor > 0 }) { "all payer amounts must be positive" }
            require(payers.sumOf { it.amountMinor } == amountMinor) {
                "sum of payer amounts must equal schedule amount"
            }
        }
        if (allocations != null) {
            require(allocations.isNotEmpty()) { "allocations must not be empty if provided" }
            require(allocations.all { it.allocatedMinor > 0 }) { "all allocation amounts must be positive" }
            require(allocations.sumOf { it.allocatedMinor } == amountMinor) {
                "sum of allocation amounts must equal schedule amount"
            }
        }
    }
}

