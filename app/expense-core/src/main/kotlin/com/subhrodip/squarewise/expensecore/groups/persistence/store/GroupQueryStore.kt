package com.subhrodip.squarewise.expensecore.groups.persistence.store

import com.subhrodip.squarewise.expensecore.groups.api.GroupMemberResponse
import com.subhrodip.squarewise.expensecore.groups.api.GroupResponse
import java.util.UUID

/** Reader-side persistence port for groups and memberships. */
interface GroupQueryStore {
    /** Lists groups in which the subject has an active membership. */
    fun list(subject: String): List<GroupResponse>
    /** Lists active members and placeholders in a group. */
    fun listMembers(groupId: UUID, subject: String): List<GroupMemberResponse>
}
