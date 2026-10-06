package com.subhrodip.squarewise.accounts.auth.login
import java.time.Duration

import com.subhrodip.squarewise.accounts.auth.credential.HmacCredentialDigest
import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialRepository
import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialService
import com.subhrodip.squarewise.accounts.auth.credential.OneTimeCredentialIssuer
import com.subhrodip.squarewise.accounts.auth.abuse.LoginVerificationRateLimitService
import com.subhrodip.squarewise.security.ratelimit.RateLimitDecision
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import com.subhrodip.squarewise.accounts.auth.provider.InternalJwtTokenProvider
import com.subhrodip.squarewise.accounts.auth.session.AuthSessionRepository
import com.subhrodip.squarewise.accounts.auth.session.TokenSessionService
import com.subhrodip.squarewise.accounts.profile.persistence.InMemoryProfileStore
import com.subhrodip.squarewise.errors.domain.ApplicationException
import com.subhrodip.squarewise.errors.domain.ErrorCode
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class LoginVerificationServiceTest @Autowired constructor(
    private val credentialRepository: LoginCredentialRepository,
    private val sessionRepository: AuthSessionRepository
) {
    private val secret = ByteArray(32) { it.toByte() }
    private val digest = HmacCredentialDigest(secret)
    private val issuer = OneTimeCredentialIssuer(digest)
    private val credentialService = LoginCredentialService(credentialRepository, issuer)
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
    private val service = LoginVerificationService(
        credentialService = credentialService,
        profileStore = profileStore,
        tokenSessionService = tokenSessionService,
        accountIdentityStore = profileStore,
        loginVerificationRateLimitService = verificationRateLimitService
    )

    @Test
    fun `redeems issued credential successfully and provisions profile`() {
        val now = Instant.now()
        val issued = credentialService.issue(
            email = "login@example.com",
            kind = LoginCredentialService.CredentialKind.LINK,
            now = now
        )

        val tokens = service.verify(
            credential = issued.plaintext,
            clientKind = "BROWSER",
            deviceLabel = "test",
            now = now.plusSeconds(5)
        )

        assertNotNull(tokens.accessToken)
        assertNotNull(tokens.refreshToken)

        val identity = profileStore.findByEmail("login@example.com")
        assertNotNull(identity)
        val profile = profileStore.get(identity!!.subject)
        assertNotNull(profile)
        assertEquals("login", profile!!.displayName)
        assertEquals(identity.accountId, profile.accountId)
    }

    @Test
    fun `fails closed when credential is invalid or already consumed`() {
        val now = Instant.now()
        val issued = credentialService.issue(
            email = "once@example.com",
            kind = LoginCredentialService.CredentialKind.CODE,
            now = now
        )

        // First redemption succeeds
        service.verify(issued.plaintext, "NATIVE", null, now.plusSeconds(1))

        // Second redemption fails closed
        val ex = assertThrows(ApplicationException::class.java) {
            service.verify(issued.plaintext, "NATIVE", null, now.plusSeconds(2))
        }
        assertEquals(ErrorCode.ERR_03, ex.errorCode)
    }

    @Test
    fun `reuses an existing identity without provisioning another account`() {
        val now = Instant.now()
        val accountId = UUID.randomUUID()
        val subject = "sqw:existing-$accountId"
        profileStore.create(accountId, subject, "Existing User", "UTC", "EUR")
        profileStore.enrollIdentity(
            accountId = accountId,
            issuer = "squarewise-internal",
            providerSubject = subject,
            email = "existing@example.com",
            verified = true,
        )
        val issued = credentialService.issue(
            email = "existing@example.com",
            kind = LoginCredentialService.CredentialKind.LINK,
            now = now,
        )

        val tokens = service.verify(issued.plaintext, "BROWSER", "existing-device", now.plusSeconds(1))

        assertNotNull(tokens.accessToken)
        assertEquals(accountId, profileStore.findByEmail("existing@example.com")?.accountId)
        assertEquals(subject, profileStore.findByEmail("existing@example.com")?.subject)
        assertEquals("Existing User", profileStore.get(subject)?.displayName)
    }
}
