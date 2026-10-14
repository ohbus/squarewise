package com.subhrodip.squarewise.expensecore.groups.persistence.store

import com.subhrodip.squarewise.expensecore.groups.api.GroupMemberResponse
import com.subhrodip.squarewise.expensecore.groups.api.GroupResponse
import com.subhrodip.squarewise.db.routing.DbReader
import java.util.UUID

/** Reader-side persistence port for groups and memberships. */
@DbReader("groups.query")
interface GroupQueryStore {
    /** Lists groups in which the subject has an active membership. */
    fun list(subject: String): List<GroupResponse>

    /** Lists at most [limit] groups so a caller cannot materialize an unbounded collection. */
    fun list(subject: String, limit: Int): List<GroupResponse> {
        require(limit in 1..100) { "group limit must be between 1 and 100" }
        return list(subject).take(limit)
    }

    /** Resolves one group for a member without scanning the member's group list. */
    fun get(groupId: UUID, subject: String): GroupResponse? =
        list(subject).firstOrNull { it.groupId == groupId }

    /** Lists at most [limit] active members and placeholders in a group. */
    fun listMembers(groupId: UUID, subject: String, limit: Int = 100): List<GroupMemberResponse>
}
