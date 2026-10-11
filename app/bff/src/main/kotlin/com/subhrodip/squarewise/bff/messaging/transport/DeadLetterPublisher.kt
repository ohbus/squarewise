package com.subhrodip.squarewise.bff.messaging.transport

import com.subhrodip.squarewise.errors.async.DeadLetterRecord
import com.subhrodip.squarewise.bff.messaging.config.BffMessagingProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.AmqpException
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** Port for publishing bounded terminal BFF message failures. */
fun interface DeadLetterPublisher {
    /** Publish one structured dead-letter record. */
    fun publish(record: DeadLetterRecord)
}

/** RabbitMQ adapter for the BFF dead-letter contract. */
@Component
class RabbitDeadLetterPublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val properties: BffMessagingProperties,
    private val objectMapper: ObjectMapper,
) : DeadLetterPublisher {
    override fun publish(record: DeadLetterRecord) {
        rabbitTemplate.invoke { operations ->
            val correlation = CorrelationData()
            operations.convertAndSend(
                properties.deadLetterExchange,
                "bff.dead-letter",
                objectMapper.writeValueAsBytes(record),
                correlation,
            )
            operations.waitForConfirmsOrDie(5_000)
            correlation.returned?.let { returned ->
                throw AmqpException("Dead-letter message was returned: ${returned.replyText}")
            }
            null
        }
    }
}
