package com.subhrodip.squarewise.bff.messaging.transport

import com.subhrodip.squarewise.bff.messaging.model.BffEventEnvelope
import com.subhrodip.squarewise.bff.messaging.service.BffEventConsumer
import com.subhrodip.squarewise.bff.errors.BffDomainException
import com.subhrodip.squarewise.errors.async.AsyncContext
import com.subhrodip.squarewise.errors.async.AsyncExecutionResult
import com.subhrodip.squarewise.errors.async.AsyncExecutionTemplate
import com.subhrodip.squarewise.errors.async.AsyncMetricsRecorder
import com.subhrodip.squarewise.errors.async.MessageDisposition
import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.exceptions.FatalErrorClassifier

import com.rabbitmq.client.Channel
import java.util.UUID
import java.util.concurrent.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener
import tools.jackson.databind.ObjectMapper

/**
 * AMQP message listener adapter for BFF event consumption.
 *
 * Deserializes message payloads conforming to `envelope.schema.json`, delegates to
 * [BffEventConsumer] for deduplication and live-update fanout. Retryable fanout failures are
 * requeued, while terminal failures are acknowledged only after a structured dead-letter record
 * has been published.
 */
class RabbitBffEventListener(
    private val eventConsumer: BffEventConsumer,
    private val objectMapper: ObjectMapper,
    private val asyncExecutionTemplate: AsyncExecutionTemplate = AsyncExecutionTemplate(AsyncMetricsRecorder { _, _, _ -> }),
    private val deadLetterPublisher: DeadLetterPublisher,
) : ChannelAwareMessageListener {

    private val log = LoggerFactory.getLogger(RabbitBffEventListener::class.java)

    override fun onMessage(message: Message, channel: Channel?) {
        val deliveryTag = message.messageProperties.deliveryTag
        val parsed = runCatching { parse(message.body) }
        try {
            when (val result = asyncExecutionTemplate.execute(
                context = AsyncContext(
                    parsed.getOrNull()?.eventId ?: UUID.nameUUIDFromBytes(message.body),
                    "bff.event",
                    1,
                    attemptCount(message),
                    "bff.rabbit",
                ),
                definition = BffErrors.LIVE_UPDATE_UPSTREAM_UNAVAILABLE,
                queue = "squarewise.bff.events",
                payload = if (message.body.isEmpty()) "<empty>".toByteArray() else message.body,
            ) { eventConsumer.consume(parsed.getOrThrow()) }) {
                is AsyncExecutionResult.Completed -> channel?.basicAck(deliveryTag, false)
                is AsyncExecutionResult.Failed -> result.deadLetter?.let { deadLetter ->
                    deadLetterPublisher.publish(deadLetter)
                    channel?.basicAck(deliveryTag, false)
                } ?: reject(channel, deliveryTag, result.disposition == MessageDisposition.NACK_REQUEUE)
            }
        } catch (exception: Exception) {
            if (exception is CancellationException || FatalErrorClassifier.isFatal(exception)) throw exception
            log.error("Failed BFF event delivery tag={} exceptionType={}", deliveryTag, exception::class.simpleName)
            reject(channel, deliveryTag, true)
        }
    }

    private fun reject(channel: Channel?, deliveryTag: Long, requeue: Boolean) {
        try {
            channel?.basicReject(deliveryTag, requeue)
        } catch (exception: Exception) {
            log.error("Failed BFF event rejection tag={} exceptionType={}", deliveryTag, exception::class.simpleName)
        }
    }

    private fun parse(payload: ByteArray): BffEventEnvelope = try {
        objectMapper.readValue(payload, BffEventEnvelope::class.java)
    } catch (exception: Exception) {
        throw BffDomainException(BffErrors.LIVE_UPDATE_EVENT_INVALID, cause = exception)
    }

    private fun attemptCount(message: Message): Int =
        (message.messageProperties.headers["x-attempt"] as? Number)?.toInt()?.coerceIn(1, 4)
            ?: if (message.messageProperties.isRedelivered == true) 3 else 1
}
