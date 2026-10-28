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
        val outcome = processEvent(event)
        val delivery = resolveDelivery(event, outcome)
        if (shouldDispatch(outcome, delivery)) {
            executeDispatch(event)
        }
        return outcome
    }

    private fun processEvent(event: NotificationEvent): NotificationConsumptionOutcome {
        return try {
            processor.process(event)
        } catch (failure: RuntimeException) {
            if (failure.isConstraintViolation() && processedEvents.existsById(event.eventId)) {
                NotificationConsumptionOutcome.DUPLICATE
            } else {
                throw failure
            }
        }
    }

    private fun resolveDelivery(
        event: NotificationEvent,
        outcome: NotificationConsumptionOutcome
    ): NotificationEmailDeliveryEntity? {
        val repository = emailDeliveries ?: return null
        return if (outcome == NotificationConsumptionOutcome.APPLIED) {
            repository.findById(event.notificationId).orElseGet {
                repository.save(
                    NotificationEmailDeliveryEntity(
                        notificationId = event.notificationId,
                        recipientEmail = event.recipientEmail
                    )
                )
            }
        } else {
            repository.findById(event.notificationId).orElseGet {
                repository.save(
                    NotificationEmailDeliveryEntity(
                        notificationId = event.notificationId,
                        recipientEmail = event.recipientEmail
                    )
                )
            }
        }
    }

    private fun shouldDispatch(
        outcome: NotificationConsumptionOutcome,
        delivery: NotificationEmailDeliveryEntity?
    ): Boolean {
        if (delivery?.status == NotificationEmailDeliveryEntity.DELIVERED ||
            delivery?.status == NotificationEmailDeliveryEntity.SKIPPED
        ) {
            return false
        }
        return outcome == NotificationConsumptionOutcome.APPLIED ||
            delivery?.status == NotificationEmailDeliveryEntity.PENDING ||
            delivery?.status == NotificationEmailDeliveryEntity.DISPATCHING
    }

    private fun executeDispatch(event: NotificationEvent) {
        val claimAt = emailDeliveries?.let { repository ->
            val now = Instant.now()
            if (repository.claimForDispatch(event.notificationId, now, now.minus(DISPATCH_LEASE)) == 0) {
                throw NotificationDomainException(
                    NotificationErrors.EMAIL_DISPATCH_FAILED,
                    "Email delivery is still owned by another worker"
                )
            }
            now
        }
        try {
            dispatchEmailIfEnabled(event, claimAt)
        } catch (failure: NotificationDomainException) {
            val status = if (failure.definition.retryPolicy == RetryPolicy.NEVER) {
                NotificationEmailDeliveryEntity.SKIPPED
            } else {
                NotificationEmailDeliveryEntity.PENDING
            }
            markDelivery(event.notificationId, status, claimAt)
            throw failure
        } catch (failure: RuntimeException) {
            markDelivery(event.notificationId, NotificationEmailDeliveryEntity.PENDING, claimAt)
            throw failure
        }
    }

    private fun dispatchEmailIfEnabled(event: NotificationEvent, claimAt: Instant?) {
        val recipientId = event.recipientId
        val preferences = preferenceStore.get(recipientId)
        if (!preferences.emailEnabled) {
            markDelivery(event.notificationId, NotificationEmailDeliveryEntity.SKIPPED, claimAt)
            log.info("Email notifications disabled for recipientId={}; skipping dispatch", opaqueRecipientId(recipientId))
            return
        }
        val recipientEmail = resolveRecipientEmail(event) ?: run {
            markDelivery(event.notificationId, NotificationEmailDeliveryEntity.SKIPPED, claimAt)
            return
        }
        if (!deliveryRateLimiter.allow(recipientId)) {
            log.warn(
                "Email delivery rate limit reached for recipientId={}; suppressing delivery",
                opaqueRecipientId(recipientId)
            )
            markDelivery(event.notificationId, NotificationEmailDeliveryEntity.SKIPPED, claimAt)
            return
        }
        val deliveryOutcome = emailDispatcher.send(recipientEmail, "Notification: ${event.title}", event.body)
        when (deliveryOutcome) {
            EmailDeliveryOutcome.DELIVERED -> markDelivery(
                event.notificationId,
                NotificationEmailDeliveryEntity.DELIVERED,
                claimAt
            )
            EmailDeliveryOutcome.SKIPPED -> markDelivery(
                event.notificationId,
                NotificationEmailDeliveryEntity.SKIPPED,
                claimAt
            )
            EmailDeliveryOutcome.RETRYABLE_FAILURE,
            EmailDeliveryOutcome.PERMANENT_FAILURE -> throw NotificationDomainException(
                NotificationErrors.EMAIL_DISPATCH_FAILED
            )
        }
        log.info("Email dispatch outcome for recipientId={} (notificationId={}): {}", opaqueRecipientId(recipientEmail), event.notificationId, deliveryOutcome)
    }

    private fun markDelivery(notificationId: UUID, status: String, claimAt: Instant?) {
        if (emailDeliveries == null || claimAt == null) return
        emailDeliveries.updateStatusIfClaimed(notificationId, status, Instant.now(), claimAt)
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
