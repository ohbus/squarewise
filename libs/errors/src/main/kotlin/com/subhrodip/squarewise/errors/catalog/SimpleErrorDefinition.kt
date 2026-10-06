package com.subhrodip.squarewise.errors.catalog

import com.subhrodip.squarewise.errors.code.DisclosurePolicy
import com.subhrodip.squarewise.errors.code.ErrorCode
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.code.ErrorSeverity
import com.subhrodip.squarewise.errors.code.RetryPolicy

/**
 * Immutable compiled representation of one authoritative catalog record.
 *
 * The object contains only constructor values, so production lookup does not
 * parse YAML, scan classes, or use reflection.
 */
data class SimpleErrorDefinition(
    override val numericCode: ErrorCode,
    override val errorName: String,
    override val legacyCode: String?,
    override val title: String,
    override val safeDetail: String,
    override val messageKey: String,
    override val httpStatus: Int?,
    override val graphqlClassification: String?,
    override val retryPolicy: RetryPolicy,
    override val severity: ErrorSeverity,
    override val disclosure: DisclosurePolicy,
) : ErrorDefinition
