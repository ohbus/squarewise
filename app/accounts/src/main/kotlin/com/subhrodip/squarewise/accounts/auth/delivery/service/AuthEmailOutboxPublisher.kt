package com.subhrodip.squarewise.accounts.auth.delivery.service

import java.util.Date

import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailPublishOutcome
import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import tools.jackson.databind.ObjectMapper

/** Publishes protected authentication-email events and advances outbox state. */
class AuthEmailOutboxPublisher(
    private val outbox: AuthEmailOutboxService,
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    private val exchange: String,
    private val routingKey: String,
    private val lease: Duration,
    private val retryAfter: Duration,
    private val maximumAttempts: Int,
    private val clock: () -> Instant = Instant::now
) {
    private val log = LoggerFactory.getLogger(AuthEmailOutboxPublisher::class.java)

    /** Claims and publishes at most one event, returning its lifecycle outcome. */
    fun publishOne(): AuthEmailPublishOutcome {
        val now = clock()
        val record = outbox.claim(now, lease) ?: return AuthEmailPublishOutcome.EMPTY
        return try {
            val body = objectMapper.writeValueAsBytes(
                mapOf(
                    "eventId" to record.eventId.toString(),
                    "eventType" to "auth.email.requested.v1",
                    "schemaVersion" to 1,
                    "aggregateId" to record.eventId.toString(),
                    "groupId" to ZERO_UUID,
                    "groupRevision" to 1,
                    "occurredAt" to record.createdAt.toString(),
                    "payload" to mapOf(
                        "recipient" to record.recipient,
                        "template" to record.template,
                        "encryptedCredential" to record.encryptedCredential,
                        "expiresAt" to record.expiresAt.toString()
                    )
                )
            )
            val properties = MessageProperties().apply {
                contentType = MessageProperties.CONTENT_TYPE_JSON
                messageId = record.eventId.toString()
                timestamp = Date.from(record.createdAt)
            }
            rabbitTemplate.send(exchange, routingKey, Message(body, properties))
            outbox.acknowledge(record.eventId, clock())
            AuthEmailPublishOutcome.PUBLISHED
        } catch (error: AmqpException) {
            log.warn("Auth email event publish failed for event {}: {}", record.eventId, error.message)
            outbox.reject(record.eventId, clock(), retryAfter, maximumAttempts)
            AuthEmailPublishOutcome.RETRY_SCHEDULED
        } catch (error: Exception) {
            log.error("Auth email event serialization failed for event {}", record.eventId, error)
            outbox.reject(record.eventId, clock(), retryAfter, maximumAttempts)
            AuthEmailPublishOutcome.RETRY_SCHEDULED
        }
    }

    private companion object {
        const val ZERO_UUID = "00000000-0000-0000-0000-000000000000"
    }
}
