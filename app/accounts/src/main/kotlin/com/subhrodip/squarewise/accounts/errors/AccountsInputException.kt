package com.subhrodip.squarewise.accounts.errors

/**
 * Typed invalid-input failure for internal Accounts configuration and crypto boundaries.
 *
 * It remains an [IllegalArgumentException] for callers that rely on the low-level
 * validation contract while avoiding anonymous generic throw sites.
 */
class AccountsInputException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
