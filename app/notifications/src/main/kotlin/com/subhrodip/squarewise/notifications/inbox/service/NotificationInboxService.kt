package com.subhrodip.squarewise.notifications.inbox.service

import com.subhrodip.squarewise.notifications.inbox.model.InboxItem
import com.subhrodip.squarewise.notifications.inbox.model.InboxPage
import com.subhrodip.squarewise.notifications.inbox.persistence.NotificationInboxStore
import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.ReadConsistency
import com.subhrodip.squarewise.errors.catalog.NotificationErrors
import com.subhrodip.squarewise.notifications.errors.NotificationDomainException
import com.subhrodip.squarewise.observability.db.DbTelemetry
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.springframework.stereotype.Service

/** Application service for inbox writes and cursor-paginated reads. */
@Service
class NotificationInboxService(private val store: NotificationInboxStore, private val dbTelemetry: DbTelemetry = DbTelemetry()) {
    fun append(subject: String, item: InboxItem) = store.append(subject, item)
    fun list(subject: String): List<InboxItem> = store.list(subject)
    fun markAsRead(subject: String, notificationId: UUID): Boolean = store.markAsRead(subject, notificationId)
    fun page(subject: String, cursor: String?, limit: Int): InboxPage {
        require(limit in 1..100) { "inbox page limit must be between 1 and 100" }
        val key = cursor?.let(::decodeCursor)
        val page = dbTelemetry.measureQuery("notification.inbox.history", "approved-query") {
            DbContextHolder.withContext(DbExecutionContext("notification.inbox.history", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)) {
                store.page(subject, key?.first, key?.second, limit)
            }
        }
        return InboxPage(page, page.lastOrNull()?.let(::encodeCursor).takeIf { page.size == limit })
    }
    private fun encodeCursor(item: InboxItem): String = Base64.getUrlEncoder().withoutPadding().encodeToString("${item.occurredAt}|${item.notificationId}".toByteArray())
    private fun decodeCursor(cursor: String): Pair<Instant, UUID> = runCatching { String(Base64.getUrlDecoder().decode(cursor)).split('|').also { require(it.size == 2) }.let { Instant.parse(it[0]) to UUID.fromString(it[1]) } }.getOrElse { throw NotificationDomainException(NotificationErrors.INBOX_CURSOR_INVALID, "invalid inbox cursor", it) }
}
