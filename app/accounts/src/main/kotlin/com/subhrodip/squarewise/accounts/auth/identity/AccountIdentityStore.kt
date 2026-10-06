package com.subhrodip.squarewise.accounts.auth.identity

import com.subhrodip.squarewise.accounts.errors.AccountsInputException

import java.util.UUID

/**
 * Authoritative port for managing durable account identity mappings.
 *
 * PostgreSQL acts as the single source of truth for identity mappings,
 * decoupled from contact email.
 */
interface AccountIdentityStore {
    /**
     * Finds the current identity for a durable account identifier.
     *
     * @param accountId Local account identifier stored in the session.
     * @return Current identity or `null` when the account is unmapped.
     */
    fun findByAccountId(accountId: UUID): AccountIdentity?

    /**
     * Resolves an identity by natural composite key of issuer and provider subject.
     *
     * @param issuer Identifier of the issuing authority.
     * @param providerSubject Subject claim unique to that authority.
     * @return Current identity or `null`.
     */
    fun findByIssuerAndSubject(issuer: String, providerSubject: String): AccountIdentity? = null

    /**
     * Resolves an active identity by verified user contact email.
     *
     * @param email Normalized user email.
     * @return Current identity or `null`.
     */
    fun findByEmail(email: String): AccountIdentity? = null

    /**
     * Enrolls or links an identity to an account.
     *
     * @param accountId Stable account identifier.
     * @param issuer Trusted authority identifier.
     * @param providerSubject Subject claim unique to that authority.
     * @param email Verified contact email.
     * @param verified Whether the email is verified.
     * @return Enrolled [AccountIdentity].
     */
    fun enrollIdentity(
        accountId: UUID,
        issuer: String,
        providerSubject: String,
        email: String,
        verified: Boolean
    ): AccountIdentity = AccountIdentity(
        accountId = accountId,
        subject = providerSubject,
        email = email,
        deletionRequested = false,
        issuer = issuer
    )

    /**
     * Updates the contact email of an account identity without modifying the account subject.
     *
     * @param accountId Account whose email is being updated.
     * @param email New normalized email.
     * @param verified Verification status of the new email.
     * @return Updated [AccountIdentity].
     */
    fun updateEmail(accountId: UUID, email: String, verified: Boolean): AccountIdentity =
        findByAccountId(accountId) ?: throw AccountsInputException("Account not found")
}
