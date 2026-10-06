package com.subhrodip.squarewise.expensecore.groups.persistence.store

import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupInvitationRepository
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreateInviteRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreatePlaceholderRequest
import com.subhrodip.squarewise.expensecore.groups.api.GroupMemberResponse
import com.subhrodip.squarewise.expensecore.groups.api.GroupResponse
import com.subhrodip.squarewise.expensecore.groups.api.InviteResponse
import com.subhrodip.squarewise.expensecore.groups.api.UpdateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.domain.GroupAuditEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupInvitationEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore
import com.subhrodip.squarewise.expensecore.sync.persistence.SynchronizationStore
import com.subhrodip.squarewise.ids.generation.UuidGenerator
import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.DbOperationKind
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors

/**
 * JPA persistence adapter implementing [GroupStore] for group lifecycle operations,
 * memberships, placeholders, invitations, audit logging, synchronization records, and transactional outbox events.
 *
 * Invariants:
 * - Mutating operations run within transactional boundaries.
 * - Group renames, archiving, placeholder creation, member removals, and invite actions atomically append an audit log entry, sync record, and outbox event.
 * - Invitations are strictly validated by format, expiration, and revocation state before claiming.
 */
@Primary
@Service
class JpaGroupStore(
    private val groups: GroupRepository,
    private val memberships: GroupMembershipRepository,
    private val invitations: GroupInvitationRepository,
    private val audit: GroupAuditCommandStore,
    private val synchronization: SynchronizationStore,
    private val outbox: OutboxStore
) : GroupStore {

    /**
     * Creates a new expense group with the calling subject as the initial active member.
     */
    @Transactional
    override fun create(subject: String, request: CreateGroupRequest): GroupResponse {
        val entity = groups.save(
            GroupEntity(
                groupId = UuidGenerator.next(),
                name = request.name.trim(),
                kind = request.kind,
                currency = request.currency,
                status = "ACTIVE"
            )
        )
        addMembership(entity.groupId, subject)
        return entity.toResponse()
    }

    /**
     * Lists all groups in which the given user subject is an active member.
     */
    @Transactional(readOnly = true)
    override fun list(subject: String): List<GroupResponse> =
        DbContextHolder.withContext(DbExecutionContext("groups.list", DbOperationKind.QUERY)) {
            memberships.findAllBySubjectAndStatusOrderByMembershipId(subject, "ACTIVE")
                .mapNotNull { groups.findById(it.groupId).orElse(null) }
                .filter { it.status == "ACTIVE" }
                .map { it.toResponse() }
        }

    /**
     * Updates an existing expense group's name and increments its revision.
     */
    @Transactional
    override fun update(groupId: UUID, subject: String, request: UpdateGroupRequest): GroupResponse {
        checkActiveMembership(groupId, subject)
        val entity = groups.findForMembershipUpdate(groupId) ?: notFound()
        checkActiveGroup(entity)
        entity.name = request.name.trim()
        entity.revision += 1
        val saved = groups.save(entity)
        val occurredAt = Instant.now()
        val payload = mapOf(
            "groupId" to groupId.toString(),
            "name" to saved.name,
            "revision" to saved.revision,
            "changedBy" to subject
        )
        recordMutation(groupId, subject, "group.renamed", saved.revision, payload, occurredAt, "group.renamed.v1")
        return saved.toResponse()
    }

    /**
     * Archives an active group, transitioning its status to ARCHIVED and incrementing revision.
     */
    @Transactional
    override fun archive(groupId: UUID, subject: String): GroupResponse {
        checkActiveMembership(groupId, subject)
        val entity = groups.findForMembershipUpdate(groupId) ?: notFound()
        if (entity.status == "ARCHIVED") {
            throw ExpenseDomainException(ExpenseErrors.ERR_06, "Group is already archived")
        }
        entity.status = "ARCHIVED"
        entity.revision += 1
        val saved = groups.save(entity)
        val occurredAt = Instant.now()
        val payload = mapOf(
            "groupId" to groupId.toString(),
            "status" to saved.status,
            "revision" to saved.revision,
            "changedBy" to subject
        )
        recordMutation(groupId, subject, "group.archived", saved.revision, payload, occurredAt, "group.archived.v1")
        return saved.toResponse()
    }

    /**
     * Creates a named placeholder member within an active group.
     */
    @Transactional
    override fun addPlaceholder(
        groupId: UUID,
        subject: String,
        request: CreatePlaceholderRequest
    ): GroupMemberResponse {
        checkActiveMembership(groupId, subject)
        val group = groups.findForMembershipUpdate(groupId) ?: notFound()
        checkActiveGroup(group)
        val membershipId = UuidGenerator.next()
        val entity = memberships.save(
            GroupMembershipEntity(
                membershipId = membershipId,
                groupId = groupId,
                subject = null,
                displayName = request.name.trim(),
                isPlaceholder = true,
                status = "ACTIVE"
            )
        )
        group.revision += 1
        groups.save(group)
        val occurredAt = Instant.now()
        val payload = mapOf(
            "groupId" to groupId.toString(),
            "membershipId" to membershipId.toString(),
            "displayName" to entity.displayName.orEmpty(),
            "revision" to group.revision,
            "changedBy" to subject
        )
        recordMutation(groupId, subject, "member.placeholder_added", group.revision, payload, occurredAt, "member.placeholder_added.v1")
        return entity.toResponse()
    }

    /**
     * Soft-removes a member from an active group while preserving historical financial records.
     */
    @Transactional
    override fun removeMember(groupId: UUID, subject: String, membershipId: UUID) {
        checkActiveMembership(groupId, subject)
        val group = groups.findForMembershipUpdate(groupId) ?: notFound()
        checkActiveGroup(group)
        val target = memberships.findByMembershipIdAndGroupId(membershipId, groupId) ?: notFound()
        if (target.status == "REMOVED") {
            throw ExpenseDomainException(ExpenseErrors.ERR_06, "Member is already removed")
        }
        target.status = "REMOVED"
        memberships.save(target)
        group.revision += 1
        groups.save(group)
        val occurredAt = Instant.now()
        val payload = mapOf(
            "groupId" to groupId.toString(),
            "membershipId" to membershipId.toString(),
            "targetSubject" to target.subject.orEmpty(),
            "displayName" to target.displayName.orEmpty(),
            "revision" to group.revision,
            "changedBy" to subject
        )
        recordMutation(groupId, subject, "member.removed", group.revision, payload, occurredAt, "member.removed.v1")
    }

    /**
     * Lists all active members and placeholders registered in the specified group.
     */
    @Transactional(readOnly = true)
    override fun listMembers(groupId: UUID, subject: String): List<GroupMemberResponse> {
        checkActiveMembership(groupId, subject)
        val group = groups.findById(groupId).orElse(null) ?: notFound()
        if (group.status != "ACTIVE") notFound()
        return memberships.findByGroupIdAndStatus(groupId, "ACTIVE").map { it.toResponse() }
    }

    /**
     * Generates a new invitation token for joining an active group (optionally targeting a placeholder).
     */
    @Transactional
    override fun invite(groupId: UUID, subject: String, request: CreateInviteRequest): InviteResponse {
        checkActiveMembership(groupId, subject)
        val group = groups.findById(groupId).orElse(null) ?: notFound()
        checkActiveGroup(group)
        if (request.placeholderId != null) {
            val placeholder = memberships.findByMembershipIdAndGroupId(request.placeholderId, groupId)
            if (placeholder == null || !placeholder.isPlaceholder || placeholder.status != "ACTIVE" || placeholder.subject != null) {
                throw ExpenseDomainException(ExpenseErrors.ERR_06, "Placeholder not found or already bound")
            }
        }
        val token = invitationToken()
        val expiresAt = Instant.now().plus(Duration.ofHours(request.expiresInHours.toLong()))
        invitations.save(GroupInvitationEntity(token, groupId, expiresAt, placeholderId = request.placeholderId))
        return InviteResponse(token, expiresAt, request.placeholderId)
    }

    /**
     * Revokes a pending invitation token within an active group.
     */
    @Transactional
    override fun revokeInvite(groupId: UUID, subject: String, token: String) {
        checkActiveMembership(groupId, subject)
        val group = groups.findForMembershipUpdate(groupId) ?: notFound()
        checkActiveGroup(group)
        val occurredAt = Instant.now()
        if (invitations.revokeIfAvailable(token, groupId, occurredAt) != 1) {
            conflict("Invite is invalid, already claimed, or already revoked")
        }
        group.revision += 1
        groups.save(group)
        val payload = mapOf(
            "groupId" to groupId.toString(),
            "token" to token,
            "revision" to group.revision,
            "changedBy" to subject
        )
        recordMutation(groupId, subject, "invitation.revoked", group.revision, payload, occurredAt, "invitation.revoked.v1")
    }

    /**
     * Claims an invitation token, adding the caller subject or binding to a targeted placeholder.
     */
    @Transactional
    override fun claim(token: String, subject: String): GroupResponse {
        if (!token.matches(INVITATION_TOKEN)) conflict("Invite token is invalid")
        val invitation = invitations.findById(token).orElseThrow { conflict("Invite not found") }
        if (invitation.claimedAt != null || invitation.revokedAt != null || !invitation.expiresAt.isAfter(Instant.now())) {
            conflict("Invite is invalid, expired, or already claimed/revoked")
        }
        val group = groups.findForMembershipUpdate(invitation.groupId) ?: notFound()
        checkActiveGroup(group)

        if (invitation.placeholderId != null) {
            val placeholder = memberships.findByMembershipIdAndGroupId(invitation.placeholderId!!, group.groupId)
                ?: conflict("Placeholder not found")
            if (placeholder.status != "ACTIVE" || placeholder.subject != null) {
                conflict("Placeholder is no longer available")
            }
            val claimedAt = Instant.now()
            if (invitations.claimIfAvailable(token, subject, claimedAt) != 1) {
                conflict("Invite could not be claimed")
            }
            placeholder.subject = subject
            placeholder.isPlaceholder = false
            memberships.save(placeholder)
            group.revision += 1
            val saved = groups.save(group)
            val payload = mapOf(
                "groupId" to group.groupId.toString(),
                "token" to token,
                "claimedBy" to subject,
                "placeholderId" to invitation.placeholderId?.toString(),
                "revision" to saved.revision
            )
            recordMutation(group.groupId, subject, "invitation.claimed", saved.revision, payload, claimedAt, "invitation.claimed.v1")
            return saved.toResponse()
        } else {
            if (memberships.existsByGroupIdAndSubjectAndStatus(group.groupId, subject, "ACTIVE")) {
                return group.toResponse()
            }
            val claimedAt = Instant.now()
            if (invitations.claimIfAvailable(token, subject, claimedAt) != 1) {
                conflict("Invite could not be claimed")
            }
            addMembership(group.groupId, subject)
            group.revision += 1
            val saved = groups.save(group)
            val payload = mapOf(
                "groupId" to group.groupId.toString(),
                "token" to token,
                "claimedBy" to subject,
                "revision" to saved.revision
            )
            recordMutation(group.groupId, subject, "invitation.claimed", saved.revision, payload, claimedAt, "invitation.claimed.v1")
            return saved.toResponse()
        }
    }

    private fun addMembership(groupId: UUID, subject: String) {
        if (!memberships.existsByGroupIdAndSubjectAndStatus(groupId, subject, "ACTIVE")) {
            memberships.save(
                GroupMembershipEntity(
                    membershipId = UuidGenerator.next(),
                    groupId = groupId,
                    subject = subject,
                    displayName = null,
                    isPlaceholder = false,
                    status = "ACTIVE"
                )
            )
        }
    }

    private fun checkActiveMembership(groupId: UUID, subject: String) {
        if (!memberships.existsByGroupIdAndSubjectAndStatus(groupId, subject, "ACTIVE")) {
            notFound()
        }
    }

    private fun checkActiveGroup(group: GroupEntity) {
        if (group.status == "ARCHIVED") {
            throw ExpenseDomainException(ExpenseErrors.ERR_06, "Group is archived")
        }
    }

    private fun recordMutation(
        groupId: UUID,
        subject: String,
        action: String,
        revision: Long,
        payload: Map<String, Any?>,
        occurredAt: Instant,
        eventType: String
    ) {
        val payloadStr = payload.toString()
        audit.save(
            GroupAuditEntity(
                auditId = UuidGenerator.next(),
                groupId = groupId,
                subject = subject,
                action = action,
                revision = revision,
                payload = payloadStr,
                occurredAt = occurredAt
            )
        )
        synchronization.append(groupId.toString(), groupId.toString(), payloadStr)
        outbox.append(
            OutboxMessage(
                eventId = UuidGenerator.next(),
                eventType = eventType,
                aggregateId = groupId,
                groupId = groupId,
                groupRevision = revision,
                occurredAt = occurredAt,
                payload = payload
            )
        )
    }

    private fun invitationToken(): String =
        UuidGenerator.next().toString().replace("-", "") + UuidGenerator.next().toString().replace("-", "")

    private fun conflict(message: String): Nothing =
        throw ExpenseDomainException(ExpenseErrors.ERR_06, message)

    private fun notFound(): Nothing =
        throw ExpenseDomainException(ExpenseErrors.ERR_05, "Group not found")

    private companion object {
        val INVITATION_TOKEN = Regex("^[a-f0-9]{64}$")
    }
}

private fun GroupEntity.toResponse() = GroupResponse(groupId, name, kind, revision, status)

private fun GroupMembershipEntity.toResponse() =
    GroupMemberResponse(membershipId, groupId, subject, displayName, isPlaceholder, status)
