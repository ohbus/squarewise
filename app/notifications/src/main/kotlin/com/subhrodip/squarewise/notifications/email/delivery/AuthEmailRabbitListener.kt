@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.notifications.email.delivery

import com.rabbitmq.client.Channel
import com.subhrodip.squarewise.notifications.consumer.transport.DeadLetterPublisher
import com.subhrodip.squarewise.notifications.consumer.transport.InvalidEnvelopeException
import com.subhrodip.squarewise.errors.async.AsyncContext
import com.subhrodip.squarewise.errors.async.AsyncExecutionResult
import com.subhrodip.squarewise.errors.async.AsyncExecutionTemplate
import com.subhrodip.squarewise.errors.async.AsyncMetricsRecorder
import com.subhrodip.squarewise.errors.async.MessageDisposition
import com.subhrodip.squarewise.errors.catalog.NotificationErrors
import com.subhrodip.squarewise.errors.exceptions.FatalErrorClassifier
import com.subhrodip.squarewise.notifications.errors.NotificationDomainException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener
import org.springframework.stereotype.Component

private const val AUTH_EMAIL_EVENT_TYPE = "auth.email.requested.v1"

/** Consumes encrypted auth-email envelopes and delegates plaintext handling only inside Notifications. */
@Component
class AuthEmailRabbitListener(
    private val objectMapper: ObjectMapper,
    private val consumer: AuthEmailDeliveryConsumer,
    private val asyncExecutionTemplate: AsyncExecutionTemplate = AsyncExecutionTemplate(AsyncMetricsRecorder { _, _, _ -> }),
    private val deadLetterPublisher: DeadLetterPublisher = DeadLetterPublisher { },
) : ChannelAwareMessageListener {
    @RabbitListener(
        queues = ["\${squarewise.notifications.auth-email-queue:squarewise.auth-email.v2}"],
        ackMode = "MANUAL"
    )
    override fun onMessage(message: Message, channel: Channel?) {
        val deliveryTag = message.messageProperties.deliveryTag
        try {
            when (val result = asyncExecutionTemplate.execute(
                context = AsyncContext(
                    eventId = UUID.nameUUIDFromBytes(message.body),
                    eventType = AUTH_EMAIL_EVENT_TYPE,
                    schemaVersion = 1,
                    attemptCount = attemptCount(message),
                    source = "notifications.auth-email",
                ),
                definition = NotificationErrors.EMAIL_DISPATCH_FAILED,
                queue = "squarewise.auth-email.v2",
                payload = if (message.body.isEmpty()) "<empty>".toByteArray() else message.body,
            ) {
                val outcome = consumer.consume(parse(message.body))
                if (outcome == EmailDeliveryOutcome.RETRYABLE_FAILURE ||
                    outcome == EmailDeliveryOutcome.PERMANENT_FAILURE
                ) {
                    throw NotificationDomainException(
                        NotificationErrors.EMAIL_DISPATCH_FAILED
                    )
                }
                outcome
            }) {
                is AsyncExecutionResult.Completed -> channel?.basicAck(deliveryTag, false)
                is AsyncExecutionResult.Failed -> {
                    val deadLetter = result.deadLetter
                    if (deadLetter != null) {
                        deadLetterPublisher.publish(deadLetter)
                        channel?.basicAck(deliveryTag, false)
                    } else {
                        channel?.basicReject(deliveryTag, result.disposition == MessageDisposition.NACK_REQUEUE)
                    }
                }
            }
        } catch (exception: Exception) {
            if (exception is CancellationException || FatalErrorClassifier.isFatal(exception)) throw exception
            channel?.basicReject(deliveryTag, true)
        }
    }

    private fun parse(body: ByteArray): AuthEmailDeliveryEvent {
        val root = try {
            objectMapper.readTree(body)
                ?: throw InvalidEnvelopeException("Empty auth email event")
        } catch (error: InvalidEnvelopeException) {
            throw error
        } catch (error: Exception) {
            throw InvalidEnvelopeException("Malformed auth email event", error)
        }
        val eventType = required(root, "eventType").asString()
        if (eventType != AUTH_EMAIL_EVENT_TYPE) {
            throw InvalidEnvelopeException("Unexpected auth email event type")
        }
        val payload = root.get("payload")
            ?: throw InvalidEnvelopeException("Auth email payload is missing")
        return AuthEmailDeliveryEvent(
            eventId = required(root, "eventId").asString(),
            recipient = required(payload, "recipient").asString(),
            template = required(payload, "template").asString(),
            encryptedCredential = required(payload, "encryptedCredential").asString(),
            expiresAt = runCatching { Instant.parse(required(payload, "expiresAt").asString()) }
                .getOrElse { throw InvalidEnvelopeException("Invalid auth email expiry", it) }
        )
    }

    private fun required(node: JsonNode, field: String): JsonNode =
        node.get(field)?.takeIf { !it.isNull } ?: throw InvalidEnvelopeException("Missing auth email field: $field")

    private fun attemptCount(message: Message): Int =
        (message.messageProperties.headers["x-attempt"] as? Number)?.toInt()?.coerceIn(1, 4)
            ?: if (message.messageProperties.isRedelivered == true) 3 else 1
}
