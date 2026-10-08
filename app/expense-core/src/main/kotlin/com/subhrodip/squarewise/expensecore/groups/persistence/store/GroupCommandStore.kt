package com.subhrodip.squarewise.expensecore.groups.persistence.store

import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreateInviteRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreatePlaceholderRequest
import com.subhrodip.squarewise.expensecore.groups.api.GroupMemberResponse
import com.subhrodip.squarewise.expensecore.groups.api.GroupResponse
import com.subhrodip.squarewise.expensecore.groups.api.InviteResponse
import com.subhrodip.squarewise.expensecore.groups.api.UpdateGroupRequest
import java.util.UUID

/** Writer-side persistence port for groups, memberships, and invitations. */
interface GroupCommandStore {
    /** Creates a new group owned by the creator. */
    fun create(subject: String, request: CreateGroupRequest): GroupResponse
    /** Updates group metadata and increments its revision. */
    fun update(groupId: UUID, subject: String, request: UpdateGroupRequest): GroupResponse
    /** Archives an active group. */
    fun archive(groupId: UUID, subject: String): GroupResponse
    /** Adds a named placeholder participant. */
    fun addPlaceholder(groupId: UUID, subject: String, request: CreatePlaceholderRequest): GroupMemberResponse
    /** Soft-removes a member while retaining historical records. */
    fun removeMember(groupId: UUID, subject: String, membershipId: UUID)
    /** Generates a time-limited invitation. */
    fun invite(groupId: UUID, subject: String, request: CreateInviteRequest): InviteResponse
    /** Revokes a pending invitation. */
    fun revokeInvite(groupId: UUID, subject: String, token: String)
    /** Claims an active invitation. */
    fun claim(token: String, subject: String): GroupResponse
}
