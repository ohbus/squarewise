package com.subhrodip.squarewise.notifications.consumer.service

import com.subhrodip.squarewise.notifications.consumer.model.NotificationConsumptionOutcome
import com.subhrodip.squarewise.notifications.consumer.model.NotificationEvent
import com.subhrodip.squarewise.notifications.consumer.persistence.ProcessedNotificationEventRepository
import com.subhrodip.squarewise.notifications.email.delivery.EmailDispatcher
import com.subhrodip.squarewise.notifications.email.delivery.opaqueRecipientId
import com.subhrodip.squarewise.notifications.email.persistence.NotificationEmailDeliveryEntity
import com.subhrodip.squarewise.notifications.email.persistence.NotificationEmailDeliveryRepository

import com.subhrodip.squarewise.notifications.delivery.rate.DeliveryRateLimiter
import com.subhrodip.squarewise.notifications.email.delivery.EmailDeliveryOutcome
import com.subhrodip.squarewise.notifications.errors.NotificationDomainException
import com.subhrodip.squarewise.notifications.preferences.persistence.PreferenceStore
import com.subhrodip.squarewise.errors.catalog.NotificationErrors
import com.subhrodip.squarewise.errors.code.RetryPolicy

import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
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
    private val deliveryRateLimiter: DeliveryRateLimiter,
    private val emailDeliveries: NotificationEmailDeliveryRepository? = null,
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

        val delivery = if (outcome == NotificationConsumptionOutcome.APPLIED) {
            emailDeliveries?.let { repository ->
                repository.findById(event.notificationId).orElseGet {
                    repository.save(NotificationEmailDeliveryEntity(event.notificationId))
                }
            }
        } else {
            emailDeliveries?.let { repository -> repository.findById(event.notificationId).orElse(null) }
        }
        if (
            outcome == NotificationConsumptionOutcome.APPLIED ||
            delivery?.status == NotificationEmailDeliveryEntity.PENDING ||
            delivery?.status == NotificationEmailDeliveryEntity.DISPATCHING
        ) {
            if (delivery?.status == NotificationEmailDeliveryEntity.DELIVERED || delivery?.status == NotificationEmailDeliveryEntity.SKIPPED) {
                return outcome
            }
            emailDeliveries?.let { repository ->
                val now = Instant.now()
                if (repository.claimForDispatch(event.notificationId, now, now.minus(DISPATCH_LEASE)) == 0) {
                    return outcome
                }
            }
            try {
                dispatchEmailIfEnabled(event)
            } catch (failure: NotificationDomainException) {
                if (failure.definition.retryPolicy == RetryPolicy.NEVER) {
                    markDelivery(event.notificationId, NotificationEmailDeliveryEntity.SKIPPED)
                } else {
                    markDelivery(event.notificationId, NotificationEmailDeliveryEntity.PENDING)
                }
                throw failure
            } catch (failure: RuntimeException) {
                markDelivery(event.notificationId, NotificationEmailDeliveryEntity.PENDING)
                throw failure
            }
        }

        return outcome
    }

    private fun dispatchEmailIfEnabled(event: NotificationEvent) {
        val recipientId = event.recipientId
        val preferences = preferenceStore.get(recipientId)
        if (!preferences.emailEnabled) {
            markDelivery(event.notificationId, NotificationEmailDeliveryEntity.SKIPPED)
            log.info("Email notifications disabled for recipientId={}; skipping dispatch", opaqueRecipientId(recipientId))
            return
        }
        val recipientEmail = resolveRecipientEmail(event) ?: run {
            markDelivery(event.notificationId, NotificationEmailDeliveryEntity.SKIPPED)
            return
        }
        if (!deliveryRateLimiter.allow(recipientId)) {
            log.warn(
                "Email delivery rate limit reached for recipientId={}; suppressing delivery",
                opaqueRecipientId(recipientId)
            )
            markDelivery(event.notificationId, NotificationEmailDeliveryEntity.SKIPPED)
            return
        }
        val deliveryOutcome = emailDispatcher.send(recipientEmail, "Notification: ${event.title}", event.body)
        when (deliveryOutcome) {
            EmailDeliveryOutcome.DELIVERED -> markDelivery(
                event.notificationId,
                NotificationEmailDeliveryEntity.DELIVERED
            )
            EmailDeliveryOutcome.SKIPPED -> markDelivery(
                event.notificationId,
                NotificationEmailDeliveryEntity.SKIPPED
            )
            EmailDeliveryOutcome.RETRYABLE_FAILURE,
            EmailDeliveryOutcome.PERMANENT_FAILURE -> throw NotificationDomainException(
                NotificationErrors.EMAIL_DISPATCH_FAILED
            )
        }
        log.info("Email dispatch outcome for recipientId={} (notificationId={}): {}", opaqueRecipientId(recipientEmail), event.notificationId, deliveryOutcome)
    }

    private fun markDelivery(notificationId: UUID, status: String) {
        emailDeliveries?.findById(notificationId)?.ifPresent { delivery ->
            delivery.status = status
            delivery.updatedAt = Instant.now()
            emailDeliveries.save(delivery)
        }
    }

    private fun resolveRecipientEmail(event: NotificationEvent): String? {
        if (!event.recipientEmail.isNullOrBlank()) {
            return event.recipientEmail.trim()
        }
        val recipient = event.subject.trim()
        return recipient.takeIf { it.contains("@") }
    }

    private companion object {
        /**
         * Bounds recovery time for a consumer that crashes after claiming a dispatch.
         * A provider may still receive one duplicate if the process crashes after SMTP accepts the mail.
         */
        private val DISPATCH_LEASE: Duration = Duration.ofMinutes(5)
    }
}

private fun RuntimeException.isConstraintViolation(): Boolean =
    this is DataIntegrityViolationException || this is ConstraintViolationException
