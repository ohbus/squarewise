package com.subhrodip.squarewise.accounts.auth.session

import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** Persistence port for refresh-token rotation and family revocation. */
interface AuthSessionRepository : JpaRepository<AuthSessionEntity, UUID> {
    /** Revokes every active refresh session owned by an account in one writer update. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AuthSessionEntity s SET s.revokedAt = :revokedAt WHERE s.accountId = :accountId AND s.revokedAt IS NULL")
    fun revokeAllForAccount(@Param("accountId") accountId: UUID, @Param("revokedAt") revokedAt: Instant): Int

    /** Revokes one refresh-token family, including descendants and ancestors. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AuthSessionEntity s SET s.revokedAt = :revokedAt WHERE s.familyId = :familyId AND s.revokedAt IS NULL")
    fun revokeFamily(@Param("familyId") familyId: UUID, @Param("revokedAt") revokedAt: Instant): Int

    /** Atomically marks a refresh session replaced and revoked exactly once. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE AuthSessionEntity s
           SET s.revokedAt = :revokedAt, s.replacedBySessionId = :replacement
         WHERE s.sessionId = :sessionId
           AND s.revokedAt IS NULL
           AND s.expiresAt > :revokedAt
           AND s.absoluteExpiresAt > :revokedAt
        """
    )
    fun rotateIfActive(
        @Param("sessionId") sessionId: UUID,
        @Param("replacement") replacement: UUID,
        @Param("revokedAt") revokedAt: Instant
    ): Int

    /** Finds an auth session by its refresh token HMAC digest. */
    fun findByRefreshTokenDigest(refreshTokenDigest: ByteArray): AuthSessionEntity?

    /**
     * Purges expired or revoked auth sessions older than [cutoff] in bounded batches.
     */
    @Modifying
    @Query(
        value = """
            DELETE FROM auth_sessions
            WHERE session_id IN (
                SELECT session_id FROM auth_sessions
                WHERE (expires_at < :cutoff)
                   OR (revoked_at IS NOT NULL AND revoked_at < :cutoff)
                LIMIT :limit
            )
        """,
        nativeQuery = true
    )
    fun purgeExpiredOrRevokedOlderThan(@Param("cutoff") cutoff: Instant, @Param("limit") limit: Int): Int
}
