package com.subhrodip.squarewise.expensecore.categories
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
enum class ExpenseCategory(val key: String, val label: String) {
    FOOD("food", "Food"),
    LODGING("lodging", "Lodging"),
    TRANSPORT("transport", "Transport"),
    ENTERTAINMENT("entertainment", "Entertainment"),
    SHOPPING("shopping", "Shopping"),
    BILLS("bills", "Bills"),
    HEALTH("health", "Health"),
    OTHER("other", "Other");

    companion object {
        fun fromKey(key: String): ExpenseCategory = entries.firstOrNull { it.key == key.trim().lowercase() }
            ?: throw ExpenseDomainException(ExpenseErrors.ERR_02, "Unknown expense category")
    }
}
