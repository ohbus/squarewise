package com.subhrodip.squarewise.accounts.auth.provider

import java.util.UUID

/**
 * Provider-neutral identity SPI for minting access tokens.
 *
 * Implementations isolate the application domain from whether tokens are generated
 * by an internal cryptographic signer (RFC 7519 / Nimbus) or an external OIDC provider
 * (such as Keycloak, Auth0, or Okta token exchange).
 */
fun interface IdentityProviderPort {
    /**
     * Issues an access token for the given authenticated subject.
     *
     * @param accountId Stable account identifier UUID.
     * @param subject Canonical subject claim (e.g., "squarewise|uuid" or OIDC subject).
     * @param email Canonical normalized user email.
     * @return [IssuedToken] containing signed JWT string, scheme, and lifetime in seconds.
     */
    fun issueAccessToken(accountId: UUID, subject: String, email: String): IssuedToken
}
