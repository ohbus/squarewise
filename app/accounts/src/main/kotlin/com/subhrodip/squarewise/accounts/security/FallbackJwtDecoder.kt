package com.subhrodip.squarewise.accounts.security

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException

/**
 * Attempts a bounded set of JWT decoders in order and returns the first valid token.
 *
 * This is used only by the local Keycloak fixture, where CI personas are signed by
 * Keycloak while passwordless tokens are signed by Accounts. Production and staging
 * use the Accounts-owned decoder directly.
 */
class FallbackJwtDecoder(
    private val decoders: List<JwtDecoder>
) : JwtDecoder {
    init {
        require(decoders.isNotEmpty()) { "At least one JWT decoder is required" }
    }

    /**
     * Decodes with each configured authority and fails with the final validation error.
     *
     * @param token compact serialized JWT.
     * @return validated JWT from the first accepting decoder.
     * @throws JwtException when no configured decoder accepts the token.
     */
    override fun decode(token: String): Jwt {
        var lastFailure: JwtException = JwtException("No configured JWT decoder accepted the token")
        for (decoder in decoders) {
            try {
                return decoder.decode(token)
            } catch (failure: JwtException) {
                lastFailure = failure
            }
        }
        throw lastFailure
    }
}
