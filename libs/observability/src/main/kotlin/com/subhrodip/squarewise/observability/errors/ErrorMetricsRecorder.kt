package com.subhrodip.squarewise.observability.errors

import com.subhrodip.squarewise.errors.code.ErrorCategory
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.code.ErrorDomain
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import java.util.concurrent.ConcurrentHashMap

/** Records governed error counters with finite catalog-derived dimensions only. */
class ErrorMetricsRecorder(private val registry: MeterRegistry) {
    private val observedDimensions: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Increment the bounded error counter for one catalog definition. */
    fun record(definition: ErrorDefinition) {
        val domain = ErrorDomain.entries.single { it.digit == definition.numericCode.domainDigit }
        val category = ErrorCategory.entries.single { it.digit == definition.numericCode.categoryDigit }
        val tags = Tags.of(
            "domain", domain.name,
            "category", category.name,
            "code", definition.numericCode.value,
            "status", (definition.httpStatus?.toString() ?: "none"),
            "error_name", definition.errorName,
        )
        val dimensionKey = listOf(
            domain.name,
            category.name,
            definition.numericCode.value,
            definition.httpStatus?.toString() ?: "none",
            definition.errorName,
        ).joinToString("|")
        if (observedDimensions.size >= MAX_DIMENSIONS && !observedDimensions.contains(dimensionKey)) {
            throw ObservabilityPlatformException(
                PlatformErrors.OBSERVABILITY_PIPELINE_FAILED,
                "error metric dimension budget exceeded"
            )
        }
        observedDimensions.add(dimensionKey)
        registry.counter(METRIC_NAME, tags).increment()
    }

    private companion object {
        const val METRIC_NAME: String = "squarewise_errors_total"
        const val MAX_DIMENSIONS: Int = 1_000
    }
}
