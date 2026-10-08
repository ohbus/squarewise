package com.subhrodip.squarewise.observability.errors

import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.catalog.SimpleErrorDefinition
import com.subhrodip.squarewise.errors.code.DisclosurePolicy
import com.subhrodip.squarewise.errors.code.ErrorCode
import com.subhrodip.squarewise.errors.code.ErrorSeverity
import com.subhrodip.squarewise.errors.code.RetryPolicy
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import com.subhrodip.squarewise.errors.observability.ErrorTraceSpan
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
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
        verify(logger).warn(anyString(), any(), any(), any())
        adapter.logError(cause, ExpenseErrors.OUTBOX_RELAY_PUBLISH_FAILED, "request-1")
        verify(logger).error(anyString(), any(), any(), any(), eq(cause))
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

    @Test
    fun `default ErrorLogger constructor creates instance and logs INFO severity`() {
        val adapter = ErrorLogger()
        // verify calling logError with an INFO definition works without error
        adapter.logError(RuntimeException("info"), PlatformErrors.RESOURCE_NOT_FOUND, "req-info")
    }

    @Test
    fun `ErrorLogger logs INFO severity to mock logger`() {
        val logger = mock(Logger::class.java)
        val adapter = ErrorLogger(logger)
        val infoDefinition = SimpleErrorDefinition(
            numericCode = ErrorCode("111101"),
            errorName = "INFO_ERROR",
            title = "Info Error",
            safeDetail = "Info safe detail",
            messageKey = "info.error",
            httpStatus = 200,
            graphqlClassification = null,
            retryPolicy = RetryPolicy.NEVER,
            severity = ErrorSeverity.INFO,
            disclosure = DisclosurePolicy.PUBLIC,
        )
        adapter.logError(RuntimeException("test"), infoDefinition, "req-1")
        verify(logger).info(anyString(), any(), any(), any())
    }

    @Test
    fun `ErrorMetricsRecorder handles null httpStatus`() {
        val registry = SimpleMeterRegistry()
        val recorder = ErrorMetricsRecorder(registry)
        recorder.record(PlatformErrors.BROKER_UNAVAILABLE)
        assertEquals(1.0, registry.get("squarewise_errors_total").tag("status", "none").counter().count())
    }

    @Test
    fun `ErrorMetricsRecorder throws ObservabilityPlatformException when dimension budget exceeded`() {
        val registry = SimpleMeterRegistry()
        val recorder = ErrorMetricsRecorder(registry)

        val ex = assertThrows<ObservabilityPlatformException> {
            for (i in 1..1001) {
                val seq = String.format("%02d", (i % 99) + 1)
                val def = SimpleErrorDefinition(
                    numericCode = ErrorCode("1111$seq"),
                    errorName = "ERROR_$i",
                    title = "Error $i",
                    safeDetail = "Detail",
                    messageKey = "error.$i",
                    httpStatus = 500,
                    graphqlClassification = "INTERNAL",
                    retryPolicy = RetryPolicy.NEVER,
                    severity = ErrorSeverity.ERROR,
                    disclosure = DisclosurePolicy.PUBLIC,
                )
                recorder.record(def)
            }
        }
        assertEquals("error metric dimension budget exceeded", ex.message)
    }

    @Test
    fun `ObservabilityPlatformException constructors`() {
        val defaultEx = ObservabilityPlatformException(PlatformErrors.OBSERVABILITY_PIPELINE_FAILED)
        assertEquals("OBSERVABILITY_PIPELINE_FAILED", defaultEx.message)
        assertEquals(null, defaultEx.cause)
        assertEquals(ErrorDiagnostics.EMPTY, defaultEx.diagnostics)

        val cause = RuntimeException("obs root")
        val diag = object : ErrorDiagnostics {
            override val entries = mapOf("metric" to "failed")
        }
        val fullEx = ObservabilityPlatformException(
            PlatformErrors.OBSERVABILITY_PIPELINE_FAILED,
            "custom msg",
            cause,
            diag
        )
        assertEquals("custom msg", fullEx.message)
        assertSame(cause, fullEx.cause)
        assertSame(diag, fullEx.diagnostics)
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
