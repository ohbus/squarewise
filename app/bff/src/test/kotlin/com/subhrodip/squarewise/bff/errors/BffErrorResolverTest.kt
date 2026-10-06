package com.subhrodip.squarewise.bff.errors

import com.fasterxml.jackson.databind.ObjectMapper
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.errors.diagnostics.ResourceIdentifier
import com.subhrodip.squarewise.errors.exceptions.EntityNotFoundException
import com.subhrodip.squarewise.errors.web.ProblemDetailsDto
import graphql.schema.DataFetchingEnvironment
import graphql.language.Field
import graphql.execution.ExecutionStepInfo
import graphql.execution.ResultPath
import graphql.Scalars
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.net.URI
import java.time.Instant
import java.util.concurrent.TimeoutException

/** Verifies upstream identity preservation and typed gateway failure mapping. */
class BffErrorResolverTest {
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
    fun `upstream problem fields are preserved in GraphQL extensions`() {
        val problem = ProblemDetailsDto(
            type = URI("https://squarewise.example/problems/group_not_found"),
            title = "Group not found",
            status = 404,
            detail = "Group not found",
            instance = "/groups/1",
            code = "NOT_FOUND",
            requestId = "upstream-request",
            source = "expense-core",
            timestamp = Instant.parse("2026-10-07T00:00:00Z"),
            numericCode = "213201",
            errorName = "GROUP_NOT_FOUND",
        )

        val error = BffGraphQLErrorResolver().resolve(UpstreamProblemException(problem), environment, "bff-request")

        val extensions = error.extensions!!
        assertEquals("NOT_FOUND", extensions["code"])
        assertEquals("213201", extensions["numericCode"])
        assertEquals("GROUP_NOT_FOUND", extensions["errorName"])
        assertEquals("upstream-request", extensions["requestId"])
        assertEquals("expense-core", extensions["source"])
        assertFalse(extensions.containsKey("bff-request"))
    }

    @Test
    fun `local and timeout failures use governed definitions`() {
        val resolver = BffGraphQLErrorResolver()
        val local = resolver.resolve(EntityNotFoundException(ResourceIdentifier("group")), environment, "request")
        val timeout = resolver.resolve(TimeoutException("network timeout"), environment, "request")

        assertEquals(ExpenseErrors.GROUP_NOT_FOUND.numericCode.value, local.extensions!!["numericCode"])
        assertEquals("UPSTREAM_TIMEOUT", timeout.extensions!!["errorName"])
        assertTrue(timeout.message.isNotBlank())
    }

    @Test
    fun `decoder retains upstream fields`() {
        val decoder = UpstreamProblemDecoder(ObjectMapper())
        val exception = decoder.decode(404, """{"type":"https://example/problem","title":"Not found","detail":"safe","instance":"/groups/1","code":"NOT_FOUND","numericCode":"213201","errorName":"GROUP_NOT_FOUND","requestId":"source-request","source":"expense-core","timestamp":"2026-10-07T00:00:00Z"}""", "fallback", "fallback-source")

        assertEquals("source-request", exception.problem.requestId)
        assertEquals("213201", exception.problem.numericCode)
        assertEquals("expense-core", exception.problem.source)
    }
}
