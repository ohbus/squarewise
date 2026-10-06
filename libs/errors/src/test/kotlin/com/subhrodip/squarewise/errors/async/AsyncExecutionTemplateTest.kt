package com.subhrodip.squarewise.errors.async

import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import java.util.UUID

/** Verifies retry, poison isolation, dead-letter shaping, and fatal propagation. */
class AsyncExecutionTemplateTest {
    private val context = AsyncContext(UUID.randomUUID(), "expense.created.v1", 1, 1, "expense-core")

    @Test
    fun `transient failure requeues before retry limit`() {
        var recorded: MessageDisposition? = null
        val result = AsyncExecutionTemplate(AsyncMetricsRecorder { _, disposition, _ -> recorded = disposition })
            .execute(context, BffErrors.UPSTREAM_TIMEOUT, "events", byteArrayOf(1)) { error("transient") }

        assertEquals(MessageDisposition.NACK_REQUEUE, (result as AsyncExecutionResult.Failed).disposition)
        assertEquals(MessageDisposition.NACK_REQUEUE, recorded)
        assertEquals(null, result.deadLetter)
    }

    @Test
    fun `poison failure dead letters on first attempt with safe detail`() {
        val result = AsyncExecutionTemplate(AsyncMetricsRecorder { _, _, _ -> })
            .execute(context, ExpenseErrors.GROUP_NOT_FOUND, "events", byteArrayOf(1, 2)) { error("SQL password") }

        val failure = result as AsyncExecutionResult.Failed
        assertEquals(MessageDisposition.DEAD_LETTERED, failure.disposition)
        assertTrue(failure.deadLetter!!.diagnosticReason.contains("group", ignoreCase = true))
        assertTrue(!failure.deadLetter.diagnosticReason.contains("password"))
    }

    @Test
    fun `retry policy dead letters at third attempt`() {
        val retryContext = context.copy(attemptCount = 3)
        val result = AsyncExecutionTemplate(AsyncMetricsRecorder { _, _, _ -> })
            .execute(retryContext, BffErrors.UPSTREAM_TIMEOUT, "events", byteArrayOf(1)) { error("timeout") }

        assertEquals(MessageDisposition.DEAD_LETTERED, (result as AsyncExecutionResult.Failed).disposition)
    }

    @Test
    fun `fatal errors are propagated rather than handled`() {
        assertThrows(OutOfMemoryError::class.java) {
            AsyncExecutionTemplate(AsyncMetricsRecorder { _, _, _ -> })
                .execute(context, BffErrors.UPSTREAM_TIMEOUT, "events", byteArrayOf(1)) { throw OutOfMemoryError() }
        }
    }

    @Test
    fun `successful operation completes`() {
        val result = AsyncExecutionTemplate(AsyncMetricsRecorder { _, _, _ -> })
            .execute(context, ExpenseErrors.GROUP_NOT_FOUND, "events", byteArrayOf(1)) { "ok" }

        assertEquals("ok", (result as AsyncExecutionResult.Completed).value)
    }

    @Test
    fun `context rejects invalid delivery metadata`() {
        assertThrows(IllegalArgumentException::class.java) {
            AsyncContext(UUID.randomUUID(), "", 1, 1, "expense-core")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AsyncContext(UUID.randomUUID(), "x".repeat(129), 1, 1, "expense-core")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AsyncContext(UUID.randomUUID(), "expense.created.v1", 0, 1, "expense-core")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AsyncContext(UUID.randomUUID(), "expense.created.v1", 1, 0, "expense-core")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AsyncContext(UUID.randomUUID(), "expense.created.v1", 1, 5, "expense-core")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AsyncContext(UUID.randomUUID(), "expense.created.v1", 1, 1, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AsyncContext(UUID.randomUUID(), "expense.created.v1", 1, 1, "x".repeat(81))
        }
    }

    @Test
    fun `dead letter builder rejects malformed records`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeadLetterRecordBuilder.build(context, "", byteArrayOf(1), ExpenseErrors.GROUP_NOT_FOUND)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeadLetterRecordBuilder.build(context, "x".repeat(256), byteArrayOf(1), ExpenseErrors.GROUP_NOT_FOUND)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeadLetterRecordBuilder.build(context, "events", byteArrayOf(), ExpenseErrors.GROUP_NOT_FOUND)
        }
    }

    @Test
    fun `cancellation is propagated by disposition strategy`() {
        assertThrows(CancellationException::class.java) {
            MessageDispositionStrategy.decide(BffErrors.UPSTREAM_TIMEOUT, 1, CancellationException())
        }
    }
}
