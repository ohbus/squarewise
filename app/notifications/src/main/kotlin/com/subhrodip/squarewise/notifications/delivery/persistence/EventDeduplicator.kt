package com.subhrodip.squarewise.notifications.delivery.persistence

import java.util.UUID

/** Port used to ensure a notification event is processed at most once. */
fun interface EventDeduplicator {
    /** Returns true only for the first delivery of an event id. */
    fun firstDelivery(eventId: UUID): Boolean
}
