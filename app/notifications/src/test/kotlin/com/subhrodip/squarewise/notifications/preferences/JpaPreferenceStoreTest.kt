package com.subhrodip.squarewise.notifications.preferences

import com.subhrodip.squarewise.notifications.preferences.api.PreferenceController
import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences
import com.subhrodip.squarewise.notifications.preferences.persistence.InMemoryPreferenceStore
import com.subhrodip.squarewise.notifications.preferences.persistence.JpaPreferenceStore
import com.subhrodip.squarewise.notifications.preferences.persistence.NotificationPreferenceRepository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:notification_preferences;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
    ]
)
@Transactional
class JpaPreferenceStoreTest @Autowired constructor(
    private val store: JpaPreferenceStore,
    private val repository: NotificationPreferenceRepository
) {
    @Test
    fun `returns defaults without creating a persistence row`() {
        assertEquals(NotificationPreferences(), store.get("alice"))
        assertEquals(0, repository.count())
    }

    @Test
    fun `persists and updates preferences by authenticated subject`() {
        store.put("alice", NotificationPreferences(emailEnabled = false, pushEnabled = true))
        store.put("alice", NotificationPreferences(emailEnabled = false, pushEnabled = false))

        assertEquals(
            NotificationPreferences(emailEnabled = false, pushEnabled = false),
            store.get("alice")
        )
        assertEquals(1, repository.count())
    }

    @Test
    fun `rejects blank subject before repository access`() {
        assertThrows(IllegalArgumentException::class.java) {
            store.get("   ")
        }
        assertEquals(0, repository.count())
    }

    @Test
    fun `accepts the shared maximum subject length`() {
        store.put("a".repeat(256), NotificationPreferences())
        assertEquals(1, repository.count())
    }

    @Test
    fun `rejects subject longer than the durable column contract`() {
        assertThrows(IllegalArgumentException::class.java) {
            store.put("a".repeat(257), NotificationPreferences())
        }
        assertEquals(0, repository.count())
    }

    @Test
    fun `store without cache queries repository directly`() {
        val storeWithoutCache = JpaPreferenceStore(repository, preferenceCache = null)
        assertEquals(NotificationPreferences(), storeWithoutCache.get("bob"))
        storeWithoutCache.put("bob", NotificationPreferences(emailEnabled = true, pushEnabled = false))
        assertEquals(NotificationPreferences(emailEnabled = true, pushEnabled = false), storeWithoutCache.get("bob"))
    }
}
