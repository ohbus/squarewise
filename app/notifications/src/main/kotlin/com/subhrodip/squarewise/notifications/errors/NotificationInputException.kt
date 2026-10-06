package com.subhrodip.squarewise.notifications.errors

/** Typed invalid-input failure for Notifications crypto and configuration boundaries. */
class NotificationInputException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
