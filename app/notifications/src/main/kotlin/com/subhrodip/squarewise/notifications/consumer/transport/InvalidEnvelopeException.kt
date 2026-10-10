package com.subhrodip.squarewise.notifications.consumer.transport

import com.subhrodip.squarewise.errors.catalog.NotificationErrors
import com.subhrodip.squarewise.notifications.errors.NotificationDomainException

/** Raised when a broker envelope violates the notification event contract. */
class InvalidEnvelopeException(message: String, cause: Throwable? = null) :
    NotificationDomainException(NotificationErrors.NOTIFICATION_PAYLOAD_CORRUPT, message, cause)
