package com.subhrodip.squarewise.errors.web

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.errors.code.DisclosurePolicy
import com.subhrodip.squarewise.errors.code.ErrorCode
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.code.ErrorSeverity
import com.subhrodip.squarewise.errors.code.RetryPolicy
import com.subhrodip.squarewise.errors.exceptions.ConcurrencyConflictException
import com.subhrodip.squarewise.errors.exceptions.DomainValidationException
import com.subhrodip.squarewise.errors.exceptions.PlatformDomainException
import com.subhrodip.squarewise.errors.http.FieldViolation
import com.subhrodip.squarewise.errors.request.RequestIdContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException

/** Verifies additive problem mapping and containment of cause text. */
class GlobalErrorAdviceTest {

    @Test
    fun `default constructor sets service name to unknown`() {
        val defaultAdvice = GlobalErrorAdvice()
        val response = defaultAdvice.unexpected(IllegalStateException("failed"))
        assertEquals("unknown", response.body?.source)
    }

    @Test
    fun `governed failure emits additive identity without cause text`() {
        val advice = GlobalErrorAdvice("accounts")

        RequestIdContext.with("request-123") {
            val response = advice.governed(ConcurrencyConflictException(cause = IllegalStateException("select password from users")))
            val body = response.body!!

            assertEquals(409, body.status)
            assertEquals(CategoryCode.STATE_CONFLICT.name, body.code)
            assertEquals(PlatformErrors.RESOURCE_CONFLICT.numericCode.value, body.numericCode)
            assertEquals("RESOURCE_CONFLICT", body.errorName)
            assertEquals(PlatformErrors.RESOURCE_CONFLICT.messageKey, body.messageKey)
            assertFalse(body.detail.contains("password"))
            assertEquals("request-123", body.requestId)
        }
    }

    @Test
    fun `governed failure with null httpStatus defaults to 500`() {
        val advice = GlobalErrorAdvice("accounts")
        val response = advice.governed(PlatformDomainException(PlatformErrors.BROKER_UNAVAILABLE))
        assertEquals(500, response.body?.status)
        assertEquals(500, response.statusCode.value())
    }

    @Test
    fun `unexpected failure returns static safe internal problem`() {
        val advice = GlobalErrorAdvice("expense-core")

        val body = advice.unexpected(IllegalStateException("jdbc password=secret" )).body!!

        assertEquals(500, body.status)
        assertEquals(CategoryCode.INTERNAL_ERROR.name, body.code)
        assertEquals("UNEXPECTED_INTERNAL_ERROR", body.errorName)
        assertTrue(body.detail.isNotBlank())
        assertFalse(body.detail.contains("jdbc"))
    }

    @Test
    fun `malformed request maps to static safe problem`() {
        val advice = GlobalErrorAdvice("expense-core")
        val response = advice.malformed()
        assertEquals(400, response.statusCode.value())
        assertEquals("REQUEST_BODY_MALFORMED", response.body?.errorName)
        assertEquals(CategoryCode.MALFORMED_REQUEST.name, response.body?.code)
    }

    @Test
    fun `method not allowed maps with Allow header when supported methods provided`() {
        val advice = GlobalErrorAdvice("expense-core")
        val ex = HttpRequestMethodNotSupportedException("POST", listOf("GET", "HEAD"))
        val response = advice.methodNotAllowed(ex)
        assertEquals(405, response.statusCode.value())
        assertEquals("METHOD_NOT_ALLOWED", response.body?.errorName)
        assertEquals("GET, HEAD", response.headers.getFirst("Allow"))
    }

    @Test
    fun `method not allowed maps with empty Allow header when supported methods is null`() {
        val advice = GlobalErrorAdvice("expense-core")
        val ex = HttpRequestMethodNotSupportedException("POST", null as List<String>?)
        val response = advice.methodNotAllowed(ex)
        assertEquals(405, response.statusCode.value())
        assertEquals("METHOD_NOT_ALLOWED", response.body?.errorName)
        assertEquals("", response.headers.getFirst("Allow"))
    }

    @Test
    fun `governed domain validation extracts and redacts field violations`() {
        val advice = GlobalErrorAdvice("accounts")
        val longText = "a".repeat(300)
        val violations = listOf(
            FieldViolation("user.password", "Too short", "validation.min", "secret123"),
            FieldViolation("user.email", "Invalid email", "validation.email", "bad-email"),
            FieldViolation("user.apiKey", "Invalid key", "validation.key", "my-secret-key"),
            FieldViolation("user.token", "Invalid token", "validation.token", "my-token"),
            FieldViolation("user.secret_stuff", "Invalid secret", "validation.secret", "my-secret"),
            FieldViolation("user.age", "Negative", "validation.min", null),
            FieldViolation("user.bio", "Too long", "validation.max", longText),
        )
        val ex = DomainValidationException(violations)
        val response = advice.governed(ex)
        val body = response.body!!

        assertEquals(422, body.status)
        assertEquals(CategoryCode.VALIDATION_ERROR.name, body.code)
        assertEquals(7, body.violations.size)
        assertEquals("[REDACTED]", body.violations[0].rejectedValue)
        assertEquals("bad-email", body.violations[1].rejectedValue)
        assertEquals("[REDACTED]", body.violations[2].rejectedValue)
        assertEquals("[REDACTED]", body.violations[3].rejectedValue)
        assertEquals("[REDACTED]", body.violations[4].rejectedValue)
        assertEquals(null, body.violations[5].rejectedValue)
        assertEquals(256, (body.violations[6].rejectedValue as String).length)
    }

    @Test
    fun `bean validation extracts and sanitizes field errors`() {
        val advice = GlobalErrorAdvice("accounts")
        val target = Any()
        val binding = BeanPropertyBindingResult(target, "request")
        binding.addError(FieldError("request", "password", "supersecret", false, arrayOf("Size"), null, "Must be at least 8 characters"))
        binding.addError(FieldError("request", "amount", 100, false, null, null, null))
        val param = MethodParameter(GlobalErrorAdviceTest::class.java.getDeclaredMethod("sampleMethod", String::class.java), 0)
        val ex = MethodArgumentNotValidException(param, binding)

        val response = advice.validation(ex)
        val body = response.body!!

        assertEquals(422, body.status)
        assertEquals(CategoryCode.VALIDATION_ERROR.name, body.code)
        assertEquals(2, body.violations.size)
        assertEquals("[REDACTED]", body.violations[0].rejectedValue)
        assertEquals("validation.size", body.violations[0].messageKey)
        assertEquals("100", body.violations[1].rejectedValue)
        assertEquals("The value is invalid", body.violations[1].message)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun sampleMethod(param: String) = Unit
}
