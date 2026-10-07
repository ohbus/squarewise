package com.subhrodip.squarewise.errors.code

/** Registered bounded contexts represented by the first error-code digit. */
enum class ErrorDomain(val digit: Int) {
    /** Accounts and identity context. */
    ACCOUNTS(1),

    /** Expense Core financial context. */
    EXPENSE_CORE(2),

    /** Notifications and delivery context. */
    NOTIFICATIONS(3),

    /** GraphQL BFF context. */
    BFF(4),

    /** Shared platform context. */
    PLATFORM(9),
}
