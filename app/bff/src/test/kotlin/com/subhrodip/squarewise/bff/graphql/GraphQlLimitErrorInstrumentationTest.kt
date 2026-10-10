package com.subhrodip.squarewise.bff.graphql

import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import graphql.GraphqlErrorBuilder
import graphql.ExecutionResultImpl
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/** Verifies stable public extensions for GraphQL depth, complexity, and validation failures. */
class GraphQlLimitErrorInstrumentationTest {
    private val instrumentation = GraphQlLimitErrorInstrumentation()
    private val parameters = mock(InstrumentationExecutionParameters::class.java)

    @Test
    fun `maps complexity failures from the graphql engine to rate limits`() {
        val result = execute(GraphQlComplexityLimitInstrumentation(1))

        assertRateLimited(result)
    }

    @Test
    fun `maps depth failure extensions from the graphql engine to rate limits`() {
        val result = execute(GraphQlDepthLimitInstrumentation(1))

        assertRateLimited(result)
    }

    @Test
    fun `preserves the depth limit marker on graphql execution errors`() {
        val result = execute(GraphQlDepthLimitInstrumentation(1), mapLimitErrors = false)

        assertEquals("DEPTH", result.errors.single().extensions?.get("squarewiseLimit"))
    }

    @Test
    fun `maps unrelated execution failures to validation errors`() {
        val result = instrument("Field cannot be selected")
        val error = result.errors.single()

        assertEquals(CategoryCode.VALIDATION_ERROR.name, error.extensions?.get("code"))
        assertEquals(BffErrors.GRAPHQL_INPUT_INVALID.safeDetail, error.message)
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
                .errors(listOf(
                    GraphqlErrorBuilder.newError()
                        .message(message)
                        .build()
                ))
                .build(),
            parameters,
            null
        ).join()

    private fun execute(limitInstrumentation: Instrumentation, mapLimitErrors: Boolean = true): ExecutionResult =
        GraphQL.newGraphQL(schema())
            .instrumentation(
                if (mapLimitErrors) {
                    ChainedInstrumentation(listOf(limitInstrumentation, instrumentation))
                } else {
                    limitInstrumentation
                }
            )
            .build()
            .execute("{ root { value } }")

    private fun assertRateLimited(result: ExecutionResult) {
        val error = result.errors.single()
        assertEquals(CategoryCode.RATE_LIMIT_EXCEEDED.name, error.extensions?.get("code"))
        assertEquals(PlatformErrors.SECURITY_RATE_LIMITED.safeDetail, error.message)
        assertEquals(60, error.extensions?.get("retryAfterSeconds"))
        assertNotNull(error.extensions?.get("requestId"))
    }

    private fun schema() = SchemaGenerator().makeExecutableSchema(
        SchemaParser().parse("type Query { root: Root } type Root { value: String }"),
        RuntimeWiring.newRuntimeWiring()
            .type("Query") { builder ->
                builder.dataFetcher("root") { mapOf("value" to "ok") }
            }
            .build()
    )
}
