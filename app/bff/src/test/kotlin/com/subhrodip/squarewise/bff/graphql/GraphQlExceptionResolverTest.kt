package com.subhrodip.squarewise.bff.graphql

import com.subhrodip.squarewise.bff.transport.UpstreamServiceException
import com.subhrodip.squarewise.bff.errors.BffDomainException
import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import graphql.language.Field
import graphql.execution.ExecutionStepInfo
import graphql.execution.ResultPath
import graphql.Scalars
import graphql.schema.DataFetchingEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.graphql.execution.ErrorType

/** Verifies the GraphQL resolver's complete public failure classification matrix. */
class GraphQlExceptionResolverTest {
    private val resolver = ExposedResolver()
    private val environment = mock(DataFetchingEnvironment::class.java).apply {
        `when`(getField()).thenReturn(Field("test"))
        `when`(getExecutionStepInfo()).thenReturn(
            ExecutionStepInfo.newExecutionStepInfo()
                .type(Scalars.GraphQLString)
                .path(ResultPath.rootPath())
                .build()
        )
    }

    @Test
    fun `maps every governed BFF definition to its public GraphQL contract`() {
        listOf(
            BffErrors.GRAPHQL_INPUT_INVALID,
            BffErrors.GRAPHQL_OPERATION_INVALID,
            BffErrors.GRAPHQL_AGGREGATION_FAILED,
            BffErrors.SUBSCRIPTION_LIMIT_EXCEEDED,
            PlatformErrors.AUTHENTICATION_REQUIRED,
        ).forEach { definition ->
            val error = resolver.resolve(BffDomainException(definition), environment)

            assertEquals(expectedGraphQlCode(definition), error.extensions?.get("code"))
            assertEquals(definition.safeDetail, error.message)
            assertEquals(expectedClassification(definition), error.errorType)
            assertNotNull(error.extensions?.get("requestId"))
            if (definition == BffErrors.SUBSCRIPTION_LIMIT_EXCEEDED) {
                assertEquals(60, error.extensions?.get("retryAfterSeconds"))
            } else {
                assertNull(error.extensions?.get("retryAfterSeconds"))
            }
        }
    }

    @Test
    fun `maps every upstream status and uses internal default for unknown status`() {
        val expected = mapOf(
            400 to "VALIDATION_ERROR",
            401 to "AUTHENTICATION_ERROR",
            403 to "AUTHORIZATION_ERROR",
            404 to "NOT_FOUND",
            409 to "STATE_CONFLICT",
            422 to "VALIDATION_ERROR",
            429 to "RATE_LIMIT_EXCEEDED",
            500 to "INTERNAL_ERROR",
            503 to "INTERNAL_ERROR",
            504 to "INTERNAL_ERROR"
        )

        expected.forEach { (status, code) ->
            val error = resolver.resolve(UpstreamServiceException(status), environment)
            assertEquals(code, error.extensions?.get("code"))
            assertNotNull(error.extensions?.get("requestId"))
            if (status == 429) assertEquals(60, error.extensions?.get("retryAfterSeconds"))
        }
    }

    @Test
    fun `maps argument and unknown failures to safe validation and internal errors`() {
        val argumentError = resolver.resolve(IllegalArgumentException("secret detail"), environment)
        assertEquals("VALIDATION_ERROR", argumentError.extensions?.get("code"))
        assertEquals(ErrorType.BAD_REQUEST, argumentError.errorType)
        assertEquals(BffErrors.GRAPHQL_INPUT_INVALID.safeDetail, argumentError.message)

        val unknownError = resolver.resolve(IllegalStateException("secret detail"), environment)
        assertEquals("INTERNAL_ERROR", unknownError.extensions?.get("code"))
        assertEquals(ErrorType.INTERNAL_ERROR, unknownError.errorType)
        assertEquals(BffErrors.GRAPHQL_AGGREGATION_FAILED.safeDetail, unknownError.message)
    }

    private fun expectedGraphQlCode(definition: ErrorDefinition): String =
        definition.category.name

    private fun expectedClassification(definition: ErrorDefinition): ErrorType = when (definition.httpStatus) {
        401 -> ErrorType.UNAUTHORIZED
        403 -> ErrorType.FORBIDDEN
        404 -> ErrorType.NOT_FOUND
        in 500..599 -> ErrorType.INTERNAL_ERROR
        else -> ErrorType.BAD_REQUEST
    }

    private class ExposedResolver : GraphQlExceptionResolver() {
        fun resolve(exception: Throwable, environment: DataFetchingEnvironment) =
            resolveToSingleError(exception, environment)
    }
}
