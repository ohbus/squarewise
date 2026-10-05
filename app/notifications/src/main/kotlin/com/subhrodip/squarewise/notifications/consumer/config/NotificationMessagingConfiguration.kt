package com.subhrodip.squarewise.notifications.consumer.config

import com.subhrodip.squarewise.ids.events.EventConstants
import com.subhrodip.squarewise.notifications.delivery.rate.DeliveryRateLimiter
import com.subhrodip.squarewise.notifications.delivery.rate.RedisDeliveryRateLimiter
import java.time.Duration
import com.subhrodip.squarewise.notifications.email.config.EmailProperties
import com.subhrodip.squarewise.notifications.consumer.transport.BrokerEnvelopeParser
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import com.subhrodip.squarewise.security.ratelimit.HmacRateLimitKeyDeriver
import com.subhrodip.squarewise.security.ratelimit.RedisRateLimiter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import tools.jackson.databind.ObjectMapper

/** Declares the notification event exchange, queues, and domain-event bindings. */
@Configuration
@EnableConfigurationProperties(NotificationMessagingProperties::class, EmailProperties::class)
class NotificationMessagingConfiguration(
    @Value("\${SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET:}")
    private val encodedRateLimitSecret: String
) {

    /** Creates the mandatory Redis-backed distributed limiter for runtime profiles. */
    @Bean
    fun rateLimiter(redis: StringRedisTemplate, meterRegistry: MeterRegistry): RateLimiter =
        RedisRateLimiter(redis, HmacRateLimitKeyDeriver.fromBase64(encodedRateLimitSecret), meterRegistry)

    /** Requires the application-managed mapper for broker envelope parsing. */
    @Bean
    fun brokerEnvelopeParser(objectMapper: ObjectMapper): BrokerEnvelopeParser = BrokerEnvelopeParser(objectMapper)

    /** Provides the bounded per-recipient delivery policy used by consumers. */
    @Bean
    fun deliveryRateLimiter(
        rateLimiter: RateLimiter,
        properties: NotificationMessagingProperties
    ): DeliveryRateLimiter = RedisDeliveryRateLimiter(
        rateLimiter,
        limit = properties.deliveryMaxPermits,
        window = Duration.ofSeconds(properties.deliveryWindowSeconds)
    )

    /** Declares the shared durable event exchange. */
    @Bean
    fun notificationEventsExchange(): TopicExchange =
        TopicExchange(EventConstants.EVENTS_EXCHANGE, true, false)

    /** Declares the durable notification consumer queue. */
    @Bean
    fun notificationQueue(properties: NotificationMessagingProperties): Queue =
        Queue(properties.queue, true, false, false, mapOf("x-dead-letter-exchange" to properties.deadLetterExchange))

    /** Routes group events to the notification consumer queue. */
    @Bean
    fun notificationGroupBinding(
        notificationQueue: Queue,
        notificationEventsExchange: TopicExchange
    ): Binding = BindingBuilder.bind(notificationQueue)
        .to(notificationEventsExchange)
        .with(EventConstants.Routing.ALL_GROUP_EVENTS)

    /** Routes expense events to the notification consumer queue. */
    @Bean
    fun notificationExpenseBinding(
        notificationQueue: Queue,
        notificationEventsExchange: TopicExchange
    ): Binding = BindingBuilder.bind(notificationQueue)
        .to(notificationEventsExchange)
        .with(EventConstants.Routing.ALL_EXPENSE_EVENTS)

    /** Routes settlement events to the notification consumer queue. */
    @Bean
    fun notificationSettlementBinding(
        notificationQueue: Queue,
        notificationEventsExchange: TopicExchange
    ): Binding = BindingBuilder.bind(notificationQueue)
        .to(notificationEventsExchange)
        .with(EventConstants.Routing.ALL_SETTLEMENT_EVENTS)

    /** Declares the dedicated durable auth-email queue. */
    @Bean
    fun authEmailQueue(properties: NotificationMessagingProperties): Queue =
        Queue(properties.authEmailQueue, true, false, false, mapOf("x-dead-letter-exchange" to properties.deadLetterExchange))

    /** Declares the exchange receiving permanently rejected deliveries. */
    @Bean
    fun notificationDeadLetterExchange(properties: NotificationMessagingProperties): TopicExchange =
        TopicExchange(properties.deadLetterExchange, true, false)

    /** Declares the durable notification poison-message queue. */
    @Bean
    fun notificationDeadLetterQueue(properties: NotificationMessagingProperties): Queue =
        Queue(properties.deadLetterQueue, true)

    /** Declares the durable authentication-email poison-message queue. */
    @Bean
    fun authEmailDeadLetterQueue(properties: NotificationMessagingProperties): Queue =
        Queue(properties.authEmailDeadLetterQueue, true)

    /** Routes rejected notification events to the notification DLQ. */
    @Bean
    fun notificationDeadLetterBinding(
        notificationDeadLetterQueue: Queue,
        notificationDeadLetterExchange: TopicExchange
    ): Binding = BindingBuilder.bind(notificationDeadLetterQueue)
        .to(notificationDeadLetterExchange)
        .with("#")

    /** Routes rejected auth-email events to the auth-email DLQ. */
    @Bean
    fun authEmailDeadLetterBinding(
        authEmailDeadLetterQueue: Queue,
        notificationDeadLetterExchange: TopicExchange
    ): Binding = BindingBuilder.bind(authEmailDeadLetterQueue)
        .to(notificationDeadLetterExchange)
        .with(EventConstants.Routing.AUTH_EMAIL_REQUESTED)

    /** Explicitly declares poison queues and their bindings as one broker topology. */
    @Bean
    fun deadLetterDeclarables(properties: NotificationMessagingProperties): Declarables {
        val exchange = TopicExchange(properties.deadLetterExchange, true, false)
        val notificationQueue = Queue(properties.deadLetterQueue, true)
        val authEmailQueue = Queue(properties.authEmailDeadLetterQueue, true)
        return Declarables(
            exchange,
            notificationQueue,
            authEmailQueue,
            BindingBuilder.bind(notificationQueue).to(exchange).with("#"),
            BindingBuilder.bind(authEmailQueue).to(exchange).with(EventConstants.Routing.AUTH_EMAIL_REQUESTED)
        )
    }

    /** Routes only versioned auth-email events to the protected delivery adapter. */
    @Bean
    fun authEmailBinding(
        authEmailQueue: Queue,
        notificationEventsExchange: TopicExchange
    ): Binding = BindingBuilder.bind(authEmailQueue)
        .to(notificationEventsExchange)
        .with(EventConstants.Routing.AUTH_EMAIL_REQUESTED)
}
