package com.subhrodip.squarewise.expensecore.expenses.domain

import com.subhrodip.squarewise.expensecore.expenses.api.request.AllocationItemDto

import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors

/**
 * Allocates integer minor units without floating point arithmetic. The first
 * remainder units go to the lexicographically smallest participant IDs so a
 * retry produces the same ledger postings.
 */
object AllocationCalculator {
    fun equal(totalMinor: Long, participantIds: List<String>): Map<String, Long> {
        require(totalMinor >= 0) { "total must be non-negative" }
        require(participantIds.isNotEmpty()) { "at least one participant is required" }
        require(participantIds.distinct().size == participantIds.size) { "participant IDs must be unique" }
        val sorted = participantIds.sorted()
        val base = totalMinor / sorted.size
        val remainder = (totalMinor % sorted.size).toInt()
        return sorted.mapIndexed { index, id -> id to base + if (index < remainder) 1 else 0 }.toMap()
    }

    fun exact(totalMinor: Long, items: Map<String, Long>): Map<String, Long> {
        require(totalMinor >= 0) { "total must be non-negative" }
        require(items.isNotEmpty()) { "at least one participant is required" }
        require(items.values.all { it >= 0 }) { "all allocation values must be non-negative" }
        val sum = items.values.fold(0L, FinancialArithmetic::add)
        require(sum == totalMinor) { "exact allocations sum ($sum) must equal total ($totalMinor)" }
        return items
    }

    fun percentage(totalMinor: Long, basisPoints: Map<String, Long>): Map<String, Long> {
        require(totalMinor >= 0) { "total must be non-negative" }
        val basisPointSum = basisPoints.values.fold(0L, FinancialArithmetic::add)
        require(basisPoints.isNotEmpty() && basisPoints.values.all { it >= 0 } && basisPointSum == 10_000L) {
            "percentages must be non-negative and sum to 10000 basis points"
        }
        val exactNumerators = basisPoints.mapValues { FinancialArithmetic.multiply(totalMinor, it.value) }
        val floors = exactNumerators.mapValues { it.value / 10_000L }.toMutableMap()
        var remainder = totalMinor - floors.values.fold(0L, FinancialArithmetic::add)
        exactNumerators.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value % 10_000L }.thenBy { it.key })
            .forEach { (id, _) -> if (remainder > 0) { floors[id] = floors.getValue(id) + 1; remainder-- } }
        return floors
    }

    fun weightedShares(totalMinor: Long, shares: Map<String, Long>): Map<String, Long> {
        require(totalMinor >= 0) { "total must be non-negative" }
        require(shares.isNotEmpty() && shares.values.all { it >= 0 }) { "all shares must be non-negative" }
        val totalShares = shares.values.fold(0L, FinancialArithmetic::add)
        require(totalShares > 0) { "total shares must be greater than zero" }
        val exactNumerators = shares.mapValues { FinancialArithmetic.multiply(totalMinor, it.value) }
        val floors = exactNumerators.mapValues { it.value / totalShares }.toMutableMap()
        var remainder = totalMinor - floors.values.fold(0L, FinancialArithmetic::add)
        exactNumerators.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value % totalShares }.thenBy { it.key })
            .forEach { (id, _) -> if (remainder > 0) { floors[id] = floors.getValue(id) + 1; remainder-- } }
        return floors
    }

    /**
     * Calculates participant allocations for a given mode and total minor units.
     * Enforces strict uniqueness of participant IDs before map association to prevent
     * silent allocation loss.
     *
     * @param mode Allocation split mode (EQUAL, EXACT, PERCENT_BASIS_POINTS, WEIGHTED_SHARES)
     * @param totalMinor Total expense amount in integer minor units
     * @param items List of allocation item DTOs containing participant IDs and allocation values
     * @return Map of participant ID to allocated minor units
     */
    fun calculate(mode: String, totalMinor: Long, items: List<AllocationItemDto>): Map<String, Long> {
        val participantIds = items.map { it.participantId }
        require(participantIds.distinct().size == participantIds.size) { "participant IDs must be unique" }
        return when (mode.uppercase()) {
            "EQUAL" -> equal(totalMinor, participantIds)
            "EXACT" -> exact(totalMinor, items.associate { it.participantId to parseAllocationValue(it.value) })
            "PERCENT_BASIS_POINTS" -> percentage(totalMinor, items.associate { it.participantId to parseAllocationValue(it.value) })
            "WEIGHTED_SHARES" -> weightedShares(totalMinor, items.associate { it.participantId to parseAllocationValue(it.value) })
            else -> throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "Unsupported allocation mode: $mode")
        }
    }

    private fun parseAllocationValue(rawValue: String): Long = rawValue.toLongOrNull() ?: throw ExpenseDomainException(
        ExpenseErrors.EXPENSE_REQUEST_INVALID,
        if (rawValue.isNotEmpty() && rawValue.all(Char::isDigit)) {
            "allocation value must fit in a signed 64-bit integer"
        } else {
            "allocation value must be a valid integer"
        }
    )
}
