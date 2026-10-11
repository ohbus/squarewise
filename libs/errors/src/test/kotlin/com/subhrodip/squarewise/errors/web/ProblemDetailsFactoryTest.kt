package com.subhrodip.squarewise.errors.web

import com.subhrodip.squarewise.errors.catalog.BffErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies default problem-detail fields remain catalog-controlled. */
class ProblemDetailsFactoryTest {
    @Test
    fun `creates a safe problem without optional violations`() {
        val problem = ProblemDetailsFactory("test-service").create(BffErrors.UPSTREAM_TIMEOUT, "request-1")

        assertEquals(BffErrors.UPSTREAM_TIMEOUT.safeDetail, problem.detail)
        assertEquals("test-service", problem.source)
        assertTrue(problem.violations.isEmpty())
    }
}
