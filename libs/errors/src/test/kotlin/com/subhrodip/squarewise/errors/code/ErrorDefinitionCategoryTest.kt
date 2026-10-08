package com.subhrodip.squarewise.errors.code

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Quality test suite verifying coarse category derivation across every branch and pattern
 * in ErrorDefinition.category.
 */
class ErrorDefinitionCategoryTest {

    private fun definition(name: String, status: Int? = null): ErrorDefinition = object : ErrorDefinition {
        override val numericCode: ErrorCode = ErrorCode("999901")
        override val errorName: String = name
        override val title: String = name
        override val safeDetail: String = name
        override val messageKey: String = "error.test"
        override val httpStatus: Int? = status
        override val graphqlClassification: String? = null
        override val retryPolicy: RetryPolicy = RetryPolicy.NEVER
        override val severity: ErrorSeverity = ErrorSeverity.ERROR
        override val disclosure: DisclosurePolicy = DisclosurePolicy.PUBLIC
    }

    @Test
    fun `maps malformed request category`() {
        assertEquals(CategoryCode.MALFORMED_REQUEST, definition("REQUEST_BODY_MALFORMED").category)
        assertEquals(CategoryCode.MALFORMED_REQUEST, definition("GRAPHQL_OPERATION_INVALID").category)
    }

    @Test
    fun `maps resource gone category`() {
        assertEquals(CategoryCode.RESOURCE_GONE, definition("SYNC_CURSOR_EXPIRED").category)
        assertEquals(CategoryCode.RESOURCE_GONE, definition("INVITATION_EXPIRED").category)
        assertEquals(CategoryCode.RESOURCE_GONE, definition("ANY_OLD_RESOURCE", 410).category)
    }

    @Test
    fun `maps rate limit exceeded category`() {
        assertEquals(CategoryCode.RATE_LIMIT_EXCEEDED, definition("SOME_ERROR", 429).category)
        assertEquals(CategoryCode.RATE_LIMIT_EXCEEDED, definition("IP_RATE_LIMITED").category)
        assertEquals(CategoryCode.RATE_LIMIT_EXCEEDED, definition("GLOBAL_LIMITER_BLOCKED").category)
        assertEquals(CategoryCode.RATE_LIMIT_EXCEEDED, definition("DAILY_RATE_LIMIT_EXCEEDED").category)
        // LIMIT without EXCEEDED should not match rate limit exceeded
        assertEquals(CategoryCode.INTERNAL_ERROR, definition("SOFT_LIMIT_REACHED").category)
    }

    @Test
    fun `maps authentication error category`() {
        assertEquals(CategoryCode.AUTHENTICATION_ERROR, definition("CUSTOM_AUTH", 401).category)
        assertEquals(CategoryCode.AUTHENTICATION_ERROR, definition("USER_UNAUTHENTICATED").category)
        assertEquals(CategoryCode.AUTHENTICATION_ERROR, definition("BEARER_TOKEN_INVALID").category)
        assertEquals(CategoryCode.AUTHENTICATION_ERROR, definition("USER_SESSION_TERMINATED").category)
        assertEquals(CategoryCode.AUTHENTICATION_ERROR, definition("USER_LOGIN_FAILED").category)
        // Ensure LOGIN strings containing RATE or LIMIT don't accidentally match authentication branch if status != 401
        assertEquals(CategoryCode.RATE_LIMIT_EXCEEDED, definition("LOGIN_RATE_LIMITED").category)
        assertEquals(CategoryCode.INTERNAL_ERROR, definition("LOGIN_RATE_CHECK_FAILED").category)
        assertEquals(CategoryCode.INTERNAL_ERROR, definition("LOGIN_LIMIT_CHECK_FAILED").category)
    }

    @Test
    fun `maps authorization error category`() {
        assertEquals(CategoryCode.AUTHORIZATION_ERROR, definition("FORBIDDEN_CALL", 403).category)
        assertEquals(CategoryCode.AUTHORIZATION_ERROR, definition("RESOURCE_ACCESS_DENIED").category)
        assertEquals(CategoryCode.AUTHORIZATION_ERROR, definition("GROUP_FORBIDDEN").category)
        assertEquals(CategoryCode.AUTHORIZATION_ERROR, definition("CSRF_REJECTED").category)
        assertEquals(CategoryCode.AUTHORIZATION_ERROR, definition("ORIGIN_NOT_ALLOWED").category)
    }

    @Test
    fun `maps not found category`() {
        assertEquals(CategoryCode.NOT_FOUND, definition("OBJECT_MISSING", 404).category)
        assertEquals(CategoryCode.NOT_FOUND, definition("ITEM_NOT_FOUND").category)
        assertEquals(CategoryCode.NOT_FOUND, definition("ITEM_HIDDEN").category)
    }

    @Test
    fun `maps state conflict category`() {
        assertEquals(CategoryCode.STATE_CONFLICT, definition("CONCURRENT_UPDATE", 409).category)
        assertEquals(CategoryCode.STATE_CONFLICT, definition("VERSION_CONFLICT").category)
        assertEquals(CategoryCode.STATE_CONFLICT, definition("EMAIL_ALREADY_REGISTERED").category)
        assertEquals(CategoryCode.STATE_CONFLICT, definition("WORKSPACE_ARCHIVED").category)
        assertEquals(CategoryCode.STATE_CONFLICT, definition("SUBSCRIPTION_PAUSED").category)
        assertEquals(CategoryCode.STATE_CONFLICT, definition("PAYMENT_REVERSED").category)
        assertEquals(CategoryCode.STATE_CONFLICT, definition("COUPON_CLAIMED").category)
        assertEquals(CategoryCode.STATE_CONFLICT, definition("INVITATION_REVOKED").category)
        assertEquals(CategoryCode.STATE_CONFLICT, definition("MEMBER_REMOVED").category)
        assertEquals(CategoryCode.STATE_CONFLICT, definition("ROLE_BOUND").category)
        assertEquals(CategoryCode.STATE_CONFLICT, definition("DUPLICATE_ENTRY").category)
    }

    @Test
    fun `maps business rule violation category`() {
        assertEquals(CategoryCode.BUSINESS_RULE_VIOLATION, definition("ALLOCATION_SUM_MISMATCH").category)
        assertEquals(CategoryCode.BUSINESS_RULE_VIOLATION, definition("PARTICIPANT_SET_INVALID").category)
        assertEquals(CategoryCode.BUSINESS_RULE_VIOLATION, definition("INSUFFICIENT_BALANCE").category)
    }

    @Test
    fun `maps validation error category`() {
        assertEquals(CategoryCode.VALIDATION_ERROR, definition("GENERIC_VALIDATION_ERROR", 422).category)
    }

    @Test
    fun `maps default internal error category`() {
        assertEquals(CategoryCode.INTERNAL_ERROR, definition("UNEXPECTED_DATABASE_CRASH", 500).category)
        assertEquals(CategoryCode.INTERNAL_ERROR, definition("SOMETHING_STRANGE", null).category)
    }
}
