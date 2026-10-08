package com.subhrodip.squarewise.notifications.email.delivery

import java.time.Instant

/** Broker-decoded protected authentication-email event. */
data class AuthEmailDeliveryEvent(
    val eventId: String,
    val recipient: String,
    val template: String,
    val encryptedCredential: String,
    val expiresAt: Instant
)
