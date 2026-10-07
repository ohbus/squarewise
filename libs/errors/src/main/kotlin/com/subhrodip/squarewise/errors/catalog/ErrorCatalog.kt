package com.subhrodip.squarewise.errors.catalog

import com.subhrodip.squarewise.errors.code.ErrorDefinition

/** Immutable aggregate view of every statically compiled domain catalog. */
object ErrorCatalog {
    /** All definitions in the authoritative YAML catalog order. */
    val all: List<ErrorDefinition> = buildList {
        addAll(AccountsErrors.all)
        addAll(ExpenseErrors.all)
        addAll(NotificationErrors.all)
        addAll(BffErrors.all)
        addAll(PlatformErrors.all)
    }

    /** Immutable startup-built index for allocation-free numeric-code resolution. */
    val byNumericCode: Map<String, ErrorDefinition> = all.associateBy { it.numericCode.value }

    /** Finds a compiled definition by its canonical six-digit identity. */
    fun find(numericCode: String): ErrorDefinition? = byNumericCode[numericCode]
}
