package com.subhrodip.squarewise.errors.request

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.slf4j.MDC

/** Verifies request correlation IDs are propagated and never leak between requests. */
class RequestIdContextAndFilterTest {

    private val filter = RequestIdFilter()

    /** A valid client correlation ID is reflected and visible inside the request context. */
    @Test
    fun `valid request id is propagated through the filter`() {
        val request = MockHttpServletRequest().apply {
            method = "GET"
            requestURI = "/health"
            addHeader(RequestIdFilter.HEADER, "client-request-42")
        }
        val response = MockHttpServletResponse()
        var observedContext = ""
        val chain = FilterChain { _, _ -> observedContext = RequestIdContext.get() }

        filter.doFilter(request, response, chain)

        assertEquals("client-request-42", response.getHeader(RequestIdFilter.HEADER))
        assertEquals("client-request-42", observedContext)
        assertEquals("missing-request-id", RequestIdContext.get())
    }

    /** Invalid and oversized IDs are replaced with a bounded generated UUID. */
    @Test
    fun `invalid request id is replaced and context is cleaned`() {
        val request = MockHttpServletRequest().apply {
            method = "POST"
            requestURI = "/groups"
            addHeader(RequestIdFilter.HEADER, "contains spaces")
        }
        val response = MockHttpServletResponse()
        var observedContext = ""
        val chain = FilterChain { _, _ -> observedContext = RequestIdContext.get() }

        filter.doFilter(request, response, chain)

        val responseId = response.getHeader(RequestIdFilter.HEADER)
        assertNotEquals("contains spaces", responseId)
        assertTrue(responseId?.matches(Regex("[0-9a-f-]{36}")) == true)
        assertEquals(responseId, observedContext)
        assertEquals("missing-request-id", RequestIdContext.get())
    }

    /** Missing and oversized client IDs are both replaced instead of reflected. */
    @Test
    fun `missing and oversized request ids are replaced`() {
        val missingRequest = MockHttpServletRequest().apply {
            method = "GET"
            requestURI = "/health"
        }
        val missingResponse = MockHttpServletResponse()
        filter.doFilter(missingRequest, missingResponse, FilterChain { _, _ -> })

        val oversizedRequest = MockHttpServletRequest().apply {
            method = "GET"
            requestURI = "/health"
            addHeader(RequestIdFilter.HEADER, "x".repeat(129))
        }
        val oversizedResponse = MockHttpServletResponse()
        filter.doFilter(oversizedRequest, oversizedResponse, FilterChain { _, _ -> })

        val emptyRequest = MockHttpServletRequest().apply {
            method = "GET"
            requestURI = "/health"
            addHeader(RequestIdFilter.HEADER, "")
        }
        val emptyResponse = MockHttpServletResponse()
        filter.doFilter(emptyRequest, emptyResponse, FilterChain { _, _ -> })

        assertTrue(missingResponse.getHeader(RequestIdFilter.HEADER)?.matches(Regex("[0-9a-f-]{36}")) == true)
        assertTrue(oversizedResponse.getHeader(RequestIdFilter.HEADER)?.matches(Regex("[0-9a-f-]{36}")) == true)
        assertTrue(emptyResponse.getHeader(RequestIdFilter.HEADER)?.matches(Regex("[0-9a-f-]{36}")) == true)
    }

    /** Exceptions from downstream handlers propagate while the filter still clears context. */
    @Test
    fun `filter clears context when downstream chain fails`() {
        val request = MockHttpServletRequest().apply {
            method = "GET"
            requestURI = "/failure"
            addHeader(RequestIdFilter.HEADER, "chain-failure")
        }
        val response = MockHttpServletResponse()

        val failure = IllegalStateException("downstream failure")
        val thrown = runCatching {
            filter.doFilter(request, response, FilterChain { _, _ -> throw failure })
        }.exceptionOrNull()

        assertEquals(failure, thrown)
        assertEquals("missing-request-id", RequestIdContext.get())
    }

    /** Nested context scopes restore no stale value after success or failure. */
    @Test
    fun `context cleanup runs when action throws`() {
        val failure = IllegalStateException("boom")

        try {
            RequestIdContext.with("request-1") {
                assertEquals("request-1", RequestIdContext.get())
                throw failure
            }
        } catch (actual: IllegalStateException) {
            assertEquals(failure, actual)
        }

        assertEquals("missing-request-id", RequestIdContext.get())
    }

    /** MDC remains a supported read path when no local context scope is active. */
    @Test
    fun `context reads correlation id from MDC fallback`() {
        try {
            MDC.put(RequestIdContext.MDC_KEY, "mdc-request-7")
            assertEquals("mdc-request-7", RequestIdContext.get())
            assertEquals("mdc-request-7", RequestIdContext.getOrGenerate())
        } finally {
            MDC.remove(RequestIdContext.MDC_KEY)
        }
    }

    @Test
    fun `getOrGenerate returns bound context when active or generates fresh id when missing`() {
        RequestIdContext.with("explicit-id-99") {
            assertEquals("explicit-id-99", RequestIdContext.getOrGenerate())
        }

        val generated = RequestIdContext.getOrGenerate()
        assertTrue(generated.matches(Regex("[0-9a-f-]{36}")))
    }

    @Test
    fun `RequestIdGenerator produces valid uuid`() {
        val id1 = RequestIdGenerator.next()
        val id2 = RequestIdGenerator.next()
        assertNotEquals(id1, id2)
        assertEquals(7, id1.version())
    }
}
