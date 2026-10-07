package com.subhrodip.squarewise.errors
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.exceptions.PlatformDomainException
import com.subhrodip.squarewise.errors.http.GlobalErrorHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpStatus
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

class GlobalErrorHandlerTest {

    private val handler = GlobalErrorHandler("test-service")

    @Test
    fun `handler defaults the service name when no application name is supplied`() {
        val defaultHandler = GlobalErrorHandler()

        assertEquals("unknown", defaultHandler.unexpected(IllegalStateException()).body?.source)
    }

    @Test
    fun `governed catalog failure maps directly to v1 and six-digit identities`() {
        val response = handler.governedException(
            PlatformDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group 123 not found")
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("NOT_FOUND", response.body?.code)
        assertEquals("919201", response.body?.numericCode)
        assertEquals("RESOURCE_NOT_FOUND", response.body?.errorName)
        assertEquals("The requested API resource or route does not exist.", response.body?.detail)
    }

    @Test
    fun `governed rate limit maps to 429 with bounded retry header`() {
        val response = handler.governedException(
            PlatformDomainException(PlatformErrors.SECURITY_RATE_LIMITED)
        )

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.statusCode)
        assertEquals("RATE_LIMITED", response.body?.code)
        assertEquals("60", response.headers.getFirst("Retry-After"))
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
