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

    /** Immutable identity set for O(1) zero-reflection exception validation. */
    private val definitionSet: Set<ErrorDefinition> = java.util.Collections.newSetFromMap(
        java.util.IdentityHashMap<ErrorDefinition, Boolean>(all.size)
    ).apply { addAll(all) }

    /** Checks in O(1) whether an ErrorDefinition belongs to the compiled catalog. */
    fun contains(definition: ErrorDefinition): Boolean = definitionSet.contains(definition)

    /** Finds a compiled definition by its canonical six-digit identity. */
    fun find(numericCode: String): ErrorDefinition? = byNumericCode[numericCode]
}
