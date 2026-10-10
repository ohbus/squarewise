package com.subhrodip.squarewise.notifications.inbox.persistence

import org.springframework.data.domain.Sort
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Spring Data JPA repository for [NotificationInboxEntity] operations.
 */
@Repository
interface NotificationInboxRepository : JpaRepository<NotificationInboxEntity, UUID> {
    /**
     * Retrieves all notifications for an authenticated subject using the specified sort order.
     *
     * @param subject Authenticated recipient subject.
     * @param sort Sort specification (typically occurredAt DESC, notificationId DESC).
     * @return List of matching inbox entities.
     */
    fun findAllBySubject(subject: String, sort: Sort): List<NotificationInboxEntity>

    /** Reads the first bounded inbox page in stable cursor order. */
    fun findBySubjectOrderByOccurredAtDescNotificationIdDesc(subject: String, pageable: Pageable): List<NotificationInboxEntity>

    /** Reads a bounded inbox page after a stable occurred-at/ID cursor. */
    @Query(
        """
        SELECT item FROM NotificationInboxEntity item
        WHERE item.subject = :subject
          AND (item.occurredAt < :occurredAt
               OR (item.occurredAt = :occurredAt AND item.notificationId < :notificationId))
        ORDER BY item.occurredAt DESC, item.notificationId DESC
        """
    )
    fun findPageAfter(
        @Param("subject") subject: String,
        @Param("occurredAt") occurredAt: Instant,
        @Param("notificationId") notificationId: UUID,
        pageable: Pageable
    ): List<NotificationInboxEntity>

    /**
     * Finds a single notification inbox entry by recipient subject and notification ID.
     *
     * @param subject Authenticated recipient subject.
     * @param notificationId Unique notification identifier.
     * @return Matching inbox entity or null if not found.
     */
    fun findBySubjectAndNotificationId(subject: String, notificationId: UUID): NotificationInboxEntity?
}
