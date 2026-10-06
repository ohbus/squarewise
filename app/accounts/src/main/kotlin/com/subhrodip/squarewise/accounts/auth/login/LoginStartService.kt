package com.subhrodip.squarewise.accounts.auth.login
import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailTemplate

import com.subhrodip.squarewise.accounts.auth.audit.SecurityAuditEvent
import com.subhrodip.squarewise.accounts.auth.audit.SecurityAuditLogger
import com.subhrodip.squarewise.accounts.auth.abuse.LoginRateLimitService
import com.subhrodip.squarewise.accounts.auth.abuse.RateLimitStoreUnavailableException
import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialService
import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailMessage
import com.subhrodip.squarewise.accounts.auth.delivery.service.AuthEmailSender
import java.time.Instant
import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.errors.catalog.AccountsErrors

/** Coordinates the low-friction passwordless login-start use case. */
class LoginStartService(
    private val rateLimitService: LoginRateLimitService,
    private val credentialService: LoginCredentialService,
    private val emailSender: AuthEmailSender,
    private val auditLogger: SecurityAuditLogger = SecurityAuditLogger()
) {
    /**
     * Starts login without exposing account existence or delivery details to the
     * caller. A caller that has exhausted the login policy receives the shared
     * structured rate-limit error so clients can apply bounded backoff.
     *
     * @param email raw user input; normalization occurs in the credential/key boundary.
     * @param networkPartition trusted server-derived abuse partition.
     * @param kind link or code delivery mode.
     * @param now request timestamp.
     * @return accepted when the request is admitted by the login policy.
     * @throws AccountsDomainException when the login policy denies the request or its store cannot decide.
     */
    fun start(
        email: String,
        networkPartition: String,
        kind: LoginCredentialService.CredentialKind,
        now: Instant
    ): LoginStartResult {
        val allowed = try {
            rateLimitService.tryAcquire(email, networkPartition, now)
        } catch (exception: RateLimitStoreUnavailableException) {
            throw AccountsDomainException(AccountsErrors.LOGIN_LIMITER_UNAVAILABLE, cause = exception)
        }
        if (!allowed) {
            auditLogger.emit(SecurityAuditEvent.LOGIN_RATE_LIMITED)
            throw AccountsDomainException(AccountsErrors.LOGIN_RATE_LIMITED)
        }

        val credential = runCatching {
            credentialService.issue(email, kind, now)
        }.getOrNull() ?: return LoginStartResult.ACCEPTED

        runCatching {
            emailSender.send(
                AuthEmailMessage(
                    recipient = credential.canonicalEmail,
                    template = kind.toTemplate(),
                    credential = credential.plaintext,
                    expiresAt = credential.expiresAt
                )
            )
        }
        return LoginStartResult.ACCEPTED
    }

    private fun LoginCredentialService.CredentialKind.toTemplate() =
        when (this) {
            LoginCredentialService.CredentialKind.LINK -> AuthEmailTemplate.LOGIN_LINK
            LoginCredentialService.CredentialKind.CODE -> AuthEmailTemplate.LOGIN_CODE
        }
}
