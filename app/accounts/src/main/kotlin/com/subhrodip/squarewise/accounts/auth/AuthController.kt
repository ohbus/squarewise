package com.subhrodip.squarewise.accounts.auth

import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialService
import com.subhrodip.squarewise.accounts.auth.login.LoginStartRequest
import com.subhrodip.squarewise.accounts.auth.login.LoginStartResponse
import com.subhrodip.squarewise.accounts.auth.login.LoginStartService
import com.subhrodip.squarewise.accounts.auth.login.LoginVerifyRequest
import com.subhrodip.squarewise.accounts.auth.login.LoginVerificationService
import com.subhrodip.squarewise.accounts.auth.abuse.ClientAddressResolver
import com.subhrodip.squarewise.accounts.auth.abuse.RateLimitStoreUnavailableException
import com.subhrodip.squarewise.accounts.auth.abuse.RefreshRateLimitService
import com.subhrodip.squarewise.accounts.auth.session.RefreshTokenRequest
import com.subhrodip.squarewise.accounts.auth.session.LogoutRequest
import com.subhrodip.squarewise.accounts.auth.session.TokenResponse
import com.subhrodip.squarewise.accounts.auth.session.TokenSessionService
import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import java.security.Principal
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.HttpHeaders
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Public authentication REST controller for Accounts service.
 *
 * Implements contracted endpoints:
 * - `POST /accounts/v1/auth/login/start`: Starts passwordless login flow.
 * - `POST /accounts/v1/auth/login/verify`: Verifies single-use link/code credential.
 * - `POST /accounts/v1/auth/token/refresh`: Rotates refresh token within its family.
 * - `POST /accounts/v1/auth/logout`: Revokes active session family.
 *
 * @param loginStartService Passwordless login start orchestrator.
 * @param loginVerificationService Credential verification and session creation service.
 * @param tokenSessionService Token session and family lifecycle coordinator.
 * @param refreshRateLimitService Refresh-token rotation rate limiter.
 * @param clientAddressResolver Trusted-proxy-aware client IP resolver for rate-limit partitioning (SEC-007).
 */
@RestController
@RequestMapping(ApiEndpoints.Accounts.V1.BASE)
class AuthController(
    private val loginStartService: LoginStartService,
    private val loginVerificationService: LoginVerificationService,
    private val tokenSessionService: TokenSessionService,
    private val refreshRateLimitService: RefreshRateLimitService,
    private val clientAddressResolver: ClientAddressResolver
) {

    /**
     * Initiates passwordless authentication. An admitted request responds with
     * 202 Accepted; an exhausted login policy responds with structured 429.
     *
     * @param request Validated [LoginStartRequest].
     * @param servletRequest Incoming HTTP servlet request for network partitioning.
     * @return 202 Accepted with [LoginStartResponse] when admitted.
     * @throws AccountsDomainException when the login policy is exhausted.
     */
    @PostMapping(ApiEndpoints.Accounts.V1.LOGIN_START)
    fun startLogin(
        @Valid @RequestBody request: LoginStartRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<LoginStartResponse> {
        val kind = when (request.channel?.uppercase()) {
            "CODE" -> LoginCredentialService.CredentialKind.CODE
            else -> LoginCredentialService.CredentialKind.LINK
        }
        val networkPartition = clientAddressResolver.resolvePartition(servletRequest)

        loginStartService.start(
            email = request.email,
            networkPartition = networkPartition,
            kind = kind,
            now = Instant.now()
        )

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(LoginStartResponse())
    }

    /**
     * Redeems a one-time credential, returning access and refresh tokens.
     *
     * @param request Validated [LoginVerifyRequest].
     * @param servletRequest Incoming HTTP servlet request for client metadata.
     * @return 200 OK with [TokenResponse].
     * @throws AccountsDomainException when verification admission or the refresh admission limit
     * is exhausted or its fail-closed store cannot decide.
     */
    @PostMapping(ApiEndpoints.Accounts.V1.LOGIN_VERIFY)
    fun verifyLogin(
        @Valid @RequestBody request: LoginVerifyRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<TokenResponse> {
        val userAgent = servletRequest.getHeader(ApiEndpoints.Headers.USER_AGENT)
        val tokenResponse = loginVerificationService.verify(
            credential = request.credential,
            clientKind = request.clientKind,
            deviceLabel = userAgent,
            now = Instant.now(),
            networkPartition = clientAddressResolver.resolvePartition(servletRequest)
        )
        return tokenResponse(tokenResponse)
    }

    /**
     * Rotates an active refresh token, returning a new token pair.
     *
     * @param request Validated [RefreshTokenRequest].
     * @param servletRequest Incoming HTTP servlet request for client metadata.
     * @return 200 OK with [TokenResponse].
     */
    @PostMapping(ApiEndpoints.Accounts.V1.TOKEN_REFRESH)
    fun refreshToken(
        @Valid @RequestBody request: RefreshTokenRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<TokenResponse> {
        val userAgent = servletRequest.getHeader(ApiEndpoints.Headers.USER_AGENT)
        val networkPartition = clientAddressResolver.resolvePartition(servletRequest)
        try {
            if (!refreshRateLimitService.tryAcquire(networkPartition, Instant.now())) {
                throw AccountsDomainException(AccountsErrors.REFRESH_RATE_LIMITED)
            }
        } catch (exception: RateLimitStoreUnavailableException) {
            throw AccountsDomainException(AccountsErrors.LOGIN_LIMITER_UNAVAILABLE, cause = exception)
        }
        val tokenResponse = tokenSessionService.rotateSession(
            rawRefreshToken = request.refreshToken,
            deviceLabel = userAgent,
            now = Instant.now()
        )
        return tokenResponse(tokenResponse)
    }

    /**
     * Revokes the active session for an authenticated caller.
     *
     * @param principal Authenticated user principal.
     */
    @PostMapping(ApiEndpoints.Accounts.V1.LOGOUT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        principal: Principal?,
        @Valid @RequestBody request: LogoutRequest
    ) {
        if (principal == null || principal.name.isBlank()) {
            throw AccountsDomainException(AccountsErrors.PROFILE_SUBJECT_INVALID)
        }
        tokenSessionService.revokeSessionByRefreshToken(
            rawRefreshToken = request.refreshToken,
            expectedSubject = principal.name,
            now = Instant.now()
        )
    }

    /** Returns token material with cache directives that prevent intermediary persistence. */
    private fun tokenResponse(response: TokenResponse): ResponseEntity<TokenResponse> =
        ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.PRAGMA, "no-cache")
            .body(response)
}
