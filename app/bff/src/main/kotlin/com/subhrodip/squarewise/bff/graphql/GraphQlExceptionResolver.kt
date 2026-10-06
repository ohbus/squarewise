package com.subhrodip.squarewise.bff.graphql

import com.subhrodip.squarewise.bff.errors.BffGraphQLErrorResolver
import com.subhrodip.squarewise.bff.errors.UpstreamProblemException
import com.subhrodip.squarewise.bff.transport.UpstreamServiceException
import com.subhrodip.squarewise.errors.domain.ApplicationException
import com.subhrodip.squarewise.errors.domain.ErrorCode
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.errors.request.RequestIdContext
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component
import java.util.UUID

/** Maps resolver failures to the public GraphQL error-code contract. */
@Component
class GraphQlExceptionResolver : DataFetcherExceptionResolverAdapter() {
    private val governedResolver = BffGraphQLErrorResolver()

    override fun resolveToSingleError(exception: Throwable, environment: DataFetchingEnvironment): GraphQLError {
        if (exception is UpstreamProblemException || exception is SquarewiseException || exception is java.util.concurrent.TimeoutException) {
            return governedResolver.resolve(exception, environment, RequestIdContext.get())
        }
        val error = classify(exception)
        val extensions = buildMap<String, Any> {
            put("code", error.code)
            put("requestId", UUID.randomUUID().toString())
            if (error.code == "RATE_LIMITED") put("retryAfterSeconds", 60)
        }
        return GraphqlErrorBuilder.newError(environment)
            .message(error.detail)
            .errorType(error.classification)
            .extensions(extensions)
            .build()
    }

    private fun classify(exception: Throwable): PublicGraphQlError {
        val applicationException = exception as? ApplicationException
        if (applicationException != null) {
            return PublicGraphQlError(
                code = applicationException.errorCode.graphQlCode(),
                detail = applicationException.errorCode.safeDetail,
                classification = applicationException.errorCode.graphQlClassification()
            )
        }

        val upstream = exception as? UpstreamServiceException
        if (upstream != null) {
            val errorCode = when (upstream.status) {
                400 -> ErrorCode.ERR_02
                401 -> ErrorCode.ERR_03
                403 -> ErrorCode.ERR_04
                404 -> ErrorCode.ERR_05
                409 -> ErrorCode.ERR_06
                422 -> ErrorCode.ERR_02
                429 -> ErrorCode.ERR_11
                else -> ErrorCode.ERR_01
            }
            return PublicGraphQlError(
                code = errorCode.graphQlCode(),
                detail = errorCode.safeDetail,
                classification = errorCode.graphQlClassification()
            )
        }

        if (exception is IllegalArgumentException) {
            return PublicGraphQlError(
                code = ErrorCode.ERR_02.graphQlCode(),
                detail = ErrorCode.ERR_02.safeDetail,
                classification = ErrorType.BAD_REQUEST
            )
        }

        return PublicGraphQlError(
            code = ErrorCode.ERR_01.graphQlCode(),
            detail = ErrorCode.ERR_01.safeDetail,
            classification = ErrorType.INTERNAL_ERROR
        )
    }

    private data class PublicGraphQlError(
        val code: String,
        val detail: String,
        val classification: ErrorType
    )
}

private fun ErrorCode.graphQlCode(): String = when (this) {
    ErrorCode.ERR_01, ErrorCode.ERR_07, ErrorCode.ERR_08 -> "INTERNAL_ERROR"
    ErrorCode.ERR_02, ErrorCode.ERR_10 -> "VALIDATION_FAILED"
    ErrorCode.ERR_03 -> "UNAUTHENTICATED"
    ErrorCode.ERR_04 -> "FORBIDDEN"
    ErrorCode.ERR_05 -> "NOT_FOUND"
    ErrorCode.ERR_06, ErrorCode.ERR_09 -> "CONFLICT"
    ErrorCode.ERR_11 -> "RATE_LIMITED"
    ErrorCode.ERR_12 -> "GOVERNANCE_COMPLETED"
}

private fun ErrorCode.graphQlClassification(): ErrorType = when (this) {
    ErrorCode.ERR_03 -> ErrorType.UNAUTHORIZED
    ErrorCode.ERR_04 -> ErrorType.FORBIDDEN
    ErrorCode.ERR_05 -> ErrorType.NOT_FOUND
    ErrorCode.ERR_01, ErrorCode.ERR_07, ErrorCode.ERR_08 -> ErrorType.INTERNAL_ERROR
    else -> ErrorType.BAD_REQUEST
}
