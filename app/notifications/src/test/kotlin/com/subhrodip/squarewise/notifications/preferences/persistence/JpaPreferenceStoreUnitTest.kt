package com.subhrodip.squarewise.notifications.preferences.persistence

import com.subhrodip.squarewise.notifications.preferences.cache.PreferenceCache
import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Unit tests verifying [JpaPreferenceStore] caching and direct invalidation behavior
 * outside active transaction synchronizations.
 */
class JpaPreferenceStoreUnitTest {

    private val repository = mock(NotificationPreferenceRepository::class.java)

    @Test
    fun `get leverages cache when not in write transaction and serves repeat lookups`() {
        val cache = PreferenceCache()
        val store = JpaPreferenceStore(repository, preferenceCache = cache)
        val entity = NotificationPreferenceEntity("alice", emailEnabled = false, pushEnabled = true)
        `when`(repository.findById("alice")).thenReturn(Optional.of(entity))

        val first = store.get("alice")
        assertEquals(NotificationPreferences(emailEnabled = false, pushEnabled = true), first)

        // Second call must resolve from cache without hitting repository again
        val second = store.get("alice")
        assertEquals(first, second)
        verify(repository, times(1)).findById("alice")
    }

    @Test
    fun `put invalidates cache directly when transaction synchronization is not active`() {
        val cache = PreferenceCache()
        val store = JpaPreferenceStore(repository, preferenceCache = cache)
        val subject = "bob"
        val existing = NotificationPreferenceEntity(subject, emailEnabled = true, pushEnabled = true)
        val updatedEntity = NotificationPreferenceEntity(subject, emailEnabled = false, pushEnabled = false)
        `when`(repository.findById(subject))
            .thenReturn(Optional.of(existing))
            .thenReturn(Optional.of(existing))
            .thenReturn(Optional.of(updatedEntity))
        `when`(repository.save(existing)).thenReturn(updatedEntity)

        // Preload cache (1st repository findById)
        val initial = store.get(subject)
        assertTrue(initial.emailEnabled)

        // Put new preferences (2nd repository findById)
        val updatedPrefs = NotificationPreferences(emailEnabled = false, pushEnabled = false)
        store.put(subject, updatedPrefs)

        assertFalse(existing.emailEnabled)
        assertFalse(existing.pushEnabled)
        verify(repository).save(existing)

        // Since cache was invalidated directly, next get reloads from repository (3rd repository findById)
        val reloaded = store.get(subject)
        assertFalse(reloaded.emailEnabled)
        assertFalse(reloaded.pushEnabled)
        verify(repository, times(3)).findById(subject)
    }

    @Test
    fun `put creates new entity when not found in repository`() {
        val cache = PreferenceCache()
        val store = JpaPreferenceStore(repository, preferenceCache = cache)
        val subject = "new-user"
        `when`(repository.findById(subject)).thenReturn(Optional.empty())

        val prefs = NotificationPreferences(emailEnabled = true, pushEnabled = false)
        store.put(subject, prefs)

        verify(repository).save(any(NotificationPreferenceEntity::class.java))
    }

    @Test
    fun `uncached store supports get and put without cache`() {
        val uncachedStore = JpaPreferenceStore(repository)
        val subject = "carol"
        `when`(repository.findById(subject)).thenReturn(Optional.empty())

        val prefs = uncachedStore.get(subject)
        assertTrue(prefs.emailEnabled)
        assertTrue(prefs.pushEnabled)

        uncachedStore.put(subject, NotificationPreferences(emailEnabled = false, pushEnabled = false))
        verify(repository).save(any(NotificationPreferenceEntity::class.java))
    }

    @Test
    fun `put executes cache invalidation in afterCommit callback when transaction synchronization is active`() {
        val cache = PreferenceCache()
        val store = JpaPreferenceStore(repository, preferenceCache = cache)
        val subject = "dave"
        val existing = NotificationPreferenceEntity(subject, emailEnabled = true, pushEnabled = true)
        `when`(repository.findById(subject)).thenReturn(Optional.of(existing))

        TransactionSynchronizationManager.initSynchronization()
        try {
            store.put(subject, NotificationPreferences(emailEnabled = false, pushEnabled = false))
            val synchronizations = TransactionSynchronizationManager.getSynchronizations()
            assertEquals(1, synchronizations.size)
            synchronizations.first().afterCommit()
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }
}
