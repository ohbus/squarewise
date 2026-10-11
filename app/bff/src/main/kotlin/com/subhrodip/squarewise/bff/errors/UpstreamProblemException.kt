package com.subhrodip.squarewise.bff.errors

import com.subhrodip.squarewise.bff.transport.UpstreamServiceException
import com.subhrodip.squarewise.errors.catalog.ErrorCatalog
import com.subhrodip.squarewise.errors.web.ProblemDetailsDto

/** Typed upstream failure retaining the original Problem Details document. */
class UpstreamProblemException(
    val problem: ProblemDetailsDto,
    cause: Throwable? = null,
) : UpstreamServiceException(
    status = problem.status,
    message = "Upstream problem ${problem.errorName}",
    definition = ErrorCatalog.find(problem.numericCode),
    cause = cause,
)
