package com.subhrodip.squarewise.notifications.email.delivery

import com.subhrodip.squarewise.db.routing.DbWriter
import com.subhrodip.squarewise.notifications.consumer.model.NotificationEvent
import com.subhrodip.squarewise.notifications.consumer.service.NotificationEventConsumer
import com.subhrodip.squarewise.notifications.email.persistence.NotificationEmailDeliveryRepository
import com.subhrodip.squarewise.notifications.inbox.persistence.NotificationInboxRepository
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Replays notification email deliveries whose worker lease expired after a crash. */
@Component
class NotificationEmailDeliveryRecovery(
    private val deliveries: NotificationEmailDeliveryRepository,
    private val inbox: NotificationInboxRepository,
    private val consumer: NotificationEventConsumer,
    private val clock: Clock = Clock.systemUTC(),
    @Value("\${squarewise.notifications.email-delivery-lease-minutes:5}") leaseMinutes: Long = 5,
    @Value("\${squarewise.notifications.email-delivery-recovery-batch-size:100}") private val batchSize: Int = 100
) {
    private val log = LoggerFactory.getLogger(NotificationEmailDeliveryRecovery::class.java)
    private val lease: Duration = Duration.ofMinutes(leaseMinutes)

    init {
        require(!lease.isNegative && !lease.isZero) { "email delivery lease must be positive" }
        require(batchSize > 0) { "email delivery recovery batch size must be positive" }
    }

    /** Replays at most one bounded batch of leases older than the configured lease duration. */
    @Scheduled(fixedDelayString = "\${squarewise.notifications.email-delivery-recovery-delay-ms:60000}")
    @DbWriter("notifications.email-delivery.recovery")
    fun recoverExpired(): Int {
        val cutoff = clock.instant().minus(lease)
        return deliveries.findDispatchingBefore(cutoff, PageRequest.of(0, batchSize)).count { delivery ->
            val item = inbox.findById(delivery.notificationId).orElse(null) ?: return@count false
            try {
                consumer.consume(
                    NotificationEvent(
                        eventId = UUID.nameUUIDFromBytes(
                            "notification-email-recovery:${delivery.notificationId}".toByteArray(StandardCharsets.UTF_8)
                        ),
                        notificationId = delivery.notificationId,
                        subject = item.subject,
                        eventType = item.eventType,
                        message = item.message,
                        occurredAt = item.occurredAt,
                        recipientEmail = delivery.recipientEmail,
                        title = item.eventType,
                        body = item.message
                    )
                )
                true
            } catch (failure: RuntimeException) {
                log.warn("Notification email recovery failed for notificationId={}", delivery.notificationId, failure)
                false
            }
        }
    }
}
