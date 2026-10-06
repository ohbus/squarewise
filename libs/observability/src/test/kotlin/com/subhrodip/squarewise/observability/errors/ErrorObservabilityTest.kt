package com.subhrodip.squarewise.observability.errors

import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.errors.observability.ErrorTraceSpan
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.slf4j.Logger

/** Verifies bounded logging, metrics, and trace enrichment behavior. */
class ErrorObservabilityTest {
    @Test
    fun `client error logging omits throwable while internal error includes it`() {
        val logger = mock(Logger::class.java)
        val adapter = ErrorLogger(logger)
        val cause = IllegalStateException("secret")

        adapter.logError(cause, ExpenseErrors.GROUP_NOT_FOUND, "request-1")
        verify(logger).warn(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
        adapter.logError(cause, ExpenseErrors.OUTBOX_RELAY_PUBLISH_FAILED, "request-1")
        verify(logger).error(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(cause))
    }

    @Test
    fun `metrics use only governed bounded dimensions`() {
        val registry = SimpleMeterRegistry()
        val recorder = ErrorMetricsRecorder(registry)

        recorder.record(ExpenseErrors.GROUP_NOT_FOUND)
        recorder.record(ExpenseErrors.GROUP_NOT_FOUND)

        assertEquals(2.0, registry.get("squarewise_errors_total").tag("code", "213201").counter().count())
        assertEquals(2.0, registry.get("squarewise_errors_total").tag("category", "MISSING").counter().count())
    }

    @Test
    fun `trace enrichment marks only server errors or explicit unexpected failures`() {
        val span = RecordingSpan()

        ErrorTraceEnricher.enrich(span, ExpenseErrors.GROUP_NOT_FOUND)
        assertEquals(mapOf("error.code" to "213201", "error.name" to "GROUP_NOT_FOUND", "error.type" to "MISSING"), span.attributes)
        assertFalse(span.failed)
        ErrorTraceEnricher.enrich(span, ExpenseErrors.OUTBOX_RELAY_PUBLISH_FAILED, unexpected = true)
        assertTrue(span.failed)
    }

    private class RecordingSpan : ErrorTraceSpan {
        val attributes: MutableMap<String, String> = linkedMapOf()
        var failed: Boolean = false

        override fun setAttribute(key: String, value: String) {
            attributes[key] = value
        }

        override fun markError() {
            failed = true
        }
    }
}
