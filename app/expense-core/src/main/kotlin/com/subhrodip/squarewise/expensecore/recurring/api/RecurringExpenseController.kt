package com.subhrodip.squarewise.expensecore.recurring.api

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.recurring.service.RecurringExpenseService
import com.subhrodip.squarewise.expensecore.recurring.service.toResponse
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import java.security.Principal
import java.util.UUID

/**
 * REST controller exposing authenticated recurring expense schedule management transport.
 *
 * Provides endpoints to create, list, inspect, update, pause, and resume recurring schedules
 * within expense groups.
 */
@RestController
@RequestMapping(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_BY_ID)
class RecurringExpenseController(
    private val recurringService: RecurringExpenseService,
    private val groupRepository: GroupRepository,
    private val membershipRepository: GroupMembershipRepository
) {

    @PostMapping(ApiEndpoints.ExpenseCore.V1.SCHEDULES_SUBPATH)
    @ResponseStatus(HttpStatus.CREATED)
    fun createSchedule(
        @PathVariable groupId: UUID,
        @Valid @RequestBody request: CreateRecurringScheduleRequestDto,
        principal: Principal?
    ): RecurringScheduleResponse {
        verifyGroupAndMembership(groupId, principal)
        val amountMinor = parseAmount(request.amount.minor)

        val domainPayers = request.payers?.map {
            ExpensePayer(
                participantId = UUID.fromString(it.participantId),
                amountMinor = parseAmount(it.amount.minor)
            )
        }
        val domainAllocations = request.allocations?.map {
            ExpenseAllocation(
                participantId = UUID.fromString(it.participantId),
                allocatedMinor = parseAmount(it.amount.minor)
            )
        }

        val domainRequest = CreateRecurringScheduleRequest(
            description = request.description,
            amountMinor = amountMinor,
            currency = request.amount.currency,
            frequency = request.frequency,
            dayOfMonth = request.dayOfMonth,
            startDate = request.startDate,
            endDate = request.endDate,
            payers = domainPayers,
            allocations = domainAllocations
        )

        return recurringService.createSchedule(groupId, domainRequest).toResponse()
    }

    @GetMapping(ApiEndpoints.ExpenseCore.V1.SCHEDULES_SUBPATH)
    fun listSchedules(
        @PathVariable groupId: UUID,
        principal: Principal?
    ): List<RecurringScheduleResponse> {
        verifyGroupAndMembership(groupId, principal)
        return recurringService.listSchedules(groupId).map { it.toResponse() }
    }

    @GetMapping(ApiEndpoints.ExpenseCore.V1.SCHEDULE_BY_ID_SUBPATH)
    fun getSchedule(
        @PathVariable groupId: UUID,
        @PathVariable scheduleId: UUID,
        principal: Principal?
    ): RecurringScheduleResponse {
        verifyGroupAndMembership(groupId, principal)
        val schedule = recurringService.getSchedule(scheduleId)
            ?: throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Schedule $scheduleId not found")
        if (schedule.groupId != groupId) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Schedule $scheduleId not found in group $groupId")
        }
        return schedule.toResponse()
    }

    @PutMapping(ApiEndpoints.ExpenseCore.V1.SCHEDULE_BY_ID_SUBPATH)
    fun updateSchedule(
        @PathVariable groupId: UUID,
        @PathVariable scheduleId: UUID,
        @Valid @RequestBody request: UpdateRecurringScheduleRequestDto,
        principal: Principal?
    ): RecurringScheduleResponse {
        verifyGroupAndMembership(groupId, principal)
        val amountMinor = parseAmount(request.amount.minor)

        val domainPayers = request.payers?.map {
            ExpensePayer(
                participantId = UUID.fromString(it.participantId),
                amountMinor = parseAmount(it.amount.minor)
            )
        }
        val domainAllocations = request.allocations?.map {
            ExpenseAllocation(
                participantId = UUID.fromString(it.participantId),
                allocatedMinor = parseAmount(it.amount.minor)
            )
        }

        val domainRequest = UpdateRecurringScheduleRequest(
            description = request.description,
            amountMinor = amountMinor,
            currency = request.amount.currency,
            frequency = request.frequency,
            dayOfMonth = request.dayOfMonth,
            startDate = request.startDate,
            endDate = request.endDate,
            payers = domainPayers,
            allocations = domainAllocations
        )

        return recurringService.updateSchedule(groupId, scheduleId, domainRequest).toResponse()
    }

    @PostMapping(ApiEndpoints.ExpenseCore.V1.SCHEDULE_PAUSE_SUBPATH)
    fun pauseSchedule(
        @PathVariable groupId: UUID,
        @PathVariable scheduleId: UUID,
        principal: Principal?
    ): RecurringScheduleResponse {
        verifyGroupAndMembership(groupId, principal)
        val schedule = recurringService.getSchedule(scheduleId)
            ?: throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Schedule $scheduleId not found")
        if (schedule.groupId != groupId) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Schedule $scheduleId not found in group $groupId")
        }
        return recurringService.pauseSchedule(scheduleId).toResponse()
    }

    @PostMapping(ApiEndpoints.ExpenseCore.V1.SCHEDULE_RESUME_SUBPATH)
    fun resumeSchedule(
        @PathVariable groupId: UUID,
        @PathVariable scheduleId: UUID,
        principal: Principal?
    ): RecurringScheduleResponse {
        verifyGroupAndMembership(groupId, principal)
        val schedule = recurringService.getSchedule(scheduleId)
            ?: throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Schedule $scheduleId not found")
        if (schedule.groupId != groupId) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Schedule $scheduleId not found in group $groupId")
        }
        return recurringService.resumeSchedule(scheduleId).toResponse()
    }

    private fun verifyGroupAndMembership(groupId: UUID, principal: Principal?) {
        if (!groupRepository.existsById(groupId)) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Group $groupId not found")
        }
        val subject = principal?.name?.takeIf { it.isNotBlank() }
            ?: throw ExpenseDomainException(PlatformErrors.AUTHENTICATION_REQUIRED, "Authenticated subject is required")
        if (!membershipRepository.existsByGroupIdAndSubject(groupId, subject)) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Group $groupId not found")
        }
    }

    private fun parseAmount(minorStr: String): Long {
        val minor = minorStr.toLongOrNull()
            ?: throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "amount.minor must be a valid integer")
        if (minor <= 0) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "amount.minor must be positive")
        }
        return minor
    }
}
