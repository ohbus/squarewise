package com.subhrodip.squarewise.notifications.inbox.persistence

import com.subhrodip.squarewise.notifications.inbox.model.InboxItem
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * JPA implementation of [NotificationInboxStore] backed by [NotificationInboxRepository].
 *
 * Provides transactional methods to append inbox notifications, list paginated items ordered
 * by occurrence timestamp and notification ID descending, and mark notifications as read.
 *
 * @property repository Spring Data JPA repository for notification inbox items.
 */
@Service
class JpaNotificationInboxStore(
    private val repository: NotificationInboxRepository
) : NotificationInboxStore {

    /**
     * Appends an inbox notification for the given subject.
     *
     * @param subject Authenticated recipient subject.
     * @param item Inbox notification item to persist.
     * @throws IllegalArgumentException if subject, event type, or message validation constraints fail.
     */
    @Transactional
    override fun append(subject: String, item: InboxItem) {
        repository.save(item.toEntity(requireSubject(subject)))
    }

    /**
     * Lists all inbox notification items for a subject ordered by occurredAt descending and notificationId descending.
     *
     * @param subject Authenticated recipient subject.
     * @return List of inbox notification items.
     * @throws IllegalArgumentException if subject validation fails.
     */
    @Transactional(readOnly = true)
    override fun list(subject: String): List<InboxItem> =
        repository.findAllBySubject(
            requireSubject(subject),
            Sort.by(
                Sort.Order.desc("occurredAt"),
                Sort.Order.desc("notificationId")
            )
        ).map(NotificationInboxEntity::toItem)

    /** Reads only the requested inbox page from PostgreSQL using the cursor index. */
    @Transactional(readOnly = true)
    override fun page(
        subject: String,
        afterOccurredAt: java.time.Instant?,
        afterNotificationId: UUID?,
        limit: Int
    ): List<InboxItem> {
        require(limit in 1..100) { "inbox page limit must be between 1 and 100" }
        val pageRequest = PageRequest.of(0, limit)
        val entities = if (afterOccurredAt == null || afterNotificationId == null) {
            repository.findBySubjectOrderByOccurredAtDescNotificationIdDesc(requireSubject(subject), pageRequest)
        } else {
            repository.findPageAfter(requireSubject(subject), afterOccurredAt, afterNotificationId, pageRequest)
        }
        return entities.map(NotificationInboxEntity::toItem)
    }

    /**
     * Marks a notification as read if found and owned by the specified subject.
     *
     * @param subject Authenticated recipient subject.
     * @param notificationId Identifier of the notification to mark as read.
     * @return `true` if the notification was found and marked as read; `false` otherwise.
     * @throws IllegalArgumentException if subject validation fails.
     */
    @Transactional
    override fun markAsRead(subject: String, notificationId: UUID): Boolean {
        val entity = repository.findBySubjectAndNotificationId(requireSubject(subject), notificationId)
            ?: return false
        entity.read = true
        repository.save(entity)
        return true
    }
}

private fun requireSubject(subject: String): String {
    require(subject.isNotBlank() && subject.length <= 200) {
        "authenticated subject must contain between 1 and 200 characters"
    }
    return subject
}

private fun InboxItem.toEntity(subject: String): NotificationInboxEntity {
    require(eventType.isNotBlank() && eventType.length <= 200) {
        "event type must contain between 1 and 200 characters"
    }
    require(message.isNotBlank() && message.length <= 2000) {
        "message must contain between 1 and 2000 characters"
    }
    return NotificationInboxEntity(notificationId, subject, eventType, message, occurredAt, read)
}

private fun NotificationInboxEntity.toItem() =
    InboxItem(notificationId, eventType, message, occurredAt, read)
