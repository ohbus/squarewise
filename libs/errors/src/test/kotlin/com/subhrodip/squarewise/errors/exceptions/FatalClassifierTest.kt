package com.subhrodip.squarewise.errors.exceptions

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies that fatal termination signals are never treated as recoverable. */
class FatalClassifierTest {
    @Test
    fun `classifies fatal JVM and cancellation throwables`() {
        assertTrue(FatalErrorClassifier.isFatal(OutOfMemoryError()))
        assertTrue(FatalErrorClassifier.isFatal(ThreadDeath()))
        assertTrue(FatalErrorClassifier.isFatal(LinkageError()))
        assertTrue(FatalErrorClassifier.isFatal(InterruptedException()))
        assertTrue(FatalErrorClassifier.isFatal(java.util.concurrent.CancellationException()))
        assertFalse(FatalErrorClassifier.isFatal(IllegalStateException()))
    }
}
