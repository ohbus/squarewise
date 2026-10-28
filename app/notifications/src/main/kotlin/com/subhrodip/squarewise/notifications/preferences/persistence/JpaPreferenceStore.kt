package com.subhrodip.squarewise.notifications.preferences.persistence

import com.subhrodip.squarewise.db.routing.DbReader
import com.subhrodip.squarewise.db.routing.DbWriter
import com.subhrodip.squarewise.notifications.preferences.cache.PreferenceCache
import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * JPA implementation of [PreferenceStore] backed by [NotificationPreferenceRepository].
 *
 * Persists and retrieves user delivery preferences, defaulting to enabled preferences
 * if no custom preference record exists yet.
 *
 * @property repository Spring Data JPA repository for notification preferences.
 * @property preferenceCache Optional in-memory cache for fast hot-path preference lookups.
 */
@Service
class JpaPreferenceStore(
    private val repository: NotificationPreferenceRepository,
    @Autowired(required = false)
    private val preferenceCache: PreferenceCache? = null
) : PreferenceStore {

    /**
     * Retrieves the notification preferences for the specified subject.
     * Returns default preferences (both email and push enabled) if not explicitly configured.
     *
     * @param subject Authenticated recipient subject.
     * @return Current or default [NotificationPreferences].
     * @throws IllegalArgumentException if subject validation fails.
     */
    @DbReader("notifications.preferences.get", target = "preferences")
    @Transactional(readOnly = true)
    override fun get(subject: String): NotificationPreferences {
        val validatedSubject = requireSubject(subject)
        val inWriteTransaction = TransactionSynchronizationManager.isActualTransactionActive() &&
            !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        return if (preferenceCache != null && !inWriteTransaction) {
            preferenceCache.get(validatedSubject) { loadPreferences(validatedSubject) }
        } else {
            loadPreferences(validatedSubject)
        }
    }

    private fun loadPreferences(validatedSubject: String): NotificationPreferences =
        repository.findById(validatedSubject)
            .map(NotificationPreferenceEntity::toPreferences)
            .orElseGet(::NotificationPreferences)

    /**
     * Creates or updates the notification preferences for the specified subject.
     *
     * @param subject Authenticated recipient subject.
     * @param value Updated [NotificationPreferences] settings.
     * @throws IllegalArgumentException if subject validation fails.
     */
    @DbWriter("notifications.preferences.put")
    @Transactional
    override fun put(subject: String, value: NotificationPreferences) {
        val validatedSubject = requireSubject(subject)
        val entity = repository.findById(validatedSubject).orElseGet {
            NotificationPreferenceEntity(validatedSubject, value.emailEnabled, value.pushEnabled)
        }
        entity.emailEnabled = value.emailEnabled
        entity.pushEnabled = value.pushEnabled
        repository.save(entity)
        invalidateAfterCommit(validatedSubject)
    }

    private fun invalidateAfterCommit(subject: String) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        preferenceCache?.invalidate(subject)
                    }
                }
            )
        } else {
            preferenceCache?.invalidate(subject)
        }
    }

    private fun requireSubject(subject: String): String {
        require(subject.isNotBlank() && subject.length <= 256) {
            "authenticated subject must contain between 1 and 256 characters"
        }
        return subject
    }
}

private fun NotificationPreferenceEntity.toPreferences() =
    NotificationPreferences(emailEnabled = emailEnabled, pushEnabled = pushEnabled)
