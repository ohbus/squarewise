package com.subhrodip.squarewise.notifications.consumer.service

import com.subhrodip.squarewise.notifications.consumer.model.NotificationConsumptionOutcome
import com.subhrodip.squarewise.notifications.consumer.model.NotificationEvent
import com.subhrodip.squarewise.notifications.consumer.persistence.ProcessedNotificationEventRepository
import com.subhrodip.squarewise.notifications.email.delivery.EmailDispatcher
import com.subhrodip.squarewise.notifications.email.delivery.opaqueRecipientId

import com.subhrodip.squarewise.notifications.delivery.rate.DeliveryRateLimiter
import com.subhrodip.squarewise.notifications.preferences.persistence.PreferenceStore

import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
/**
 * Broker-facing application boundary. APPLIED and DUPLICATE deliveries may be
 * acknowledged; any exception is deliberately propagated so the delivery can
 * be retried by the broker.
 *
 * When an event is APPLIED, email dispatch is triggered if allowed by recipient preferences.
 * Email dispatch errors propagate so the broker can retry transient delivery failures.
 */
@Service
class NotificationEventConsumer(
    private val processor: TransactionalNotificationEventProcessor,
    private val processedEvents: ProcessedNotificationEventRepository,
    private val preferenceStore: PreferenceStore,
    private val emailDispatcher: EmailDispatcher,
    private val deliveryRateLimiter: DeliveryRateLimiter
) : NotificationConsumer {

    private val log = LoggerFactory.getLogger(NotificationEventConsumer::class.java)

    override fun consume(event: NotificationEvent): NotificationConsumptionOutcome {
        val outcome = try {
            processor.process(event)
        } catch (failure: RuntimeException) {
            if (failure.isConstraintViolation() && processedEvents.existsById(event.eventId)) {
                NotificationConsumptionOutcome.DUPLICATE
            } else {
                throw failure
            }
        }

        if (outcome == NotificationConsumptionOutcome.APPLIED) {
            dispatchEmailIfEnabled(event)
        }

        return outcome
    }

    private fun dispatchEmailIfEnabled(event: NotificationEvent) {
        val recipientId = event.recipientId
        val preferences = preferenceStore.get(recipientId) ?: return
        if (!preferences.emailEnabled) {
            log.info("Email notifications disabled for recipientId={}; skipping dispatch", opaqueRecipientId(recipientId))
            return
        }
        val recipientEmail = runCatching { resolveRecipientEmail(event) }.getOrElse {
            log.warn("No verified email recipient for notification {}; suppressing delivery", event.notificationId)
            return
        }
        if (!deliveryRateLimiter.allow(recipientId)) {
            log.warn(
                "Email delivery rate limit reached for recipientId={}; suppressing delivery",
                opaqueRecipientId(recipientId)
            )
            return
        }
        val deliveryOutcome = emailDispatcher.send(recipientEmail, "Notification: ${event.title}", event.body)
        log.info("Email dispatch outcome for recipientId={} (notificationId={}): {}", opaqueRecipientId(recipientEmail), event.notificationId, deliveryOutcome)
    }

    private fun resolveRecipientEmail(event: NotificationEvent): String {
        if (!event.recipientEmail.isNullOrBlank()) {
            return event.recipientEmail.trim()
        }
        val recipient = event.subject.trim()
        require(recipient.contains("@")) { "Verified recipient email is required for notification delivery" }
        return recipient
    }
}

private fun RuntimeException.isConstraintViolation(): Boolean =
    this is DataIntegrityViolationException || this is ConstraintViolationException
