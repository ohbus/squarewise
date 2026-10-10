@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.notifications.consumer.transport

import com.subhrodip.squarewise.notifications.consumer.model.NotificationEvent
import com.subhrodip.squarewise.notifications.consumer.service.NotificationConsumer
import com.subhrodip.squarewise.errors.async.AsyncContext
import com.subhrodip.squarewise.errors.async.AsyncExecutionResult
import com.subhrodip.squarewise.errors.async.AsyncExecutionTemplate
import com.subhrodip.squarewise.errors.async.AsyncMetricsRecorder
import com.subhrodip.squarewise.errors.async.MessageDisposition
import com.subhrodip.squarewise.errors.catalog.NotificationErrors

import com.rabbitmq.client.Channel
import java.util.UUID
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener
import org.springframework.stereotype.Component

/** Converts a validated broker envelope to the notification consumer event. */
fun BrokerEnvelope.toNotificationEvent(): NotificationEvent {
    val notificationId = (payload["notificationId"] as? String)?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    } ?: aggregateId
    val subject = listOfNotNull(
        payload["subject"] as? String,
        payload["changedBy"] as? String,
        payload["recipient"] as? String,
        payload["recipientId"] as? String,
        payload["userId"] as? String
    ).firstOrNull { it.isNotBlank() }
        ?: throw InvalidEnvelopeException("Notification payload is missing a recipient subject")
    val message = (payload["message"] as? String ?: payload["description"] as? String)
        ?.takeIf { it.isNotBlank() }
        ?: "$eventType for group $groupId"
    return NotificationEvent(
        eventId = eventId,
        notificationId = notificationId,
        subject = subject.take(200),
        eventType = eventType.take(200),
        message = message.take(2000),
        occurredAt = occurredAt
    )
}

/** RabbitMQ adapter that acknowledges, rejects, or requeues notification events. */
@Component
class RabbitNotificationListener(
    private val consumer: NotificationConsumer,
    private val envelopeParser: BrokerEnvelopeParser,
    private val asyncExecutionTemplate: AsyncExecutionTemplate = AsyncExecutionTemplate(AsyncMetricsRecorder { _, _, _ -> })
) : ChannelAwareMessageListener {
    @RabbitListener(
        queues = ["\${squarewise.notifications.queue:squarewise.notifications.v2}"],
        ackMode = "MANUAL"
    )
    override fun onMessage(message: Message, channel: Channel?) {
        val deliveryTag = message.messageProperties.deliveryTag
        try {
            val event = envelopeParser.parse(message.body).toNotificationEvent()
            when (val result = asyncExecutionTemplate.execute(
                context = AsyncContext(event.eventId, event.eventType, 1, attemptCount(message), "notifications.rabbit"),
                definition = NotificationErrors.NOTIFICATION_DISPATCH_FAILED,
                queue = "squarewise.notifications.v2",
                payload = message.body,
            ) { consumer.consume(event) }) {
                is AsyncExecutionResult.Completed -> channel?.basicAck(deliveryTag, false)
                is AsyncExecutionResult.Failed -> channel?.basicReject(deliveryTag, result.disposition == MessageDisposition.NACK_REQUEUE)
            }
        } catch (_: InvalidEnvelopeException) {
            channel?.basicReject(deliveryTag, false)
        }
    }

    private fun attemptCount(message: Message): Int =
        (message.messageProperties.headers["x-attempt"] as? Number)?.toInt()?.coerceIn(1, 4)
            ?: if (message.messageProperties.isRedelivered == true) 3 else 1
}
