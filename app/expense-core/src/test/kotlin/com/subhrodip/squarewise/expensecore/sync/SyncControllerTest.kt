package com.subhrodip.squarewise.expensecore.sync

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import com.subhrodip.squarewise.expensecore.sync.api.SyncController
import com.subhrodip.squarewise.expensecore.sync.persistence.InMemorySynchronizationStore
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.groups.cache.GroupAccessCache
import com.subhrodip.squarewise.expensecore.validation.EntityReferenceValidator

import com.subhrodip.squarewise.errors.web.GlobalErrorAdvice
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.security.Principal
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints

class SyncControllerTest {
    private val store = InMemorySynchronizationStore(Clock.systemUTC())
    private val memberships: GroupMembershipRepository = mock(GroupMembershipRepository::class.java)
    private val groups: GroupRepository = mock(GroupRepository::class.java)
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(
        SyncController(store, EntityReferenceValidator(groups, memberships))
    ).setControllerAdvice(GlobalErrorAdvice()).build()
    private val user = RequestPostProcessor { request -> request.userPrincipal = Principal { "alice" }; request }
    private val groupId = UUID.randomUUID()

    init {
        `when`(memberships.existsByGroupIdAndSubjectAndStatus(groupId, "alice", "ACTIVE")).thenReturn(true)
    }

    @Test
    fun `hides sync data from non-members`() {
        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSyncSnapshot(groupId)).with(RequestPostProcessor { request ->
            request.userPrincipal = Principal { "bob" }
            request
        }))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `returns ordered changes and cursor`() {
        store.append(groupId.toString(), "expense-1", "{}")
        store.delete(groupId.toString(), "expense-2")
        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSyncChanges(groupId)).with(user).param("limit", "1"))
            .andExpect(status().isOk).andExpect(jsonPath("$.changes[0].revision").value(1)).andExpect(jsonPath("$.hasMore").value(true))
    }

    @Test
    fun `rejects invalid cursor and limit`() {
        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSyncSnapshot(groupId)).with(user).param("cursor", "bad"))
            .andExpect(status().isUnprocessableContent)
        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSyncSnapshot(groupId)).with(user).param("limit", "101"))
            .andExpect(status().isUnprocessableContent)
        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSyncSnapshot(groupId)).with(user).param("limit", "0"))
            .andExpect(status().isUnprocessableContent)
    }

    /** Verifies a blank authenticated subject cannot access synchronization data. */
    @Test
    fun `rejects blank authenticated subject`() {
        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupSyncSnapshot(groupId)).with(
                RequestPostProcessor { request ->
                    request.userPrincipal = Principal { " " }
                    request
                }
            )
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects a cursor created for another group`() {
        val otherGroup = UUID.randomUUID()
        store.append(otherGroup.toString(), "expense-1", "{}")
        val cursor = store.snapshot(otherGroup.toString(), null, 1).nextCursor!!
        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSyncChanges(groupId)).with(user).param("cursor", cursor))
            .andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `rejects an expired cursor through both public sync routes`() {
        val otherStore = InMemorySynchronizationStore(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
        otherStore.append(groupId.toString(), "expense-1", "{}")
        val cursor = otherStore.snapshot(groupId.toString(), null, 1).nextCursor!!
        val expiredStore = InMemorySynchronizationStore(Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), ZoneOffset.UTC))
        val expiredMvc = MockMvcBuilders.standaloneSetup(
            SyncController(expiredStore, EntityReferenceValidator(groups, memberships))
        ).setControllerAdvice(GlobalErrorAdvice()).build()

        expiredMvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSyncSnapshot(groupId)).with(user).param("cursor", cursor))
            .andExpect(status().isUnprocessableContent)
        expiredMvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSyncChanges(groupId)).with(user).param("cursor", cursor))
            .andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `page with groupAccessCache successfully checks cached active membership`() {
        val cache = GroupAccessCache()
        val cachedMvc = MockMvcBuilders.standaloneSetup(
            SyncController(store, EntityReferenceValidator(groups, memberships, cache))
        )
            .setControllerAdvice(GlobalErrorAdvice())
            .build()

        cachedMvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSyncSnapshot(groupId)).with(user))
            .andExpect(status().isOk)

        // Repeat call: exercises the cache hit branch in GroupAccessCache.isMember
        cachedMvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSyncSnapshot(groupId)).with(user))
            .andExpect(status().isOk)
    }

    @Test
    fun `page with groupAccessCache rejects non-member`() {
        val cache = GroupAccessCache()
        val cachedMvc = MockMvcBuilders.standaloneSetup(
            SyncController(store, EntityReferenceValidator(groups, memberships, cache))
        )
            .setControllerAdvice(GlobalErrorAdvice())
            .build()

        cachedMvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSyncSnapshot(groupId)).with(RequestPostProcessor { request ->
            request.userPrincipal = Principal { "charlie" }
            request
        }))
            .andExpect(status().isNotFound)
    }
}
