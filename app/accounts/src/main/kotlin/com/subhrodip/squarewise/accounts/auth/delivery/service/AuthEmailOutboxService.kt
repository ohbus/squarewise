package com.subhrodip.squarewise.accounts.auth.delivery.service

import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailTemplate
import com.subhrodip.squarewise.accounts.auth.delivery.outbox.AuthEmailOutboxEntity
import com.subhrodip.squarewise.accounts.auth.delivery.outbox.AuthEmailOutboxRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Coordinates durable auth-email outbox append and single-worker leasing. */
@Service
class AuthEmailOutboxService(
    private val repository: AuthEmailOutboxRepository
) {
    /** Appends one delivery event with an idempotent event identifier. */
    @Transactional
    fun append(eventId: UUID, recipient: String, template: AuthEmailTemplate, encryptedCredential: String, expiresAt: Instant, now: Instant): AuthEmailOutboxEntity = repository.save(
        AuthEmailOutboxEntity(UUID.randomUUID(), eventId, recipient, template.name, encryptedCredential, expiresAt, now, now)
    )

    /** Claims the oldest available event for a bounded worker lease. */
    @Transactional
    fun claim(now: Instant, lease: Duration): AuthEmailOutboxEntity? {
        require(!lease.isNegative && !lease.isZero) { "lease must be positive" }
        val record = repository.findAvailableForClaim(now) ?: return null
        record.status = "CLAIMED"
        record.availableAt = now.plus(lease)
        record.attempts += 1
        return repository.save(record)
    }

    /** Marks a claimed event as published; stale or duplicate acknowledgements are ignored. */
    @Transactional
    fun acknowledge(eventId: UUID, now: Instant): Boolean = repository.acknowledge(eventId, now) == 1

    /** Retries a claimed event or parks it when the bounded attempt budget is exhausted. */
    @Transactional
    fun reject(eventId: UUID, now: Instant, retryAfter: Duration, maximumAttempts: Int): Boolean {
        require(!retryAfter.isNegative) { "retryAfter must not be negative" }
        require(maximumAttempts > 0) { "maximumAttempts must be positive" }
        return repository.reject(eventId, maximumAttempts, now.plus(retryAfter)) == 1
    }
}
