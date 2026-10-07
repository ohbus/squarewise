package com.subhrodip.squarewise.expensecore.expenses.api
import com.subhrodip.squarewise.expensecore.expenses.domain.AllocationCalculator
import com.subhrodip.squarewise.expensecore.expenses.api.request.AllocationPreviewRequest
import com.subhrodip.squarewise.expensecore.expenses.api.response.AllocationPreviewResponse

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import jakarta.validation.Valid

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints

@RestController
@RequestMapping(ApiEndpoints.ExpenseCore.V1.BASE + ApiEndpoints.ExpenseCore.V1.ALLOCATIONS)
class AllocationPreviewController {
    @PostMapping(ApiEndpoints.ExpenseCore.V1.ALLOCATION_PREVIEW_SUBPATH)
    @ResponseStatus(HttpStatus.OK)
    fun preview(@Valid @RequestBody request: AllocationPreviewRequest): AllocationPreviewResponse {
        val total = request.totalMinor.toLongOrNull()
            ?: throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "totalMinor must be a non-negative integer")
        if (total < 0) throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "totalMinor must be non-negative")
        val allocations = try {
            AllocationCalculator.equal(total, request.participantIds)
        } catch (error: IllegalArgumentException) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, error.message, error)
        }
            .mapValues { (_, value) -> value.toString() }
        return AllocationPreviewResponse(request.totalMinor, allocations)
    }
}
