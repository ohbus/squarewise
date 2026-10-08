package com.subhrodip.squarewise.notifications.email.delivery

import com.subhrodip.squarewise.notifications.email.security.AuthEmailEnvelopeProtector
import com.subhrodip.squarewise.notifications.delivery.rate.DeliveryRateLimiter
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Validates and delivers protected one-time authentication email events. */
@Service
class AuthEmailDeliveryConsumer(
    private val protector: AuthEmailEnvelopeProtector,
    private val dispatcher: EmailDispatcher,
    private val deliveryRateLimiter: DeliveryRateLimiter
) {
    private val log = LoggerFactory.getLogger(AuthEmailDeliveryConsumer::class.java)

    /** Delivers one event and never logs the decrypted credential. */
    fun consume(event: AuthEmailDeliveryEvent, now: Instant = Instant.now()): EmailDeliveryOutcome {
        require(event.template == "LOGIN_LINK" || event.template == "LOGIN_CODE") { "unsupported auth email template" }
        require(event.expiresAt.isAfter(now)) { "auth email credential has expired" }
        if (!deliveryRateLimiter.allow(event.recipient)) {
            log.warn("Authentication email delivery rate limit reached for event {}; suppressing delivery", event.eventId)
            return EmailDeliveryOutcome.SKIPPED
        }
        val credential = protector.reveal(event.encryptedCredential, event.recipient, event.template)
        val subject = if (event.template == "LOGIN_LINK") "Your Squarewise sign-in link" else "Your Squarewise sign-in code"
        val body = if (event.template == "LOGIN_LINK") "Use this one-time sign-in credential: $credential" else "Your one-time Squarewise sign-in code is: $credential"
        return dispatcher.send(event.recipient, subject, body).also {
            log.info("Authentication email delivery completed for event {} with outcome {}", event.eventId, it)
        }
    }
}
