package com.subhrodip.squarewise.errors
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

import com.subhrodip.squarewise.errors.domain.ApplicationException
import com.subhrodip.squarewise.errors.domain.ErrorCode
import com.subhrodip.squarewise.errors.http.GlobalErrorHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.mockito.Mockito

class GlobalErrorHandlerTest {

    private val handler = GlobalErrorHandler("test-service")

    @Test
    fun `handler defaults the service name when no application name is supplied`() {
        val defaultHandler = GlobalErrorHandler()

        assertEquals("unknown", defaultHandler.unexpected(IllegalStateException()).body?.source)
    }

    @Test
    fun `applicationException maps correctly to problem details`() {
        val ex = ApplicationException(ErrorCode.ERR_05, "Group 123 not found")
        val response = handler.applicationException(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals(404, body?.status)
        assertEquals("NOT_FOUND", body?.code)
        assertEquals("test-service", body?.source)
        assertEquals("Group 123 not found", body?.detail)
    }

    @Test
    fun `rate limit application error maps to 429 with bounded retry header`() {
        val response = handler.applicationException(ApplicationException(ErrorCode.ERR_11))

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.statusCode)
        assertEquals("RATE_LIMITED", response.body?.code)
        assertEquals("60", response.headers.getFirst("Retry-After"))
    }

    /**
     * Verifies an invalid catalog status fails closed to an internal error.
     *
     * Production [ErrorCode] entries all carry valid HTTP statuses; the mocked
     * value exercises the handler's defensive fallback if that invariant is
     * ever violated by a future catalog change.
     */
    @Test
    fun `invalid catalog status falls back to internal error`() {
        val invalidCode = mock(ErrorCode::class.java)
        `when`(invalidCode.httpStatus).thenReturn(999)
        `when`(invalidCode.safeDetail).thenReturn("Unknown catalog error")
        `when`(invalidCode.code).thenReturn("ERR-X")
        `when`(invalidCode.name).thenReturn("ERR_X")

        val response = handler.applicationException(ApplicationException(invalidCode, "safe detail"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("safe detail", response.body?.detail)
    }

    @Test
    fun `all ErrorCode values map to RFC problem schema codes`() {
        val allowedCodes = setOf(
            "VALIDATION_FAILED",
            "UNAUTHENTICATED",
            "FORBIDDEN",
            "NOT_FOUND",
            "CONFLICT",
            "IDEMPOTENCY_CONFLICT",
            "RATE_LIMITED",
            "INTERNAL_ERROR"
        )

        ErrorCode.entries.forEach { code ->
            val ex = ApplicationException(code, "Test error for $code")
            val response = handler.applicationException(ex)
            val problemCode = response.body?.code
            assertTrue(
                allowedCodes.contains(problemCode),
                "Code '$problemCode' for $code must be one of problem.schema.json allowed codes"
            )
        }
    }

    @Test
    fun `illegalArgument maps to BAD_REQUEST and VALIDATION_FAILED`() {
        val response = handler.illegalArgument(IllegalArgumentException("Invalid parameter"))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_FAILED", response.body?.code)
        assertEquals("Invalid parameter", response.body?.detail)
    }

    @Test
    fun `optimisticLock maps to CONFLICT`() {
        val response = handler.optimisticLock(OptimisticLockingFailureException("Version mismatch"))
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("CONFLICT", response.body?.code)
    }

    @Test
    fun `unexpected exception maps to 500 and INTERNAL_ERROR`() {
        val response = handler.unexpected(RuntimeException("Database connection dropped"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.code)
    }

    /** Verifies every catalog code keeps its public status and bounded response metadata. */
    @Test
    fun `every catalog error maps to its governed status`() {
        val expectedStatuses = mapOf(
            ErrorCode.ERR_01 to HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.ERR_02 to HttpStatus.BAD_REQUEST,
            ErrorCode.ERR_03 to HttpStatus.UNAUTHORIZED,
            ErrorCode.ERR_04 to HttpStatus.FORBIDDEN,
            ErrorCode.ERR_05 to HttpStatus.NOT_FOUND,
            ErrorCode.ERR_06 to HttpStatus.CONFLICT,
            ErrorCode.ERR_07 to HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.ERR_08 to HttpStatus.BAD_GATEWAY,
            ErrorCode.ERR_09 to HttpStatus.CONFLICT,
            ErrorCode.ERR_10 to HttpStatusCode.valueOf(422),
            ErrorCode.ERR_11 to HttpStatus.TOO_MANY_REQUESTS,
            ErrorCode.ERR_12 to HttpStatus.OK
        )

        expectedStatuses.forEach { (code, status) ->
            val response = handler.applicationException(ApplicationException(code, "safe detail"))

            assertEquals(status, response.statusCode, "Unexpected HTTP status for $code")
            assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
            assertEquals("test-service", response.body?.source)
            assertEquals("missing-request-id", response.body?.requestId)
            assertEquals("safe detail", response.body?.detail)
            assertEquals(if (code == ErrorCode.ERR_11) "60" else null, response.headers.getFirst("Retry-After"))
        }

        val safeFallback = handler.applicationException(ApplicationException(ErrorCode.ERR_01))
        assertEquals("Internal server error", safeFallback.body?.detail)

        val nullMessage = Mockito.mock(ApplicationException::class.java)
        Mockito.`when`(nullMessage.errorCode).thenReturn(ErrorCode.ERR_02)
        Mockito.`when`(nullMessage.message).thenReturn(null)
        assertEquals("Invalid request parameters", handler.applicationException(nullMessage).body?.detail)
    }

    /** Verifies the content-negotiation failure intentionally has no RFC 7807 body. */
    @Test
    fun `not acceptable response is bodyless`() {
        val response = handler.notAcceptable()

        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.statusCode)
        assertEquals(null, response.body)
    }

    /** Verifies field-level validation messages and the safe default message branch. */
    @Test
    fun `validation maps field violations`() {
        val binding = BeanPropertyBindingResult(Any(), "request")
        binding.addError(FieldError("request", "amount", "bad", false, null, null, null))
        binding.addError(FieldError("request", "currency", "bad", false, null, null, "invalid currency"))

        val response = handler.validation(MethodArgumentNotValidException(sampleParameter(), binding))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(2, response.body?.violations?.size)
        assertEquals("amount", response.body?.violations?.first()?.field)
        assertEquals("invalid currency", response.body?.violations?.last()?.message)
    }

    /** Verifies malformed-body detail precedence for root causes and empty messages. */
    @Test
    fun `malformed body uses root cause or safe fallback`() {
        val rootCause = IllegalStateException("invalid json")
        val withRootCause = handler.messageNotReadable(
            HttpMessageNotReadableException("outer", rootCause, inputMessage())
        )
        val withoutDetail = handler.messageNotReadable(
            HttpMessageNotReadableException("", inputMessage())
        )

        assertEquals("invalid json", withRootCause.body?.detail)
        assertEquals("Malformed request payload", withoutDetail.body?.detail)

        val blankRootCause = handler.messageNotReadable(
            HttpMessageNotReadableException("outer detail", IllegalStateException(""), inputMessage())
        )
        assertEquals("outer detail", blankRootCause.body?.detail)

        val nullRootCause = handler.messageNotReadable(
            HttpMessageNotReadableException("outer fallback", Throwable(null as String?), inputMessage())
        )
        assertEquals("outer fallback", nullRootCause.body?.detail)

        val blankEverything = handler.messageNotReadable(
            HttpMessageNotReadableException("", IllegalStateException(""), inputMessage())
        )
        assertEquals("Malformed request payload", blankEverything.body?.detail)

        val mockedException = mock(HttpMessageNotReadableException::class.java)
        `when`(mockedException.rootCause).thenReturn(null)
        `when`(mockedException.message).thenReturn("mocked detail")
        assertEquals("mocked detail", handler.messageNotReadable(mockedException).body?.detail)

        `when`(mockedException.message).thenReturn(null)
        assertEquals("Malformed request payload", handler.messageNotReadable(mockedException).body?.detail)
    }

    /** Verifies binding and type-mismatch handlers preserve safe fallback text. */
    @Test
    fun `binding and type mismatch use stable fallback responses`() {
        val binding = handler.requestBinding(ServletRequestBindingException("missing header"))
        val bindingFallback = handler.requestBinding(ServletRequestBindingException(""))
        val mismatch = handler.argumentTypeMismatch(
            MethodArgumentTypeMismatchException("x", Int::class.java, "page", sampleParameter(), null)
        )
        val unknownType = handler.argumentTypeMismatch(
            MethodArgumentTypeMismatchException("x", null, "page", sampleParameter(), null)
        )
        val lockFallback = handler.optimisticLock(OptimisticLockingFailureException(null))
        val argumentFallback = handler.illegalArgument(IllegalArgumentException())

        assertEquals("missing header", binding.body?.detail)
        assertEquals("Invalid request binding", bindingFallback.body?.detail)
        assertEquals("Type mismatch for parameter page", mismatch.body?.title)
        assertEquals(HttpStatus.BAD_REQUEST, mismatch.statusCode)
        assertEquals("Type mismatch for parameter page", unknownType.body?.title)
        assertEquals("Resource was updated by another transaction", lockFallback.body?.detail)
        assertEquals("Invalid request", argumentFallback.body?.detail)
    }

    private fun sampleParameter(): MethodParameter = MethodParameter(
        GlobalErrorHandlerTest::class.java.getDeclaredMethod("sample", String::class.java),
        0
    )

    @Suppress("UNUSED_PARAMETER")
    private fun sample(value: String) = Unit

    private fun inputMessage(): HttpInputMessage = mock(HttpInputMessage::class.java)
}
