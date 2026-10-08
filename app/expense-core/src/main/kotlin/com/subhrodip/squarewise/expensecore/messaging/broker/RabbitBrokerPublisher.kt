package com.subhrodip.squarewise.expensecore.messaging.broker

import java.util.Date
import com.subhrodip.squarewise.ids.events.EventConstants
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import tools.jackson.databind.ObjectMapper

/**
 * Spring AMQP implementation of [BrokerPublisher] that routes events to RabbitMQ.
 *
 * Conforms to `contracts/events/envelope.schema.json` by serializing messages with
 * the required envelope structure (eventId, eventType, schemaVersion, aggregateId,
 * groupId, groupRevision, occurredAt, payload) and publishing to the central topic exchange.
 */
class RabbitBrokerPublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    private val exchange: String = EventConstants.EVENTS_EXCHANGE,
    private val confirmTimeoutMs: Long = 5_000
) : BrokerPublisher {

    init {
        require(confirmTimeoutMs > 0) { "confirmTimeoutMs must be positive" }
    }

    private val log = LoggerFactory.getLogger(RabbitBrokerPublisher::class.java)

    override fun publish(message: BrokerMessage): PublishResult {
        return try {
            val routingKey = message.eventType
            val envelope = mapOf(
                "eventId" to message.eventId.toString(),
                "eventType" to message.eventType,
                "schemaVersion" to EventConstants.CURRENT_SCHEMA_VERSION,
                "aggregateId" to message.headers["aggregate-id"],
                "groupId" to message.headers["group-id"],
                "groupRevision" to (message.headers["group-revision"]?.toLongOrNull() ?: 1L),
                "occurredAt" to message.occurredAt.toString(),
                "payload" to objectMapper.readValue(message.payload, Any::class.java)
            )

            val body = objectMapper.writeValueAsBytes(envelope)
            val properties = MessageProperties().apply {
                contentType = MessageProperties.CONTENT_TYPE_JSON
                messageId = message.eventId.toString()
                timestamp = Date.from(message.occurredAt)
                message.headers.forEach { (key, value) -> setHeader(key, value) }
            }

            rabbitTemplate.invoke { operations ->
                operations.send(exchange, routingKey, Message(body, properties))
                operations.waitForConfirmsOrDie(confirmTimeoutMs)
                null
            }
            PublishResult.Confirmed
        } catch (ex: AmqpException) {
            log.error("Failed to publish message ${message.eventId} to exchange $exchange: ${ex.message}", ex)
            PublishResult.Rejected("Broker AMQP error: ${ex.message}")
        } catch (ex: Exception) {
            log.error("Failed to serialize or publish message ${message.eventId}: ${ex.message}", ex)
            PublishResult.Rejected("Publish error: ${ex.message}")
        }
    }
}
