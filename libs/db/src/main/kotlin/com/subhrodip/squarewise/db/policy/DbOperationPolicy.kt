package com.subhrodip.squarewise.db.policy

import com.subhrodip.squarewise.db.errors.DbPlatformException
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.DbRoute
import com.subhrodip.squarewise.db.routing.ReadConsistency

/** Immutable route policy for one named database operation. */
data class DbOperationPolicy(
    /** Stable low-cardinality operation name used in telemetry. */
    val operationName: String,
    /** Operation safety classification. */
    val kind: DbOperationKind,
    /** Required consistency guarantee. */
    val consistency: ReadConsistency = ReadConsistency.STRONG,
    /** Whether a healthy reader may serve this operation. */
    val readerEligible: Boolean = false,
) {
    init {
        if (!operationName.matches(OPERATION_NAME)) {
            invalid("operationName must be a stable lowercase dot-delimited identifier")
        }
        if (readerEligible && kind != DbOperationKind.QUERY) {
            invalid("Only non-mutating queries may be reader eligible")
        }
        if (readerEligible && consistency == ReadConsistency.STRONG) {
            invalid("Strong queries must use the writer")
        }
        if (kind == DbOperationKind.COMMAND && readerEligible) {
            invalid("Commands cannot use a reader")
        }
    }

    private fun invalid(detail: String): Nothing = throw DbPlatformException(
        PlatformErrors.PLATFORM_CONFIGURATION_INVALID,
        detail
    )

    /** Returns the only valid route for a writer-only operation. */
    fun defaultRoute(): DbRoute = if (readerEligible) DbRoute.READER else DbRoute.WRITER

    private companion object {
        val OPERATION_NAME = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
    }
}
