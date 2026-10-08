package com.subhrodip.squarewise.accounts.auth
import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailDeliveryResult
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertNotNull

import com.subhrodip.squarewise.accounts.auth.abuse.ClientAddressResolver
import com.subhrodip.squarewise.accounts.auth.credential.HmacCredentialDigest
import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialRepository
import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialService
import com.subhrodip.squarewise.accounts.auth.credential.OneTimeCredentialIssuer
import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailMessage
import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailTemplate
import com.subhrodip.squarewise.accounts.auth.delivery.service.AuthEmailSender
import com.subhrodip.squarewise.accounts.auth.abuse.LoginRateLimitKeyDeriver
import com.subhrodip.squarewise.accounts.auth.abuse.LoginRateLimitService
import com.subhrodip.squarewise.accounts.auth.abuse.RefreshRateLimitService
import com.subhrodip.squarewise.accounts.auth.abuse.LoginVerificationRateLimitService
import com.subhrodip.squarewise.accounts.auth.abuse.TestRateLimitBucketStore
import com.subhrodip.squarewise.accounts.auth.login.LoginStartService
import com.subhrodip.squarewise.accounts.auth.login.LoginVerificationService
import com.subhrodip.squarewise.accounts.auth.provider.InternalJwtTokenProvider
import com.subhrodip.squarewise.accounts.auth.session.AuthSessionRepository
import com.subhrodip.squarewise.accounts.auth.session.TokenSessionService
import com.subhrodip.squarewise.accounts.auth.session.TokenResponse
import com.subhrodip.squarewise.accounts.profile.persistence.InMemoryProfileStore
import com.subhrodip.squarewise.security.ratelimit.RateLimitDecision
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import com.subhrodip.squarewise.errors.http.GlobalErrorHandler
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import java.security.Principal
import java.time.Instant
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.junit.jupiter.api.Assertions.assertEquals

@SpringBootTest
@Transactional
class AuthControllerTest @Autowired constructor(
    private val credentialRepository: LoginCredentialRepository,
    private val sessionRepository: AuthSessionRepository,
    private val bucketStore: TestRateLimitBucketStore
) {
    private val secret = ByteArray(32) { it.toByte() }
    private val digest = HmacCredentialDigest(secret)
    private val issuer = OneTimeCredentialIssuer(digest)
    private val credentialService = LoginCredentialService(credentialRepository, issuer)
    private val keyDeriver = LoginRateLimitKeyDeriver(digest)
    private val rateLimitService = LoginRateLimitService(keyDeriver, bucketStore)
    private val refreshRateLimitService = RefreshRateLimitService(digest, bucketStore, maximumRequests = 1)
    private val sentEmails = mutableListOf<AuthEmailMessage>()
    private val emailSender = AuthEmailSender {
        sentEmails.add(it)
        AuthEmailDeliveryResult.QUEUED
    }
    private val startService = LoginStartService(rateLimitService, credentialService, emailSender)

    private val profileStore = InMemoryProfileStore()
    private val tokenProvider = InternalJwtTokenProvider(
        secretSigningKey = secret,
        issuerUri = "https://issuer.example.squarewise",
        audience = "squarewise-api",
        tokenLifetime = Duration.ofMinutes(10)
    )
    private val tokenSessionService = TokenSessionService(
        sessionRepository = sessionRepository,
        identityProviderPort = tokenProvider,
        credentialDigest = digest,
        accountIdentityStore = profileStore
    )
    private val verificationRateLimitService = LoginVerificationRateLimitService(
        digest = digest,
        rateLimiter = object : RateLimiter {
            override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision =
                RateLimitDecision(true, policy.maximumPermits - 1, Duration.ZERO, policy.id)
        }
    )
    private val verificationService = LoginVerificationService(
        credentialService = credentialService,
        profileStore = profileStore,
        tokenSessionService = tokenSessionService,
        accountIdentityStore = profileStore,
        loginVerificationRateLimitService = verificationRateLimitService
    )

    // No trusted proxies in tests: raw socket address is always used.
    private val clientAddressResolver = ClientAddressResolver()

    private val controller = AuthController(
        loginStartService = startService,
        loginVerificationService = verificationService,
        tokenSessionService = tokenSessionService,
        refreshRateLimitService = refreshRateLimitService,
        clientAddressResolver = clientAddressResolver
    )

    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(GlobalErrorHandler())
        .build()

    private val alice = RequestPostProcessor { request ->
        request.userPrincipal = Principal { "internal:alice@example.com" }
        request
    }

    @Test
    fun `startLogin accepts valid email and returns 202 Accepted`() {
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_LOGIN_START)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"alice@example.com\",\"channel\":\"LINK\",\"clientKind\":\"BROWSER\"}")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
    }

    @Test
    fun `startLogin selects the code credential channel`() {
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_LOGIN_START)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"code@example.com\",\"channel\":\"CODE\",\"clientKind\":\"BROWSER\"}")
        )
            .andExpect(status().isAccepted)

        assertEquals(AuthEmailTemplate.LOGIN_CODE, sentEmails.single().template)
    }

    @Test
    fun `startLogin defaults a missing channel to a link credential`() {
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_LOGIN_START)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"default-channel@example.com\",\"clientKind\":\"BROWSER\"}")
        )
            .andExpect(status().isAccepted)

        assertEquals(AuthEmailTemplate.LOGIN_LINK, sentEmails.single().template)
    }

    @Test
    fun `startLogin returns 422 Unprocessable Entity on invalid email`() {
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_LOGIN_START)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `startLogin returns structured 429 when login rate limit is reached`() {
        val request = post(ApiEndpoints.Accounts.V1.PATH_LOGIN_START)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"limited@example.com\",\"channel\":\"LINK\",\"clientKind\":\"BROWSER\"}")

        mvc.perform(request).andExpect(status().isAccepted)

        mvc.perform(
            request
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().string("Retry-After", "60"))
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.status").value(429))
    }

    @Test
    fun `verifyLogin redeems valid credential and returns 200 OK with TokenResponse`() {
        val issued = credentialService.issue(
            email = "verify@example.com",
            kind = LoginCredentialService.CredentialKind.LINK,
            now = Instant.now()
        )

        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_LOGIN_VERIFY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"${issued.plaintext}\",\"clientKind\":\"BROWSER\"}")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Pragma", "no-cache"))
            .andExpect(jsonPath("$.accessToken").isString)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").isNumber)
            .andExpect(jsonPath("$.refreshToken").isString)
    }

    @Test
    fun `verifyLogin returns 401 Unauthorized on invalid credential`() {
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_LOGIN_VERIFY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"bogus-token\",\"clientKind\":\"BROWSER\"}")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refreshToken rotates valid refresh token and returns 200 OK`() {
        val issued = credentialService.issue(
            email = "rotator@example.com",
            kind = LoginCredentialService.CredentialKind.CODE,
            now = Instant.now()
        )
        val initialSession = verificationService.verify(issued.plaintext, "BROWSER", null, Instant.now())

        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_TOKEN_REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"${initialSession.refreshToken}\"}")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Pragma", "no-cache"))
            .andExpect(jsonPath("$.accessToken").isString)
            .andExpect(jsonPath("$.refreshToken").isString)
    }

    @Test
    fun `refreshToken returns structured 429 when refresh rate limit is reached`() {
        val issued = credentialService.issue(
            email = "refresh-limited@example.com",
            kind = LoginCredentialService.CredentialKind.CODE,
            now = Instant.now()
        )
        val initialSession = verificationService.verify(issued.plaintext, "BROWSER", null, Instant.now())
        val request = post(ApiEndpoints.Accounts.V1.PATH_TOKEN_REFRESH)
            .with(RequestPostProcessor { servletRequest ->
                servletRequest.remoteAddr = "10.44.0.1"
                servletRequest
            })
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"${initialSession.refreshToken}\"}")

        mvc.perform(request).andExpect(status().isOk)

        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_TOKEN_REFRESH)
                .with(RequestPostProcessor { servletRequest ->
                    servletRequest.remoteAddr = "10.44.0.1"
                    servletRequest
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"${initialSession.refreshToken}\"}")
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().string("Retry-After", "60"))
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
    }

    @Test
    fun `refreshToken returns 401 Unauthorized on invalid or replayed refresh token`() {
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_TOKEN_REFRESH)
                .with(RequestPostProcessor { servletRequest ->
                    servletRequest.remoteAddr = "10.55.0.1"
                    servletRequest
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"unknown-refresh-token\"}")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `logout returns 204 No Content for authenticated user`() {
        val issued = credentialService.issue(
            email = "alice@example.com",
            kind = LoginCredentialService.CredentialKind.CODE,
            now = Instant.now()
        )
        val session = verificationService.verify(issued.plaintext, "BROWSER", null, Instant.now())
        val userPrincipal = RequestPostProcessor { request ->
            val identity = profileStore.findByEmail("alice@example.com")!!
            request.userPrincipal = Principal { identity.subject }
            request
        }

        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_LOGOUT)
                .with(userPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"${session.refreshToken}\"}")
        )
            .andExpect(status().isNoContent)

        val stored = sessionRepository.findByRefreshTokenDigest(digest.digest(session.refreshToken))
        assertNotNull(stored?.revokedAt)
    }

    @Test
    fun `logout returns 401 Unauthorized when unauthenticated`() {
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_LOGOUT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"unknown\"}")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `token response defaults the authorization scheme to bearer`() {
        val response = TokenResponse(
            accessToken = "access",
            expiresIn = 600,
            refreshToken = "refresh"
        )

        assertEquals("Bearer", response.tokenType)
    }

    @Test
    fun `logout returns 401 Unauthorized when principal name is blank`() {
        val blankPrincipal = RequestPostProcessor { request ->
            request.userPrincipal = Principal { " " }
            request
        }

        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_LOGOUT)
                .with(blankPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"unknown\"}")
        )
            .andExpect(status().isUnauthorized)
    }
}
