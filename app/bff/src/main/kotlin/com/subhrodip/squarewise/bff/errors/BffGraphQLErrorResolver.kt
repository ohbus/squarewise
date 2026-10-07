package com.subhrodip.squarewise.bff.errors

import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.errors.graphql.GraphQLExtensionsFormatter
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.ErrorType
import java.util.concurrent.TimeoutException

/** Translates local and upstream failures into additive GraphQL errors. */
class BffGraphQLErrorResolver {
    /** Resolve one failure without changing upstream identity or matching messages. */
    fun resolve(exception: Throwable, environment: DataFetchingEnvironment, requestId: String): GraphQLError {
        val problem = exception as? UpstreamProblemException
        if (problem != null) {
            return error(environment, problem.problem.detail, problem.problem.code, GraphQLExtensionsFormatter.fromProblem(problem.problem), classification(problem.problem.status))
        }
        val definition = when {
            exception is SquarewiseException -> exception.definition
            exception is TimeoutException -> BffErrors.UPSTREAM_TIMEOUT
            else -> BffErrors.GRAPHQL_AGGREGATION_FAILED
        }
            val extensions = GraphQLExtensionsFormatter.fromDefinition(definition, requestId).toMutableMap().apply {
                if (definition.legacyCode == "ERR-11") put("retryAfterSeconds", 60)
            }
            val detail = if (definition.legacyCode == "ERR-11") "Rate limit exceeded" else definition.safeDetail
            return error(environment, detail, definition.errorName, extensions, classification(definition))
    }

    private fun error(environment: DataFetchingEnvironment, detail: String, code: String, extensions: Map<String, Any>, type: ErrorType): GraphQLError =
        GraphqlErrorBuilder.newError(environment).message(detail).errorType(type).extensions(extensions).build()

    private fun classification(status: Int): ErrorType = when {
        status == 401 -> ErrorType.UNAUTHORIZED
        status == 403 -> ErrorType.FORBIDDEN
        status == 404 -> ErrorType.NOT_FOUND
        status >= 500 -> ErrorType.INTERNAL_ERROR
        else -> ErrorType.BAD_REQUEST
    }

    private fun classification(definition: ErrorDefinition): ErrorType = when (definition.graphqlClassification) {
        "UNAUTHENTICATED" -> ErrorType.UNAUTHORIZED
        "FORBIDDEN" -> ErrorType.FORBIDDEN
        "NOT_FOUND" -> ErrorType.NOT_FOUND
        "INTERNAL_ERROR" -> ErrorType.INTERNAL_ERROR
        else -> classification(definition.httpStatus ?: 500)
    }
}
