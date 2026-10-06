package com.subhrodip.squarewise.bff.errors

import com.subhrodip.squarewise.errors.web.ProblemDetailsDto

/** Typed upstream failure retaining the original Problem Details document. */
class UpstreamProblemException(
    val problem: ProblemDetailsDto,
    cause: Throwable? = null,
) : RuntimeException("upstream problem ${problem.errorName ?: problem.code}", cause)
