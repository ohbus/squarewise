package com.subhrodip.squarewise.bff.graphql

import com.subhrodip.squarewise.errors.code.CategoryCode
import graphql.GraphqlErrorBuilder
import graphql.ExecutionResultImpl
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/** Verifies stable public extensions for GraphQL depth, complexity, and validation failures. */
class GraphQlLimitErrorInstrumentationTest {
    private val instrumentation = GraphQlLimitErrorInstrumentation()
    private val parameters = mock(InstrumentationExecutionParameters::class.java)

    @Test
    fun `maps complexity and depth failures to rate limits`() {
        listOf("Maximum query complexity exceeded", "Maximum query depth exceeded").forEach { message ->
            val result = instrument(message)

            assertEquals(CategoryCode.RATE_LIMIT_EXCEEDED.name, result.errors.single().extensions?.get("code"))
            assertEquals(60, result.errors.single().extensions?.get("retryAfterSeconds"))
            assertNotNull(result.errors.single().extensions?.get("requestId"))
        }
    }

    @Test
    fun `maps unrelated execution failures to validation errors`() {
        val result = instrument("Field cannot be selected")
        val error = result.errors.single()

        assertEquals(CategoryCode.VALIDATION_ERROR.name, error.extensions?.get("code"))
        assertEquals("GraphQL request is invalid", error.message)
        assertNotNull(error.extensions?.get("requestId"))
    }

    @Test
    fun `preserves errors that already carry a public code`() {
        val original = GraphqlErrorBuilder.newError()
            .message("already classified")
            .extensions(mapOf<String, Any>("code" to "FORBIDDEN"))
            .build()
        val execution = ExecutionResultImpl.newExecutionResult().errors(listOf(original)).build()

        val result = instrumentation.instrumentExecutionResult(execution, parameters, null).join()

        assertEquals(original, result.errors.single())
    }

    private fun instrument(message: String) =
        instrumentation.instrumentExecutionResult(
            ExecutionResultImpl.newExecutionResult()
                .errors(listOf(GraphqlErrorBuilder.newError().message(message).build()))
                .build(),
            parameters,
            null
        ).join()
}
