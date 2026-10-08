package com.subhrodip.squarewise.errors.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.PrintWriter
import java.io.StringWriter

class StaticErrorPageFilterTest {

    private val filter = StaticErrorPageFilter()

    @Test
    fun `passes through when chain executes normally`() {
        val request = mock(HttpServletRequest::class.java)
        val response = mock(HttpServletResponse::class.java)
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
    }

    @Test
    fun `handles exception and writes static problem details response`() {
        val request = mock(HttpServletRequest::class.java)
        val response = mock(HttpServletResponse::class.java)
        val chain = mock(FilterChain::class.java)
        val writer = StringWriter()

        `when`(chain.doFilter(request, response)).thenThrow(RuntimeException("servlet failed"))
        `when`(response.writer).thenReturn(PrintWriter(writer))

        filter.doFilter(request, response, chain)

        verify(response).status = 500
        verify(response).contentType = "application/problem+json"
        val output = writer.toString()
        assertTrue(output.contains("\"status\":500"))
        assertTrue(output.contains("\"code\":\"INTERNAL_ERROR\""))
        assertTrue(output.contains("\"errorName\":\"UNEXPECTED_INTERNAL_ERROR\""))
        assertTrue(output.contains("\"messageKey\":\"error.platform.unexpected_internal\""))
    }

    @Test
    fun `rethrows fatal exceptions like OutOfMemoryError`() {
        val request = mock(HttpServletRequest::class.java)
        val response = mock(HttpServletResponse::class.java)
        val chain = mock(FilterChain::class.java)

        `when`(chain.doFilter(request, response)).thenThrow(OutOfMemoryError("heap exhausted"))

        assertThrows(OutOfMemoryError::class.java) {
            filter.doFilter(request, response, chain)
        }
    }

    @Test
    fun `rethrows fatal exceptions like InterruptedException`() {
        val request = mock(HttpServletRequest::class.java)
        val response = mock(HttpServletResponse::class.java)
        val chain = mock(FilterChain::class.java)

        `when`(chain.doFilter(request, response)).thenAnswer { throw InterruptedException("thread interrupted") }

        assertThrows(InterruptedException::class.java) {
            filter.doFilter(request, response, chain)
        }
    }
}
