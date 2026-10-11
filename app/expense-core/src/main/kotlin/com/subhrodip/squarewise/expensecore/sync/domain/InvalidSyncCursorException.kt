package com.subhrodip.squarewise.expensecore.sync.domain

import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

/** Raised when a cursor is malformed, expired, or belongs to another group. */
class InvalidSyncCursorException(
    definition: ErrorDefinition = ExpenseErrors.SYNC_CURSOR_INVALID,
) : SquarewiseException(definition)
