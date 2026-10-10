@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.bff.transport

import com.subhrodip.squarewise.bff.transport.model.output.BffProfile
import com.subhrodip.squarewise.bff.transport.model.auth.AccountsTokenResponse
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginStartRequest
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginStartResponse
import com.subhrodip.squarewise.bff.transport.BffGatewayFilters
import com.subhrodip.squarewise.bff.errors.UpstreamProblemDecoder
import com.subhrodip.squarewise.bff.errors.toUpstreamException
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import com.fasterxml.jackson.databind.ObjectMapper

/** REST gateway for authenticated Accounts profile calls. */
@Component
class AccountsGateway(
    builder: WebClient.Builder,
    @Value("${'$'}{squarewise.accounts-url}") baseUrl: String,
    @Value("${'$'}{squarewise.bff.upstream-timeout:2s}") private val timeout: Duration
) {
    private val client = builder.filter(BffGatewayFilters.bearerPropagation).baseUrl(baseUrl).build()
    private val upstreamProblemDecoder = UpstreamProblemDecoder(ObjectMapper())

    fun getMe(bearer: String?): Mono<BffProfile> =
        client.get().uri(ApiEndpoints.Accounts.V1.PATH_ME)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }
            .retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, "accounts") }
            .bodyToMono(BffProfile::class.java)
            .timeout(timeout)

    /** Starts a browser passwordless login without exposing Accounts directly to the browser. */
    fun startBrowserLogin(request: BrowserLoginStartRequest): Mono<BrowserLoginStartResponse> =
        client.post().uri(ApiEndpoints.Accounts.V1.PATH_LOGIN_START)
            .bodyValue(request)
            .retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, "accounts") }
            .bodyToMono(BrowserLoginStartResponse::class.java)
            .timeout(timeout)

    /** Verifies a browser credential and keeps the returned refresh token inside the BFF. */
    fun verifyBrowserLogin(credential: String): Mono<AccountsTokenResponse> =
        client.post().uri(ApiEndpoints.Accounts.V1.PATH_LOGIN_VERIFY)
            .bodyValue(mapOf("credential" to credential, "clientKind" to "BROWSER"))
            .retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, "accounts") }
            .bodyToMono(AccountsTokenResponse::class.java)
            .timeout(timeout)

    /** Rotates a browser refresh token received from the HttpOnly cookie. */
    fun refreshBrowserSession(refreshToken: String): Mono<AccountsTokenResponse> =
        client.post().uri(ApiEndpoints.Accounts.V1.PATH_TOKEN_REFRESH)
            .bodyValue(mapOf("refreshToken" to refreshToken))
            .retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, "accounts") }
            .bodyToMono(AccountsTokenResponse::class.java)
            .timeout(timeout)

    /** Revokes the browser session family using the current BFF access token. */
    fun logoutBrowserSession(accessToken: String, refreshToken: String): Mono<Void> =
        client.post().uri(ApiEndpoints.Accounts.V1.PATH_LOGOUT)
            .headers { headers -> headers.setBearerAuth(accessToken) }
            .bodyValue(mapOf("refreshToken" to refreshToken))
            .retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, "accounts") }
            .toBodilessEntity()
            .then()
}
