package com.subhrodip.squarewise.expensecore.groups.persistence.store

import com.subhrodip.squarewise.expensecore.groups.api.GroupMemberResponse
import com.subhrodip.squarewise.expensecore.groups.api.GroupResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Verifies the bounded default query behavior shared by group read adapters. */
class GroupQueryStoreTest {
    private val firstGroup = GroupResponse(UUID.randomUUID(), "First", "TRIP", 1)
    private val secondGroup = GroupResponse(UUID.randomUUID(), "Second", "TRIP", 2)
    private val store = object : GroupQueryStore {
        override fun list(subject: String): List<GroupResponse> = listOf(firstGroup, secondGroup)

        override fun listMembers(groupId: UUID, subject: String, limit: Int): List<GroupMemberResponse> = emptyList()
    }

    @Test
    fun `default bounded list truncates results and get resolves only matching group`() {
        assertEquals(listOf(firstGroup), store.list("alice", 1))
        assertEquals(secondGroup, store.get(secondGroup.groupId, "alice"))
        assertEquals(null, store.get(UUID.randomUUID(), "alice"))
    }

    @Test
    fun `default bounded list rejects limits outside the compatibility cap`() {
        assertThrows(IllegalArgumentException::class.java) { store.list("alice", 0) }
        assertThrows(IllegalArgumentException::class.java) { store.list("alice", 101) }
    }

    @Test
    fun `default member limit is forwarded as one hundred`() {
        var requestedLimit = 0
        val capturingStore = object : GroupQueryStore {
            override fun list(subject: String): List<GroupResponse> = emptyList()

            override fun listMembers(groupId: UUID, subject: String, limit: Int): List<GroupMemberResponse> {
                requestedLimit = limit
                return emptyList()
            }
        }

        capturingStore.listMembers(firstGroup.groupId, "alice")

        assertEquals(100, requestedLimit)
    }

    @Test
    fun `default impls bridge methods delegate properly`() {
        val defaultImplsClass = Class.forName("com.subhrodip.squarewise.expensecore.groups.persistence.store.GroupQueryStore\$DefaultImpls")
        val listMethod = defaultImplsClass.getMethod("list", GroupQueryStore::class.java, String::class.java, Int::class.javaPrimitiveType)
        assertEquals(listOf(firstGroup), listMethod.invoke(null, store, "alice", 1))

        val getMethod = defaultImplsClass.getMethod("get", GroupQueryStore::class.java, UUID::class.java, String::class.java)
        assertEquals(secondGroup, getMethod.invoke(null, store, secondGroup.groupId, "alice"))

        val listMembersDefault = defaultImplsClass.getMethod(
            "listMembers\$default",
            GroupQueryStore::class.java,
            UUID::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Any::class.java
        )
        assertEquals(emptyList<GroupMemberResponse>(), listMembersDefault.invoke(null, store, firstGroup.groupId, "alice", 0, 4, null))
    }
}
