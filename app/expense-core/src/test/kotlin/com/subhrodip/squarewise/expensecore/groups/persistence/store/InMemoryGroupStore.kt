package com.subhrodip.squarewise.expensecore.groups.persistence.store

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreateInviteRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreatePlaceholderRequest
import com.subhrodip.squarewise.expensecore.groups.api.GroupMemberResponse
import com.subhrodip.squarewise.expensecore.groups.api.GroupResponse
import com.subhrodip.squarewise.expensecore.groups.api.InviteResponse
import com.subhrodip.squarewise.expensecore.groups.api.UpdateGroupRequest
import com.subhrodip.squarewise.ids.generation.UuidGenerator
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe in-memory implementation of [GroupStore] used for testing.
 */
class InMemoryGroupStore : GroupStore {
    private val groupsByMember = ConcurrentHashMap<String, MutableSet<UUID>>()
    private val groups = ConcurrentHashMap<UUID, GroupResponse>()
    private val invites = ConcurrentHashMap<String, InviteData>()
    private val memberships = ConcurrentHashMap<UUID, MutableList<GroupMemberResponse>>()

    private data class InviteData(
        val groupId: UUID,
        val expiresAt: Instant,
        val placeholderId: UUID? = null,
        var claimedAt: Instant? = null,
        var claimedBy: String? = null,
        var revokedAt: Instant? = null
    )

    override fun create(subject: String, request: CreateGroupRequest): GroupResponse {
        val group = GroupResponse(UuidGenerator.next(), request.name.trim(), request.kind, 0, "ACTIVE")
        groups[group.groupId] = group
        groupsByMember.computeIfAbsent(subject) { ConcurrentHashMap.newKeySet() }.add(group.groupId)
        memberships.computeIfAbsent(group.groupId) { CopyOnWriteArrayList() }
            .add(GroupMemberResponse(UuidGenerator.next(), group.groupId, subject, null, false, "ACTIVE"))
        return group
    }

    override fun list(subject: String): List<GroupResponse> =
        groupsByMember[subject].orEmpty().mapNotNull(groups::get).filter { it.status == "ACTIVE" }

    override fun update(groupId: UUID, subject: String, request: UpdateGroupRequest): GroupResponse {
        if (groupsByMember[subject]?.contains(groupId) != true) {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        }
        val existing = groups[groupId]
            ?: throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        if (existing.status == "ARCHIVED") {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_CONFLICT, "Group is archived")
        }
        val updated = existing.copy(name = request.name.trim(), revision = existing.revision + 1)
        groups[groupId] = updated
        return updated
    }

    override fun archive(groupId: UUID, subject: String): GroupResponse {
        if (groupsByMember[subject]?.contains(groupId) != true) {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        }
        val existing = groups[groupId]
            ?: throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        if (existing.status == "ARCHIVED") {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_CONFLICT, "Group is already archived")
        }
        val updated = existing.copy(status = "ARCHIVED", revision = existing.revision + 1)
        groups[groupId] = updated
        return updated
    }

    override fun addPlaceholder(groupId: UUID, subject: String, request: CreatePlaceholderRequest): GroupMemberResponse {
        if (groupsByMember[subject]?.contains(groupId) != true) {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        }
        val group = groups[groupId]
            ?: throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        if (group.status == "ARCHIVED") {
            throw ExpenseDomainException(PlatformErrors.ACCESS_DENIED, "Group is archived")
        }
        val member = GroupMemberResponse(
            membershipId = UuidGenerator.next(),
            groupId = groupId,
            subject = null,
            displayName = request.name.trim(),
            isPlaceholder = true,
            status = "ACTIVE"
        )
        memberships.computeIfAbsent(groupId) { CopyOnWriteArrayList() }.add(member)
        groups[groupId] = group.copy(revision = group.revision + 1)
        return member
    }

    override fun removeMember(groupId: UUID, subject: String, membershipId: UUID) {
        if (groupsByMember[subject]?.contains(groupId) != true) {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        }
        val group = groups[groupId]
            ?: throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        if (group.status == "ARCHIVED") {
            throw ExpenseDomainException(PlatformErrors.ACCESS_DENIED, "Group is archived")
        }
        val list = memberships[groupId]
            ?: throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Member not found")
        val index = list.indexOfFirst { it.membershipId == membershipId }
        if (index == -1) {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Member not found")
        }
        val existing = list[index]
        if (existing.status == "REMOVED") {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_CONFLICT, "Member is already removed")
        }
        list[index] = existing.copy(status = "REMOVED")
        if (existing.subject != null) {
            groupsByMember[existing.subject]?.remove(groupId)
        }
        groups[groupId] = group.copy(revision = group.revision + 1)
    }

    override fun listMembers(groupId: UUID, subject: String): List<GroupMemberResponse> {
        if (groupsByMember[subject]?.contains(groupId) != true) {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        }
        if (groups[groupId]?.status != "ACTIVE") {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        }
        return memberships[groupId].orEmpty().filter { it.status == "ACTIVE" }
    }

    override fun invite(groupId: UUID, subject: String, request: CreateInviteRequest): InviteResponse {
        if (groupsByMember[subject]?.contains(groupId) != true) {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        }
        val group = groups[groupId]
            ?: throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        if (group.status == "ARCHIVED") {
            throw ExpenseDomainException(PlatformErrors.ACCESS_DENIED, "Group is archived")
        }
        if (request.placeholderId != null) {
            val list = memberships[groupId].orEmpty()
            val target = list.find { it.membershipId == request.placeholderId }
            if (target == null || !target.isPlaceholder || target.status != "ACTIVE" || target.subject != null) {
                throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Placeholder not found or already bound")
            }
        }
        val token = UuidGenerator.next().toString().replace("-", "") + UuidGenerator.next().toString().replace("-", "")
        val expiry = Instant.now().plus(Duration.ofHours(request.expiresInHours.toLong()))
        invites[token] = InviteData(groupId, expiry, request.placeholderId)
        return InviteResponse(token, expiry, request.placeholderId)
    }

    override fun revokeInvite(groupId: UUID, subject: String, token: String) {
        if (groupsByMember[subject]?.contains(groupId) != true) {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        }
        val group = groups[groupId]
            ?: throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
        if (group.status == "ARCHIVED") {
            throw ExpenseDomainException(PlatformErrors.ACCESS_DENIED, "Group is archived")
        }
        val data = invites[token]
            ?: throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Invite not found")
        if (data.groupId != groupId || data.claimedAt != null || data.revokedAt != null) {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_CONFLICT, "Invite cannot be revoked")
        }
        data.revokedAt = Instant.now()
        groups[groupId] = group.copy(revision = group.revision + 1)
    }

    override fun claim(token: String, subject: String): GroupResponse {
        if (!token.matches(Regex("^[a-f0-9]{64}$"))) {
            throw ExpenseDomainException(PlatformErrors.RESOURCE_CONFLICT, "Invite is invalid or already claimed")
        }
        val data = invites[token]
            ?: throw ExpenseDomainException(PlatformErrors.RESOURCE_CONFLICT, "Invite is invalid or already claimed")
        synchronized(data) {
            if (data.claimedAt != null || data.revokedAt != null || !data.expiresAt.isAfter(Instant.now())) {
                throw ExpenseDomainException(PlatformErrors.RESOURCE_CONFLICT, "Invite is invalid, expired, or already claimed/revoked")
            }
            val group = groups[data.groupId]
                ?: throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Group not found")
            if (group.status == "ARCHIVED") {
                throw ExpenseDomainException(PlatformErrors.ACCESS_DENIED, "Group is archived")
            }

            val memberList = memberships.computeIfAbsent(group.groupId) { CopyOnWriteArrayList() }
            if (data.placeholderId != null) {
                val index = memberList.indexOfFirst { it.membershipId == data.placeholderId }
                if (index == -1) {
                    throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Placeholder not found")
                }
                val target = memberList[index]
                if (target.status != "ACTIVE" || target.subject != null) {
                    throw ExpenseDomainException(PlatformErrors.RESOURCE_CONFLICT, "Placeholder is no longer available")
                }
                data.claimedAt = Instant.now()
                data.claimedBy = subject
                memberList[index] = target.copy(subject = subject, isPlaceholder = false)
            } else {
                if (memberList.any { it.subject == subject && it.status == "ACTIVE" }) {
                    return group
                }
                data.claimedAt = Instant.now()
                data.claimedBy = subject
                memberList.add(GroupMemberResponse(UuidGenerator.next(), group.groupId, subject, null, false, "ACTIVE"))
            }
            groupsByMember.computeIfAbsent(subject) { ConcurrentHashMap.newKeySet() }.add(group.groupId)
            val updatedGroup = group.copy(revision = group.revision + 1)
            groups[group.groupId] = updatedGroup
            return updatedGroup
        }
    }
}
