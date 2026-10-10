package com.subhrodip.squarewise.notifications.consumer.transport

import com.subhrodip.squarewise.errors.async.DeadLetterRecord
import com.subhrodip.squarewise.notifications.consumer.config.NotificationMessagingProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** Port for durably publishing structured terminal message failures. */
fun interface DeadLetterPublisher {
    /** Publish one bounded terminal failure record. */
    fun publish(record: DeadLetterRecord)
}

/** RabbitMQ adapter for the structured notification dead-letter contract. */
@Component
class RabbitDeadLetterPublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val properties: NotificationMessagingProperties,
    private val objectMapper: ObjectMapper,
) : DeadLetterPublisher {
    override fun publish(record: DeadLetterRecord) {
        rabbitTemplate.convertAndSend(
            properties.deadLetterExchange,
            "notifications.dead-letter",
            objectMapper.writeValueAsBytes(record),
        )
    }
}
