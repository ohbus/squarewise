package com.subhrodip.squarewise.bff.graphql

import com.subhrodip.squarewise.bff.errors.BffGraphQLErrorResolver
import com.subhrodip.squarewise.bff.errors.BffDomainException
import com.subhrodip.squarewise.bff.errors.UpstreamProblemException
import com.subhrodip.squarewise.bff.transport.UpstreamServiceException
import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.errors.request.RequestIdContext
import graphql.GraphQLError
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component

/** Maps resolver failures to the public GraphQL error-code contract. */
@Component
class GraphQlExceptionResolver : DataFetcherExceptionResolverAdapter() {
    private val governedResolver = BffGraphQLErrorResolver()

    override fun resolveToSingleError(exception: Throwable, environment: DataFetchingEnvironment): GraphQLError {
        if (exception is UpstreamProblemException || exception is SquarewiseException || exception is java.util.concurrent.TimeoutException) {
            return governedResolver.resolve(exception, environment, RequestIdContext.get())
        }
        val definition = when {
            exception is UpstreamServiceException && exception.definition != null -> exception.definition
            exception is UpstreamServiceException -> when (exception.status) {
                400, 422 -> BffErrors.GRAPHQL_INPUT_INVALID
                401 -> PlatformErrors.AUTHENTICATION_REQUIRED
                403 -> PlatformErrors.ACCESS_DENIED
                404 -> PlatformErrors.RESOURCE_NOT_FOUND
                409 -> PlatformErrors.RESOURCE_CONFLICT
                429 -> PlatformErrors.SECURITY_RATE_LIMITED
                503 -> BffErrors.UPSTREAM_UNAVAILABLE
                504 -> BffErrors.UPSTREAM_TIMEOUT
                else -> BffErrors.UPSTREAM_PROTOCOL_INVALID
            }
            exception is IllegalArgumentException -> BffErrors.GRAPHQL_INPUT_INVALID
            else -> BffErrors.GRAPHQL_AGGREGATION_FAILED
        }
        return governedResolver.resolve(BffDomainException(definition, cause = exception), environment, RequestIdContext.get())
    }
}
