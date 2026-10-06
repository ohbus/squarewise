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
}
