package com.subhrodip.squarewise.notifications.inbox.persistence

import com.subhrodip.squarewise.notifications.inbox.model.InboxItem
/** Reader-side persistence port for historical inbox items. */
fun interface NotificationInboxQueryStore {
    /** Lists inbox items belonging to a subject. */
    fun list(subject: String): List<InboxItem>
}
