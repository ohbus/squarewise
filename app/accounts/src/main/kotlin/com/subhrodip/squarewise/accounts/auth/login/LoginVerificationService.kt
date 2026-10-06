package com.subhrodip.squarewise.accounts.auth.login

import com.subhrodip.squarewise.accounts.auth.audit.SecurityAuditEvent
import com.subhrodip.squarewise.accounts.auth.audit.SecurityAuditLogger
import com.subhrodip.squarewise.accounts.auth.abuse.LoginVerificationRateLimitService
import com.subhrodip.squarewise.accounts.auth.abuse.RateLimitStoreUnavailableException
import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialService
import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentityStore
import com.subhrodip.squarewise.accounts.auth.session.TokenResponse
import com.subhrodip.squarewise.accounts.auth.session.TokenSessionService
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileStore
import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional

/**
 * Service coordinating single-use credential redemption, explicit identity resolution/enrollment,
 * and authenticated session initialization.
 *
 * Implements SEC-002 identity model:
 * 1. Atomically redeems the submitted credential (rejecting expired/replayed/unknown credentials).
 * 2. Resolves or explicitly enrolls the identity via [AccountIdentityStore].
 * 3. Durable identity is (issuer, subject); email is verified contact data, never concatenated to form subject.
 * 4. Initializes a new refresh token family and mints access & refresh tokens via [TokenSessionService].
 *
 * @param credentialService Credential issuance and redemption port.
 * @param profileStore Profile lookup and provisioning port.
 * @param tokenSessionService Session family and token lifecycle coordinator.
 * @param accountIdentityStore Authoritative account identity mapping port.
 * @param issuerUri Authority/issuer identifier for enrolled identities.
 */
open class LoginVerificationService(
    private val credentialService: LoginCredentialService,
    private val profileStore: ProfileStore,
    private val tokenSessionService: TokenSessionService,
    private val accountIdentityStore: AccountIdentityStore,
    private val loginVerificationRateLimitService: LoginVerificationRateLimitService,
    private val issuerUri: String = "squarewise-internal",
    private val auditLogger: SecurityAuditLogger = SecurityAuditLogger()
) {
    private val log = LoggerFactory.getLogger(LoginVerificationService::class.java)

    /**
     * Verifies and redeems a one-time login link or code.
     *
     * @param credential Plaintext credential submitted by the user.
     * @param clientKind Client device/app type ("BROWSER" or "NATIVE").
     * @param deviceLabel Optional client or user-agent label.
     * @param now Current timestamp.
     * @return [TokenResponse] containing issued access and refresh tokens.
     * @throws AccountsDomainException on invalid, expired, or replayed credentials.
     */
    @Transactional
    open fun verify(
        credential: String,
        clientKind: String,
        deviceLabel: String?,
        now: Instant,
        networkPartition: String = "unknown"
    ): TokenResponse {
        try {
            val admitted = loginVerificationRateLimitService.tryAcquire(credential, networkPartition, now)
            if (!admitted) {
                throw AccountsDomainException(AccountsErrors.LOGIN_RATE_LIMITED)
            }
        } catch (exception: RateLimitStoreUnavailableException) {
            throw AccountsDomainException(AccountsErrors.LOGIN_LIMITER_UNAVAILABLE, cause = exception)
        }
        val redeemed = credentialService.redeem(credential, now)
            ?: run {
                auditLogger.emit(SecurityAuditEvent.LOGIN_FAILURE, detail = "invalid-or-expired-credential")
                throw AccountsDomainException(AccountsErrors.LOGIN_CODE_INVALID)
            }

        val canonicalEmail = redeemed.canonicalEmail
        val existingIdentity = accountIdentityStore.findByEmail(canonicalEmail)

        val (accountId, subject) = if (existingIdentity != null) {
            existingIdentity.accountId to existingIdentity.subject
        } else {
            // Explicit enrollment: generate durable accountId and stable provider-qualified subject
            val newAccountId = UUID.randomUUID()
            val newSubject = "sqw:$newAccountId"
            val displayName = canonicalEmail.substringBefore("@").ifBlank { "User" }

            profileStore.create(
                accountId = newAccountId,
                subject = newSubject,
                displayName = displayName,
                timezone = "UTC",
                defaultCurrency = "EUR"
            )
            accountIdentityStore.enrollIdentity(
                accountId = newAccountId,
                issuer = issuerUri,
                providerSubject = newSubject,
                email = canonicalEmail,
                verified = true
            )
            auditLogger.emit(SecurityAuditEvent.IDENTITY_ENROLLED, accountId = newAccountId)
            newAccountId to newSubject
        }

        log.info("Successfully authenticated credential for accountId={}", accountId)

        return tokenSessionService.createSession(
            accountId = accountId,
            subject = subject,
            email = canonicalEmail,
            clientKind = clientKind,
            deviceLabel = deviceLabel,
            now = now
        )
    }
}
