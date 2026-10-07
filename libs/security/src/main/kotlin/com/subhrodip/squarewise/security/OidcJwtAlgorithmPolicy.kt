package com.subhrodip.squarewise.security

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.security.errors.PlatformDomainException

/** Rejects JWTs whose declared signing algorithm is outside deployment policy. */
class OidcJwtAlgorithmPolicy(
    allowedAlgorithms: Set<String>
) : OAuth2TokenValidator<Jwt> {
    private val allowed: Set<String> = allowedAlgorithms.map(String::trim).filter(String::isNotEmpty).toSet()

    init {
        if (allowed.isEmpty()) {
            throw PlatformDomainException(
                PlatformErrors.PLATFORM_CONFIGURATION_INVALID,
                OidcSecurityConstants.SIGNING_ALGORITHM_REQUIRED_MESSAGE
            )
        }
        if (allowed.any { it.startsWith(OidcSecurityConstants.SYMMETRIC_ALGORITHM_PREFIX) }) {
            throw PlatformDomainException(
                PlatformErrors.PLATFORM_CONFIGURATION_INVALID,
                OidcSecurityConstants.SYMMETRIC_SIGNING_UNSUPPORTED_MESSAGE
            )
        }
    }

    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val algorithm = token.headers[OidcSecurityConstants.ALGORITHM_HEADER] as? String
        return if (algorithm != null && algorithm in allowed) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(
                OAuth2Error(
                    OidcSecurityConstants.INVALID_TOKEN_ERROR_CODE,
                    OidcSecurityConstants.ALGORITHM_REJECTED_DESCRIPTION,
                    null
                )
            )
        }
    }
}
