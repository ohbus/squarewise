package com.subhrodip.squarewise.notifications.preferences.persistence

import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * JPA implementation of [PreferenceStore] backed by [NotificationPreferenceRepository].
 *
 * Persists and retrieves user delivery preferences, defaulting to enabled preferences
 * if no custom preference record exists yet.
 *
 * @property repository Spring Data JPA repository for notification preferences.
 */
@Service
class JpaPreferenceStore(
    private val repository: NotificationPreferenceRepository
) : PreferenceStore {

    /**
     * Retrieves the notification preferences for the specified subject.
     * Returns default preferences (both email and push enabled) if not explicitly configured.
     *
     * @param subject Authenticated recipient subject.
     * @return Current or default [NotificationPreferences].
     * @throws IllegalArgumentException if subject validation fails.
     */
    @Transactional(readOnly = true)
    override fun get(subject: String): NotificationPreferences =
        repository.findById(requireSubject(subject))
            .map(NotificationPreferenceEntity::toPreferences)
            .orElseGet(::NotificationPreferences)

    /**
     * Creates or updates the notification preferences for the specified subject.
     *
     * @param subject Authenticated recipient subject.
     * @param value Updated [NotificationPreferences] settings.
     * @throws IllegalArgumentException if subject validation fails.
     */
    @Transactional
    override fun put(subject: String, value: NotificationPreferences) {
        val validatedSubject = requireSubject(subject)
        val entity = repository.findById(validatedSubject).orElseGet {
            NotificationPreferenceEntity(validatedSubject, value.emailEnabled, value.pushEnabled)
        }
        entity.emailEnabled = value.emailEnabled
        entity.pushEnabled = value.pushEnabled
        repository.save(entity)
    }

    private fun requireSubject(subject: String): String {
        require(subject.isNotBlank() && subject.length <= 200) {
            "authenticated subject must contain between 1 and 200 characters"
        }
        return subject
    }
}

private fun NotificationPreferenceEntity.toPreferences() =
    NotificationPreferences(emailEnabled = emailEnabled, pushEnabled = pushEnabled)
