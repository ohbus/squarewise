package com.subhrodip.squarewise.notifications.inbox
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertThrows

import com.subhrodip.squarewise.notifications.inbox.persistence.JpaNotificationInboxStore
import com.subhrodip.squarewise.notifications.inbox.persistence.NotificationInboxRepository

import com.subhrodip.squarewise.notifications.inbox.api.InboxController
import com.subhrodip.squarewise.notifications.inbox.model.InboxItem
import com.subhrodip.squarewise.notifications.inbox.persistence.InMemoryNotificationInboxStore
import com.subhrodip.squarewise.notifications.inbox.persistence.NotificationInboxStore
import com.subhrodip.squarewise.notifications.inbox.service.NotificationInboxService

import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.ReadConsistency
import com.subhrodip.squarewise.errors.http.GlobalErrorHandler
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.security.Principal
import java.time.Instant
import java.util.UUID

class InboxControllerTest {
    private val recordingStore = RecordingInboxStore(InMemoryNotificationInboxStore())
    private val inbox = NotificationInboxService(recordingStore)
    private val controller = InboxController(inbox)
    private val mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(GlobalErrorHandler()).build()
    private val user = RequestPostProcessor { request -> request.userPrincipal = Principal { "alice" }; request }

    @Test
    fun `historical inbox listing uses the approved eventual reader policy`() {
        mvc.perform(get(ApiEndpoints.Notifications.V1.PATH_INBOX).with(user))
            .andExpect(status().isOk)

        assertEquals("notification.inbox.history", recordingStore.lastContext?.operationName)
        assertEquals(DbOperationKind.QUERY, recordingStore.lastContext?.kind)
        assertEquals(ReadConsistency.EVENTUAL, recordingStore.lastContext?.consistency)
        assertTrue(recordingStore.lastContext?.readerEligible == true)
    }

    @Test
    fun `orders inbox newest first and isolates subjects`() {
        val testInbox = NotificationInboxService(InMemoryNotificationInboxStore())
        testInbox.append("alice", InboxItem(UUID.randomUUID(), "expense.created", "Expense", Instant.EPOCH))
        testInbox.append("alice", InboxItem(UUID.randomUUID(), "repayment.created", "Repayment", Instant.ofEpochSecond(2)))
        testInbox.append("bob", InboxItem(UUID.randomUUID(), "expense.created", "Other", Instant.now()))
        assertEquals("repayment.created", testInbox.list("alice").first().eventType)
        assertEquals(2, testInbox.list("alice").size)
        assertEquals(1, testInbox.list("bob").size)
    }

    @Test
    fun `returns stable cursor pages`() {
        val testInbox = NotificationInboxService(InMemoryNotificationInboxStore())
        repeat(3) { index -> testInbox.append("alice", InboxItem(UUID.randomUUID(), "event-$index", "Event", Instant.ofEpochSecond(index.toLong()))) }
        val first = testInbox.page("alice", null, 2)
        assertEquals(listOf("event-2", "event-1"), first.items.map { it.eventType })
        val second = testInbox.page("alice", first.nextCursor, 2)
        assertEquals(listOf("event-0"), second.items.map { it.eventType })
        assertEquals(null, second.nextCursor)
    }

    @Test
    fun `rejects malformed cursor`() {
        val testInbox = NotificationInboxService(InMemoryNotificationInboxStore())
        val err = assertThrows(SquarewiseException::class.java) {
            testInbox.page("alice", "bad", 10)
        }
        assertEquals("VALIDATION_FAILED", err.definition.legacyCode)
    }

    @Test
    fun `marks existing notification as read returning 204`() {
        val notificationId = UUID.randomUUID()
        inbox.append("alice", InboxItem(notificationId, "expense.created", "Expense", Instant.now(), read = false))

        mvc.perform(post(ApiEndpoints.Notifications.V1.inboxMarkRead(notificationId)).with(user))
            .andExpect(status().isNoContent)

        val items = inbox.list("alice")
        assertEquals(1, items.size)
        assertTrue(items.first().read)
    }

    @Test
    fun `returns 404 when marking unknown notification as read`() {
        val unknownId = UUID.randomUUID()
        mvc.perform(post(ApiEndpoints.Notifications.V1.inboxMarkRead(unknownId)).with(user))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `returns 404 when marking another user notification as read`() {
        val notificationId = UUID.randomUUID()
        inbox.append("bob", InboxItem(notificationId, "expense.created", "Expense", Instant.now(), read = false))

        mvc.perform(post(ApiEndpoints.Notifications.V1.inboxMarkRead(notificationId)).with(user))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `rejects unauthenticated inbox listing`() {
        mvc.perform(get(ApiEndpoints.Notifications.V1.PATH_INBOX))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `rejects blank subject when listing the inbox`() {
        val blankSubject = RequestPostProcessor { request -> request.userPrincipal = Principal { "   " }; request }

        mvc.perform(get(ApiEndpoints.Notifications.V1.PATH_INBOX).with(blankSubject))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `rejects blank subject when marking an inbox item as read`() {
        val blankSubject = RequestPostProcessor { request -> request.userPrincipal = Principal { "   " }; request }

        mvc.perform(post(ApiEndpoints.Notifications.V1.inboxMarkRead(UUID.randomUUID())).with(blankSubject))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `rejects invalid inbox page limits with the validation application code`() {
        mvc.perform(get(ApiEndpoints.Notifications.V1.PATH_INBOX).with(user).param("limit", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `page limit violation is represented by the validation application exception`() {
        val error = assertThrows(SquarewiseException::class.java) {
            controller.list(Principal { "alice" }, null, 101)
        }

        assertEquals("VALIDATION_FAILED", error.definition.legacyCode)
    }

    @Test
    fun `rejects malformed inbox cursors as validation errors`() {
        mvc.perform(
            get(ApiEndpoints.Notifications.V1.PATH_INBOX)
                .with(user)
                .param("cursor", "not-a-valid-cursor")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `returns an empty page when a valid cursor is after all stored items`() {
        val testInbox = NotificationInboxService(InMemoryNotificationInboxStore())
        testInbox.append("alice", InboxItem(UUID.randomUUID(), "event", "Event", Instant.EPOCH))

        val cursor = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("1969-01-01T00:00:00Z|00000000-0000-7000-8000-000000000001".toByteArray())
        val page = testInbox.page("alice", cursor, 10)

        assertTrue(page.items.isEmpty())
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun `in memory store markAsRead updates status correctly`() {
        val store = InMemoryNotificationInboxStore()
        val id = UUID.randomUUID()
        store.append("alice", InboxItem(id, "expense.created", "Expense", Instant.now(), read = false))

        assertFalse(store.markAsRead("bob", id))
        assertFalse(store.markAsRead("alice", UUID.randomUUID()))
        assertTrue(store.markAsRead("alice", id))
        assertTrue(store.list("alice").first().read)
    }
}

private class RecordingInboxStore(private val delegate: NotificationInboxStore) : NotificationInboxStore {
    var lastContext: DbExecutionContext? = null

    override fun append(subject: String, item: InboxItem) = delegate.append(subject, item)

    override fun list(subject: String): List<InboxItem> {
        lastContext = DbContextHolder.current()
        return delegate.list(subject)
    }

    override fun markAsRead(subject: String, notificationId: UUID): Boolean =
        delegate.markAsRead(subject, notificationId)
}
