package com.subhrodip.squarewise.notifications.preferences.persistence

import com.subhrodip.squarewise.db.routing.DbReader
import com.subhrodip.squarewise.db.routing.DbWriter
import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences

/** Port for reading and updating per-subject notification preferences. */
interface PreferenceStore {
    @DbReader("notifications.preferences.query")
    fun get(subject: String): NotificationPreferences

    @DbWriter("notifications.preferences.command")
    fun put(subject: String, value: NotificationPreferences)
}
