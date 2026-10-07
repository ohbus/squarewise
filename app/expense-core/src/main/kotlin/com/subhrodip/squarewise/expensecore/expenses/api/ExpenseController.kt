package com.subhrodip.squarewise.expensecore.expenses.api

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import jakarta.validation.constraints.Size
import com.subhrodip.squarewise.expensecore.expenses.domain.AllocationCalculator
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRequestLimits
import com.subhrodip.squarewise.expensecore.expenses.api.request.AllocationItemDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.CreateExpenseRequest
import com.subhrodip.squarewise.expensecore.expenses.api.request.UpdateExpenseRequest
import com.subhrodip.squarewise.expensecore.expenses.api.request.MoneyDto
import com.subhrodip.squarewise.expensecore.expenses.api.response.ExpenseResponse
import com.subhrodip.squarewise.expensecore.expenses.api.response.ExpenseAllocationResponse
import com.subhrodip.squarewise.expensecore.expenses.api.response.GroupBalancesResponse
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.ExpenseStore
import com.subhrodip.squarewise.expensecore.expenses.service.ExpenseValidator
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository

import jakarta.validation.Valid
import java.time.Instant
import java.security.Principal
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.validation.annotation.Validated

import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
@RestController
@Validated
@RequestMapping(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_BY_ID)
class ExpenseController(
    private val expenseStore: ExpenseStore,
    private val membershipRepository: GroupMembershipRepository,
    private val groupRepository: GroupRepository
) {

    @PostMapping(ApiEndpoints.ExpenseCore.V1.EXPENSES_SUBPATH)
    @ResponseStatus(HttpStatus.CREATED)
    fun createExpense(
        @PathVariable groupId: UUID,
        @RequestHeader(ApiEndpoints.Headers.IDEMPOTENCY_KEY)
        @Size(max = ExpenseRequestLimits.MAX_IDEMPOTENCY_KEY_LENGTH)
        idempotencyKey: String,
        @Valid @RequestBody request: CreateExpenseRequest,
        principal: Principal?
    ): ExpenseResponse {
        validateRequestBounds(request, idempotencyKey)
        ensureActiveMember(groupId, principal)
        val totalMinor = ExpenseValidator.parseAndValidateAmount(request.amount.minor)

        val payerSum = ExpenseValidator.validatePayers(request.payers, request.amount.currency)

        if (payerSum != totalMinor) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "Sum of payer amounts ($payerSum) must equal total ($totalMinor)")
        }

        val allocationMap = calculateAllocation(request.allocation.mode, totalMinor, request.allocation.items)
        val domainPayers = ExpenseValidator.mapDomainPayers(request.payers)
        val domainAllocations = ExpenseValidator.mapDomainAllocations(allocationMap)

        val record = ExpenseRecord(
            expenseId = request.expenseId,
            groupId = groupId,
            description = request.description,
            category = request.category?.takeIf { it.isNotBlank() } ?: "other",
            currency = request.amount.currency,
            amountMinor = totalMinor,
            version = 1,
            allocationMode = request.allocation.mode,
            createdAt = Instant.now(),
            payers = domainPayers,
            allocations = domainAllocations
        )

        val saved = expenseStore.create(groupId, record, idempotencyKey, principal?.name)

        return toResponse(saved)
    }

    @PutMapping(ApiEndpoints.ExpenseCore.V1.EXPENSE_BY_ID_SUBPATH)
    fun updateExpense(
        @PathVariable groupId: UUID,
        @PathVariable expenseId: UUID,
        @Valid @RequestBody request: UpdateExpenseRequest,
        principal: Principal?
    ): ExpenseResponse {
        validateRequestBounds(request)
        ensureActiveMember(groupId, principal)
        val totalMinor = ExpenseValidator.parseAndValidateAmount(request.amount.minor)

        val payerSum = ExpenseValidator.validatePayers(request.payers, request.amount.currency)

        if (payerSum != totalMinor) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "Sum of payer amounts ($payerSum) must equal total ($totalMinor)")
        }

        val allocationMap = calculateAllocation(request.allocation.mode, totalMinor, request.allocation.items)
        val domainPayers = ExpenseValidator.mapDomainPayers(request.payers)
        val domainAllocations = ExpenseValidator.mapDomainAllocations(allocationMap)

        val updateRecord = ExpenseRecord(
            expenseId = expenseId,
            groupId = groupId,
            description = request.description,
            category = request.category?.takeIf { it.isNotBlank() } ?: "other",
            currency = request.amount.currency,
            amountMinor = totalMinor,
            version = request.version,
            allocationMode = request.allocation.mode,
            createdAt = Instant.now(),
            payers = domainPayers,
            allocations = domainAllocations
        )

        val updated = expenseStore.update(groupId, expenseId, updateRecord, principal?.name)

        return toResponse(updated)
    }

    @DeleteMapping(ApiEndpoints.ExpenseCore.V1.EXPENSE_BY_ID_SUBPATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteExpense(
        @PathVariable groupId: UUID,
        @PathVariable expenseId: UUID,
        @RequestParam(required = false) version: Long?,
        principal: Principal?
    ) {
        ensureActiveMember(groupId, principal)
        expenseStore.delete(groupId, expenseId, version, principal?.name)
    }

    @GetMapping(ApiEndpoints.ExpenseCore.V1.EXPENSES_SUBPATH)
    fun listExpenses(
        @PathVariable groupId: UUID,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        principal: Principal?
    ): List<ExpenseResponse> {
        ensureActiveMember(groupId, principal)
        if (limit !in 1..100) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "limit must be between 1 and 100")
        }
        val list = expenseStore.list(groupId, category, cursor, limit)
        return list.map(::toResponse)
    }

    @GetMapping(ApiEndpoints.ExpenseCore.V1.BALANCES_SUBPATH)
    fun getBalances(@PathVariable groupId: UUID, principal: Principal?): GroupBalancesResponse {
        ensureActiveMember(groupId, principal)
        val balances = expenseStore.balances(groupId)
        return GroupBalancesResponse(groupId, balances)
    }

    private fun ensureActiveMember(groupId: UUID, principal: Principal?) {
        val subject = principal?.name?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw ExpenseDomainException(PlatformErrors.AUTHENTICATION_REQUIRED, "Authenticated subject is required")
        if (!membershipRepository.existsByGroupIdAndSubject(groupId, subject)) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Group $groupId not found")
        }
        if (groupRepository.findById(groupId).map { it.status }.orElse(null) != "ACTIVE") {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NAME_CONFLICT, "Group $groupId is archived")
        }
    }

    private fun validateRequestBounds(request: CreateExpenseRequest, idempotencyKey: String) {
        if (idempotencyKey.length > ExpenseRequestLimits.MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "Idempotency-Key is too long")
        }
        validateRequestBounds(request.category, request.payers.size, request.allocation.items.size)
    }

    private fun validateRequestBounds(request: UpdateExpenseRequest) {
        validateRequestBounds(request.category, request.payers.size, request.allocation.items.size)
    }

    private fun validateRequestBounds(category: String?, payerCount: Int, allocationCount: Int) {
        if (category != null && category.length > ExpenseRequestLimits.MAX_CATEGORY_LENGTH) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "category is too long")
        }
        if (payerCount > ExpenseRequestLimits.MAX_PARTICIPANTS || allocationCount > ExpenseRequestLimits.MAX_PARTICIPANTS) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "participant count exceeds the maximum")
        }
    }

    private fun calculateAllocation(
        mode: String,
        totalMinor: Long,
        items: List<AllocationItemDto>
    ): Map<String, Long> = try {
        AllocationCalculator.calculate(mode, totalMinor, items)
    } catch (e: IllegalArgumentException) {
        throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, e.message, e)
    }

    private fun toResponse(expense: ExpenseRecord): ExpenseResponse = ExpenseResponse(
        expenseId = expense.expenseId,
        version = expense.version,
        amount = MoneyDto(expense.currency, expense.amountMinor.toString()),
        category = expense.category,
        allocations = expense.allocations.map {
            ExpenseAllocationResponse(
                participantId = it.participantId.toString(),
                amount = MoneyDto(expense.currency, it.allocatedMinor.toString())
            )
        }
    )
}
