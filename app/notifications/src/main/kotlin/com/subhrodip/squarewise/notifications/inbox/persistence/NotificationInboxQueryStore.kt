package com.subhrodip.squarewise.notifications.inbox.persistence

import com.subhrodip.squarewise.notifications.inbox.model.InboxItem
import java.time.Instant
import java.util.UUID
/** Reader-side persistence port for historical inbox items. */
interface NotificationInboxQueryStore {
    /** Returns at most one bounded compatibility page. */
    fun list(subject: String): List<InboxItem>

    /** Reads one bounded keyset page after the optional cursor. */
    fun page(subject: String, afterOccurredAt: Instant?, afterNotificationId: UUID?, limit: Int): List<InboxItem> =
        list(subject)
            .filter { item ->
                afterOccurredAt == null || afterNotificationId == null ||
                    item.occurredAt < afterOccurredAt ||
                    (item.occurredAt == afterOccurredAt && item.notificationId < afterNotificationId)
            }
            .take(limit)
}
