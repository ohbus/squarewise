package com.subhrodip.squarewise.accounts.auth.credential

import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** Persistence port for atomic one-time credential state transitions. */
interface LoginCredentialRepository : JpaRepository<LoginCredentialEntity, UUID> {
    /**
     * Consumes an active credential exactly once.
     *
     * @return one when this caller won the redemption race, otherwise zero.
     */
    @Modifying
    @Query(
        """
        UPDATE LoginCredentialEntity c
           SET c.consumedAt = :consumedAt
         WHERE c.credentialDigest = :digest
           AND c.consumedAt IS NULL
           AND c.expiresAt > :consumedAt
           AND c.remainingAttempts > 0
        """
    )
    fun consumeIfActive(
        @Param("digest") digest: ByteArray,
        @Param("consumedAt") consumedAt: Instant
    ): Int

    /** Decrements attempts only while a credential remains active and unused. */
    @Modifying
    @Query(
        """
        UPDATE LoginCredentialEntity c
           SET c.remainingAttempts = c.remainingAttempts - 1
         WHERE c.credentialDigest = :digest
           AND c.consumedAt IS NULL
           AND c.expiresAt > :now
           AND c.remainingAttempts > 0
        """
    )
    fun decrementAttemptIfActive(
        @Param("digest") digest: ByteArray,
        @Param("now") now: Instant
    ): Int

    /** Finds a credential record by its HMAC digest. */
    fun findByCredentialDigest(credentialDigest: ByteArray): LoginCredentialEntity?

    /**
     * Purges expired or consumed credentials older than the specified cutoff thresholds in bounded batches.
     *
     * @param expiredCutoff cutoff timestamp for expired credentials.
     * @param consumedCutoff cutoff timestamp for consumed credentials.
     * @param limit maximum records to delete in one batch.
     * @return count of deleted records.
     */
    @Modifying
    @Query(
        value = """
            DELETE FROM auth_login_credentials
            WHERE credential_id IN (
                SELECT credential_id FROM auth_login_credentials
                WHERE (expires_at < :expiredCutoff)
                   OR (consumed_at IS NOT NULL AND consumed_at < :consumedCutoff)
                LIMIT :limit
            )
        """,
        nativeQuery = true
    )
    fun purgeExpiredOrConsumed(
        @Param("expiredCutoff") expiredCutoff: Instant,
        @Param("consumedCutoff") consumedCutoff: Instant,
        @Param("limit") limit: Int
    ): Int
}
