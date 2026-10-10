package com.subhrodip.squarewise.bff.auth

import com.subhrodip.squarewise.bff.config.BrowserSessionCookies
import com.subhrodip.squarewise.bff.config.BrowserSessionProperties
import com.subhrodip.squarewise.bff.transport.AccountsGateway
import com.subhrodip.squarewise.bff.errors.BffDomainException
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserSessionResponse
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginStartRequest
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginStartResponse
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginVerifyRequest
import jakarta.validation.Valid
import java.security.SecureRandom
import java.util.Base64
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/** BFF-owned browser authentication boundary; refresh credentials never enter a response body. */
@RestController
@RequestMapping("/auth")
class BrowserSessionController(
    private val accounts: AccountsGateway,
    properties: BrowserSessionProperties
) {
    private val sessionProperties = properties
    private val cookies = BrowserSessionCookies(properties)
    private val random = SecureRandom()

    /** Starts the Squarewise-owned passwordless browser login. */
    @PostMapping("/login/start")
    fun start(@Valid @RequestBody request: BrowserLoginStartRequest): Mono<BrowserLoginStartResponse> =
        accounts.startBrowserLogin(request)

    /** Verifies a browser credential and establishes the BFF HttpOnly cookie session. */
    @PostMapping("/login/verify")
    fun verify(
        @Valid @RequestBody request: BrowserLoginVerifyRequest,
        exchange: ServerWebExchange
    ): Mono<BrowserSessionResponse> = accounts.verifyBrowserLogin(request.credential)
        .map { tokens ->
            writeCookies(exchange, tokens.accessToken, tokens.refreshToken)
            BrowserSessionResponse(expiresIn = tokens.expiresIn)
        }

    /** Rotates the refresh cookie after a valid CSRF proof and returns only the access token. */
    @PostMapping("/token/refresh")
    fun refresh(exchange: ServerWebExchange): Mono<BrowserSessionResponse> {
        val refresh = exchange.request.cookies.getFirst(sessionProperties.refreshCookieName)?.value
            ?: return Mono.error(BffDomainException(PlatformErrors.AUTHENTICATION_REQUIRED))
        return accounts.refreshBrowserSession(refresh).map { tokens ->
            writeCookies(exchange, tokens.accessToken, tokens.refreshToken)
            BrowserSessionResponse(expiresIn = tokens.expiresIn)
        }
    }

    /** Rotates once and revokes the resulting family before clearing browser cookies. */
    @PostMapping("/logout")
    fun logout(exchange: ServerWebExchange): Mono<ResponseEntity<Void>> {
        val refresh = exchange.request.cookies.getFirst(sessionProperties.refreshCookieName)?.value
            ?: return Mono.just(clearCookies(exchange))
        return accounts.refreshBrowserSession(refresh)
            .flatMap { tokens -> accounts.logoutBrowserSession(tokens.accessToken, tokens.refreshToken) }
            .then(Mono.fromSupplier { clearCookies(exchange) })
    }

    private fun writeCookies(exchange: ServerWebExchange, access: String, refresh: String) {
        exchange.response.addCookie(cookies.access(access))
        exchange.response.addCookie(cookies.refresh(refresh))
        exchange.response.addCookie(cookies.csrf(generateNonce()))
    }

    private fun clearCookies(exchange: ServerWebExchange): ResponseEntity<Void> {
        exchange.response.addCookie(cookies.clear(sessionProperties.accessCookieName, "/"))
        exchange.response.addCookie(cookies.clear(sessionProperties.refreshCookieName, "/auth"))
        exchange.response.addCookie(cookies.clear(sessionProperties.csrfCookieName, "/auth"))
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    private fun generateNonce(): String = ByteArray(32).also(random::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}
