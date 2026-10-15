package com.subhrodip.squarewise.errors.exceptions

import net.bytebuddy.ByteBuddy
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException

/** Verifies that fatal termination signals are never treated as recoverable. */
class FatalClassifierTest {
    @Test
    fun `classifies fatal JVM and cancellation throwables`() {
        assertTrue(FatalErrorClassifier.isFatal(OutOfMemoryError()))
        assertTrue(FatalErrorClassifier.isFatal(newThreadDeath()))
        assertTrue(FatalErrorClassifier.isFatal(LinkageError()))
        assertTrue(FatalErrorClassifier.isFatal(InterruptedException()))
        assertTrue(FatalErrorClassifier.isFatal(CancellationException()))
        assertTrue(FatalErrorClassifier.isFatal(newCoroutineCancellationException()))
        assertFalse(FatalErrorClassifier.isFatal(IllegalStateException()))
    }

    private fun newThreadDeath(): Throwable =
        Class.forName("java.lang.ThreadDeath").getDeclaredConstructor().newInstance() as Throwable

    private fun newCoroutineCancellationException(): Throwable {
        val loaded = ByteBuddy()
            .subclass(RuntimeException::class.java)
            .name("kotlinx.coroutines.CancellationException")
            .make()
            .load(FatalClassifierTest::class.java.classLoader)
            .loaded
        return loaded.getDeclaredConstructor().newInstance() as Throwable
    }
}
