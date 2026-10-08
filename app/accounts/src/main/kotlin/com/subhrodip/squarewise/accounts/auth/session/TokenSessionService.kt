package com.subhrodip.squarewise.accounts.auth.session

import java.time.Duration

import com.subhrodip.squarewise.accounts.auth.audit.SecurityAuditEvent
import com.subhrodip.squarewise.accounts.auth.audit.SecurityAuditLogger
import com.subhrodip.squarewise.accounts.auth.credential.CredentialDigest
import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentityStore
import com.subhrodip.squarewise.accounts.auth.provider.IdentityProviderPort
import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional

/**
 * Coordinates token issuance, refresh token rotation, and family-wide revocation.
 *
 * Implements strict OAuth 2.0 refresh token family tracking and reuse detection:
 * - Each verify login creates a new refresh token family.
 * - Refresh operations atomically consume the old token and issue a child session in the same family.
 * - If an already-rotated (or revoked) refresh token is presented, the entire family is immediately revoked
 *   and an unauthorized exception is raised (reuse detection).
 *
 * @param sessionRepository Auth session persistence port.
 * @param identityProviderPort Provider-neutral access token minting port.
 * @param credentialDigest HMAC digest generator for hashing refresh tokens at rest.
 * @param random Cryptographically secure random source for opaque refresh tokens.
 * @param sessionPolicy Validated access-token and refresh-session timing policy.
 * @param accountIdentityStore Writer-authoritative account identity lookup.
 */
open class TokenSessionService(
    private val sessionRepository: AuthSessionRepository,
    private val identityProviderPort: IdentityProviderPort,
    private val credentialDigest: CredentialDigest,
    private val random: SecureRandom = SecureRandom(),
    private val sessionPolicy: SessionPolicy = SessionPolicy(
        accessTokenLifetime = Duration.ofMinutes(10),
        refreshIdleLifetime = Duration.ofDays(30),
        absoluteSessionLifetime = Duration.ofDays(90),
        clockSkew = Duration.ZERO
    ),
    private val accountIdentityStore: AccountIdentityStore,
    private val auditLogger: SecurityAuditLogger = SecurityAuditLogger()
) {
    private val log = LoggerFactory.getLogger(TokenSessionService::class.java)

    /**
     * Creates an initial session family and issues both access and refresh tokens.
     *
     * @param accountId Stable account identifier UUID.
     * @param subject Canonical identity subject string.
     * @param email Canonical normalized user email.
     * @param deviceLabel Optional user-agent or client label.
     * @param now Current timestamp.
     * @return [TokenResponse] containing signed access token and new opaque refresh token.
     */
    @Transactional
    open fun createSession(
        accountId: UUID,
        subject: String,
        email: String,
        clientKind: String,
        deviceLabel: String?,
        now: Instant
    ): TokenResponse {
        val familyId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val rawRefreshToken = generateOpaqueToken()
        val digest = credentialDigest.digest(rawRefreshToken)
        val expiry = sessionPolicy.initialExpiry(now)

        val session = AuthSessionEntity(
            sessionId = sessionId,
            accountId = accountId,
            subject = subject,
            familyId = familyId,
            refreshTokenDigest = digest,
            createdAt = now,
            lastUsedAt = now,
            expiresAt = expiry.idleExpiresAt,
            absoluteExpiresAt = expiry.absoluteExpiresAt,
            revokedAt = null,
            replacedBySessionId = null,
            deviceLabel = deviceLabel?.take(MAX_DEVICE_LABEL_LENGTH),
            clientKind = clientKind
        )
        sessionRepository.save(session)

        val issuedToken = identityProviderPort.issueAccessToken(accountId, subject, email)
        auditLogger.emit(SecurityAuditEvent.LOGIN_SUCCESS, accountId = accountId)
        return TokenResponse(
            accessToken = issuedToken.accessToken,
            tokenType = issuedToken.tokenType,
            expiresIn = issuedToken.expiresIn,
            refreshToken = rawRefreshToken
        )
    }

    /**
     * Rotates a refresh token atomically within its family or detects token reuse.
     *
     * @param rawRefreshToken Presented opaque refresh token.
     * @param deviceLabel Optional user-agent or client label.
     * @param now Current timestamp.
     * @return [TokenResponse] with fresh access token and child refresh token.
     * @throws AccountsDomainException when the refresh token is invalid, expired, revoked, or reused.
     */
    @Transactional(noRollbackFor = [AccountsDomainException::class])
    open fun rotateSession(
        rawRefreshToken: String,
        deviceLabel: String?,
        now: Instant
    ): TokenResponse {
        if (rawRefreshToken.isBlank()) {
            throw AccountsDomainException(AccountsErrors.REFRESH_TOKEN_INVALID)
        }

        val digest = credentialDigest.digest(rawRefreshToken)
        val existingSession = sessionRepository.findByRefreshTokenDigest(digest)
            ?: throw AccountsDomainException(AccountsErrors.REFRESH_TOKEN_INVALID)

        // Reuse detection: if this session was already replaced or revoked, revoke entire family
        if (existingSession.revokedAt != null || existingSession.replacedBySessionId != null) {
            log.warn("Refresh token reuse detected for session family {}. Revoking family.", existingSession.familyId)
            auditLogger.emit(SecurityAuditEvent.TOKEN_REUSE_DETECTED, accountId = existingSession.accountId)
            sessionRepository.revokeFamily(existingSession.familyId, now)
            throw AccountsDomainException(AccountsErrors.SESSION_EXPIRED)
        }

        val existingExpiry = SessionExpiry(
            idleExpiresAt = existingSession.expiresAt,
            absoluteExpiresAt = existingSession.absoluteExpiresAt
        )
        if (sessionPolicy.isExpired(existingExpiry, now)) {
            sessionRepository.revokeFamily(existingSession.familyId, now)
            throw AccountsDomainException(AccountsErrors.SESSION_REVOKED)
        }

        val newSessionId = UUID.randomUUID()
        val newRawRefreshToken = generateOpaqueToken()
        val newDigest = credentialDigest.digest(newRawRefreshToken)
        val expiry = sessionPolicy.refreshedExpiry(now, existingSession.absoluteExpiresAt)

        val accountId = existingSession.accountId
            ?: throw AccountsDomainException(AccountsErrors.REFRESH_TOKEN_INVALID)
        val identity = accountIdentityStore.findByAccountId(accountId)
            ?: throw AccountsDomainException(AccountsErrors.REFRESH_TOKEN_INVALID)
        if (identity.deletionRequested) {
            auditLogger.emit(SecurityAuditEvent.SESSION_DENIED_DELETION_REQUESTED, accountId = accountId)
            sessionRepository.revokeFamily(existingSession.familyId, now)
            throw AccountsDomainException(AccountsErrors.REFRESH_REPLAY_DETECTED)
        }
        if (existingSession.subject == null || existingSession.subject != identity.subject) {
            auditLogger.emit(SecurityAuditEvent.SESSION_SUBJECT_MISMATCH, accountId = accountId)
            sessionRepository.revokeFamily(existingSession.familyId, now)
            throw AccountsDomainException(AccountsErrors.SESSION_SUBJECT_MISMATCH)
        }

        val newSession = AuthSessionEntity(
            sessionId = newSessionId,
            accountId = accountId,
            subject = existingSession.subject,
            familyId = existingSession.familyId,
            refreshTokenDigest = newDigest,
            createdAt = now,
            lastUsedAt = now,
            expiresAt = expiry.idleExpiresAt,
            absoluteExpiresAt = expiry.absoluteExpiresAt,
            revokedAt = null,
            replacedBySessionId = null,
            deviceLabel = deviceLabel?.take(MAX_DEVICE_LABEL_LENGTH) ?: existingSession.deviceLabel,
            clientKind = existingSession.clientKind
        )
        sessionRepository.save(newSession)

        val updatedCount = sessionRepository.rotateIfActive(existingSession.sessionId, newSessionId, now)
        if (updatedCount != 1) {
            // Concurrent race or collision: fail closed and revoke family
            sessionRepository.revokeFamily(existingSession.familyId, now)
            throw AccountsDomainException(AccountsErrors.SESSION_EXPIRED)
        }

        val issuedToken = identityProviderPort.issueAccessToken(accountId, identity.subject, identity.email)
        auditLogger.emit(SecurityAuditEvent.TOKEN_REFRESHED, accountId = accountId)
        return TokenResponse(
            accessToken = issuedToken.accessToken,
            tokenType = issuedToken.tokenType,
            expiresIn = issuedToken.expiresIn,
            refreshToken = newRawRefreshToken
        )
    }

    /**
     * Revokes all active sessions in the family associated with the given refresh token.
     *
     * @param rawRefreshToken Refresh token to revoke.
     * @param expectedSubject Authenticated subject that must own the token, or null for
     *        provider-independent internal revocation flows.
     * @param now Revocation timestamp.
     */
    @Transactional
    open fun revokeSessionByRefreshToken(
        rawRefreshToken: String,
        expectedSubject: String? = null,
        now: Instant
    ) {
        if (rawRefreshToken.isBlank()) return
        val digest = credentialDigest.digest(rawRefreshToken)
        val session = sessionRepository.findByRefreshTokenDigest(digest) ?: return
        if (expectedSubject != null) {
            val accountId = session.accountId ?: return
            val identity = accountIdentityStore.findByAccountId(accountId) ?: return
            if (identity.subject != expectedSubject) return
        }
        auditLogger.emit(SecurityAuditEvent.SESSION_REVOKED, accountId = session.accountId)
        sessionRepository.revokeFamily(session.familyId, now)
    }

    /**
     * Revokes all active sessions in a family directly by family ID.
     *
     * @param familyId Unique family UUID.
     * @param now Revocation timestamp.
     */
    @Transactional
    open fun revokeFamily(familyId: UUID, now: Instant) {
        sessionRepository.revokeFamily(familyId, now)
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTE_LENGTH).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val REFRESH_TOKEN_BYTE_LENGTH = 48
        const val MAX_DEVICE_LABEL_LENGTH = 120
    }
}
