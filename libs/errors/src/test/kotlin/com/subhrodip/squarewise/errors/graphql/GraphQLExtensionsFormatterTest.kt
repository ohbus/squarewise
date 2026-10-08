package com.subhrodip.squarewise.errors.graphql

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.errors.web.ProblemDetailsDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant

class GraphQLExtensionsFormatterTest {

    @Test
    fun `formats extensions from ProblemDetailsDto`() {
        val now = Instant.now()
        val problem = ProblemDetailsDto(
            type = URI("https://squarewise.example/problems/resource_not_found"),
            title = "Resource not found",
            status = 404,
            detail = "The requested resource was not found",
            instance = "/errors/resource_not_found",
            code = CategoryCode.NOT_FOUND.name,
            numericCode = "919201",
            errorName = "RESOURCE_NOT_FOUND",
            messageKey = "error.platform.resource_not_found",
            requestId = "req-1234",
            source = "squarewise-accounts",
            timestamp = now,
            violations = emptyList(),
        )

        val extensions = GraphQLExtensionsFormatter.fromProblem(problem)

        assertEquals(CategoryCode.NOT_FOUND.name, extensions["code"])
        assertEquals("919201", extensions["numericCode"])
        assertEquals("RESOURCE_NOT_FOUND", extensions["errorName"])
        assertEquals("req-1234", extensions["requestId"])
        assertEquals("squarewise-accounts", extensions["source"])
        assertEquals(now.toString(), extensions["timestamp"])
    }

    @Test
    fun `formats extensions from ErrorDefinition and requestId`() {
        val definition = PlatformErrors.AUTHENTICATION_REQUIRED
        val requestId = "req-5678"

        val extensions = GraphQLExtensionsFormatter.fromDefinition(definition, requestId)

        assertEquals(CategoryCode.AUTHENTICATION_ERROR.name, extensions["code"])
        assertEquals(definition.numericCode.value, extensions["numericCode"])
        assertEquals("AUTHENTICATION_REQUIRED", extensions["errorName"])
        assertEquals(definition.messageKey, extensions["messageKey"])
        assertEquals("req-5678", extensions["requestId"])
        assertEquals("squarewise-bff", extensions["source"])
        assertNotNull(extensions["timestamp"])
    }
}
