package com.subhrodip.squarewise.accounts.auth.jwks

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller exposing standard RFC 7517 JWKS and RFC 8414 OpenID Provider metadata.
 *
 * Allows downstream resource servers and API clients to discover signing keys and
 * issuer capabilities without direct database dependencies.
 */
@RestController
class OidcDiscoveryController(
    private val rsaKeyProvider: RsaKeyProvider,
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private val configuredIssuerUri: String
) {

    /**
     * Publishes the RFC 7517 JSON Web Key Set (JWKS) containing active and retiring public keys.
     *
     * @return Map representing the JWKS JSON object.
     */
    @GetMapping(
        value = [
            ApiEndpoints.Accounts.V1.WELL_KNOWN_JWKS,
            ApiEndpoints.Accounts.V1.PATH_JWKS
        ],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun jwks(): Map<String, Any> = rsaKeyProvider.publicJwkSet().toJSONObject()

    /**
     * Publishes RFC 8414 / OpenID Connect discovery metadata.
     *
     * @return Map of standard metadata attributes.
     */
    @GetMapping(
        value = [ApiEndpoints.Accounts.V1.WELL_KNOWN_OPENID_CONFIGURATION],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun openIdConfiguration(): Map<String, Any> {
        val issuer = configuredIssuerUri.ifBlank { "http://localhost:28081" }
        return mapOf(
            "issuer" to issuer,
            "jwks_uri" to "$issuer${ApiEndpoints.Accounts.V1.WELL_KNOWN_JWKS}",
            "response_types_supported" to listOf("token"),
            "subject_types_supported" to listOf("public"),
            "id_token_signing_alg_values_supported" to listOf("RS256"),
            "token_endpoint_auth_signing_alg_values_supported" to listOf("RS256")
        )
    }
}
