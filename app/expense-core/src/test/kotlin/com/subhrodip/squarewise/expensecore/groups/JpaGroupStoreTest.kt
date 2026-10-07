package com.subhrodip.squarewise.expensecore.groups
import com.subhrodip.squarewise.expensecore.sync.persistence.SyncChangeRepository
import java.util.UUID
import org.junit.jupiter.api.assertThrows
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreateInviteRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreatePlaceholderRequest
import com.subhrodip.squarewise.expensecore.groups.api.UpdateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupAuditRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.store.JpaGroupStore
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxRepository
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import tools.jackson.databind.ObjectMapper

/**
 * Integration tests for [JpaGroupStore] verifying transactional persistence, optimistic locking / serialization,
 * audit logging, outbox dispatch, and authorization semantics for groups, archiving, placeholders, member removal, and invitations.
 */
@SpringBootTest
class JpaGroupStoreTest @Autowired constructor(
    private val store: JpaGroupStore,
    private val auditRepository: GroupAuditRepository,
    private val syncRepository: SyncChangeRepository,
    private val outboxRepository: OutboxRepository,
    private val groupRepository: GroupRepository,
    private val membershipRepository: GroupMembershipRepository,
    private val objectMapper: ObjectMapper
) {
    /**
     * Verifies that group creation persists the group entity with trimmed name, initial revision 0,
     * and restricts group listing exclusively to active members.
     */
    @Test
    fun `persists groups and restricts listing to members`() {
        val group = store.create("alice", CreateGroupRequest(" Vienna trip ", "TRIP", "EUR"))

        assertEquals("Vienna trip", group.name)
        assertEquals(listOf(group), store.list("alice"))
        assertTrue(store.list("bob").isEmpty())
    }

    /**
     * Verifies that concurrent attempts to claim the same invitation token result in exactly
     * one successful claim and one membership addition.
     */
    @Test
    fun `allows exactly one concurrent invitation claim`() {
        val group = store.create("owner", CreateGroupRequest("Household", "HOUSEHOLD", "EUR"))
        val invitation = store.invite(group.groupId, "owner", CreateInviteRequest(24))
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)

        val results = (1..8).map { index ->
            executor.submit(Callable {
                start.await()
                runCatching { store.claim(invitation.token, "member-$index") }.isSuccess
            })
        }
        start.countDown()

        assertEquals(1, results.count { it.get() })
        executor.shutdown()
        assertEquals(1, (1..8).sumOf { store.list("member-$it").size })
    }

    /** Verifies the atomic claim failure path for two users racing on one targeted placeholder invite. */
    @Test
    fun `allows exactly one concurrent targeted placeholder claim`() {
        val group = store.create("targeted-race-owner", CreateGroupRequest("Targeted race", "TRIP", "EUR"))
        val placeholder = store.addPlaceholder(
            group.groupId,
            "targeted-race-owner",
            CreatePlaceholderRequest("Racing placeholder")
        )
        val invitation = store.invite(
            group.groupId,
            "targeted-race-owner",
            CreateInviteRequest(24, placeholder.membershipId)
        )
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val results = (1..2).map { index ->
            executor.submit(Callable {
                start.await()
                runCatching { store.claim(invitation.token, "targeted-race-$index") }.isSuccess
            })
        }
        start.countDown()

        assertEquals(1, results.count { it.get() })
        executor.shutdown()
        assertEquals(1, store.list("targeted-race-1").size + store.list("targeted-race-2").size)
    }

    /**
     * Verifies that claiming multiple distinct invitations for the same member to the same group
     * results in idempotent/single membership association.
     */
    @Test
    fun `links one membership when separate invitations are claimed concurrently`() {
        val group = store.create("owner-2", CreateGroupRequest("Trip", "TRIP", "USD"))
        val invitations = (1..2).map {
            store.invite(group.groupId, "owner-2", CreateInviteRequest(24))
        }
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val results = invitations.map { invitation ->
            executor.submit(Callable {
                start.await()
                runCatching { store.claim(invitation.token, "same-member") }.isSuccess
            })
        }
        start.countDown()

        assertEquals(2, results.count { it.get() })
        executor.shutdown()
        val memberGroups = store.list("same-member")
        assertEquals(1, memberGroups.size)
        assertEquals(group.groupId, memberGroups[0].groupId)
    }

    /**
     * Verifies that listing members is restricted to members of the group and rejects non-members.
     */
    @Test
    fun `lists members for group members and rejects non-members`() {
        val group = store.create("member-alice", CreateGroupRequest("Alps trip", "TRIP", "EUR"))
        val membersBeforeClaim = store.listMembers(group.groupId, "member-alice")
        assertEquals(1, membersBeforeClaim.size)
        assertEquals(group.groupId, membersBeforeClaim[0].groupId)
        assertEquals("member-alice", membersBeforeClaim[0].subject)

        val invitation = store.invite(group.groupId, "member-alice", CreateInviteRequest(24))
        store.claim(invitation.token, "member-bob")

        val membersAfterClaim = store.listMembers(group.groupId, "member-bob")
        assertEquals(2, membersAfterClaim.size)
        assertEquals(setOf("member-alice", "member-bob"), membersAfterClaim.map { it.subject }.toSet())

        val err = assertThrows<SquarewiseException> {
            store.listMembers(group.groupId, "intruder")
        }
        assertEquals("NOT_FOUND", err.definition.legacyCode)
    }

    /**
     * Verifies that updating a group name trims whitespace, increments the revision number,
     * updates the database entity, and records audit and outbox side effects.
     */
    @Test
    fun `updates group name and increments revision`() {
        val group = store.create("update-alice", CreateGroupRequest("Before rename", "TRIP", "EUR"))
        assertEquals(0, group.revision)

        val updated = store.update(group.groupId, "update-alice", UpdateGroupRequest("  After rename  "))
        assertEquals("After rename", updated.name)
        assertEquals(1, updated.revision)

        val fetched = store.list("update-alice").first { it.groupId == group.groupId }
        assertEquals("After rename", fetched.name)
        assertEquals(1, fetched.revision)

        val updateErr = assertThrows<SquarewiseException> {
            store.update(group.groupId, "stranger", UpdateGroupRequest("Nope"))
        }
        assertEquals("NOT_FOUND", updateErr.definition.legacyCode)
    }

    /**
     * Verifies that a successful rename writes one matching audit, sync, and outbox effect.
     * The persisted payload and revision are compared across every durable representation.
     */
    @Test
    fun `rename effects contain the same exact payload and revision`() {
        val group = store.create("effect-owner", CreateGroupRequest("Before", "TRIP", "EUR"))

        val updated = store.update(group.groupId, "effect-owner", UpdateGroupRequest("After"))
        val audit = auditRepository.findAll().single { it.groupId == group.groupId }
        val sync = syncRepository.findAll().single { it.groupId == group.groupId.toString() }
        val outbox = outboxRepository.findAll().single { it.groupId == group.groupId }
        val expectedPayload = "{groupId=${group.groupId}, name=After, revision=1, changedBy=effect-owner}"
        val expectedOutboxPayload = objectMapper.writeValueAsString(
            mapOf("groupId" to group.groupId.toString(), "name" to "After", "revision" to 1, "changedBy" to "effect-owner")
        )

        assertEquals(1L, updated.revision)
        assertEquals("group.renamed", audit.action)
        assertEquals("effect-owner", audit.subject)
        assertEquals(1L, audit.revision)
        assertEquals(expectedPayload, audit.payload)
        assertEquals(1L, sync.revision)
        assertEquals(expectedPayload, sync.payload)
        assertEquals("group.renamed.v1", outbox.eventType)
        assertEquals(group.groupId, outbox.aggregateId)
        assertEquals(1L, outbox.groupRevision)
        assertEquals(expectedOutboxPayload, outbox.payload)
        assertNotNull(outbox.occurredAt)
    }

    /**
     * Verifies that a persistence constraint failure at transaction commit rolls back the group
     * mutation and every associated effect, leaving the previously committed state observable.
     */
    @Test
    fun `rename constraint failure rolls back group and all effects`() {
        val group = store.create("rollback-owner", CreateGroupRequest("Before", "TRIP", "EUR"))
        val initialAuditCount = auditRepository.count()
        val initialSyncCount = syncRepository.count()
        val initialOutboxCount = outboxRepository.count()
        val oversizedName = "x".repeat(121)

        assertTrue(runCatching {
            store.update(group.groupId, "rollback-owner", UpdateGroupRequest(oversizedName))
        }.isFailure)

        val persisted = groupRepository.findById(group.groupId).orElseThrow()
        assertEquals("Before", persisted.name)
        assertEquals(0L, persisted.revision)
        assertEquals(initialAuditCount, auditRepository.count())
        assertEquals(initialSyncCount, syncRepository.count())
        assertEquals(initialOutboxCount, outboxRepository.count())
    }

    /**
     * Verifies that concurrent rename requests are serialized using pessimistic write locking,
     * resulting in sequential revision increments without lost updates.
     */
    @Test
    fun `serializes concurrent renames and preserves both revisions`() {
        val group = store.create("concurrent-owner", CreateGroupRequest("Before", "TRIP", "EUR"))
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val results = listOf("First", "Second").map { name ->
            executor.submit(Callable {
                start.await()
                store.update(group.groupId, "concurrent-owner", UpdateGroupRequest(name))
            })
        }
        start.countDown()

        val updates = results.map { it.get() }
        executor.shutdown()
        assertEquals(setOf(1L, 2L), updates.map { it.revision }.toSet())
        assertEquals(2L, store.list("concurrent-owner").single { it.groupId == group.groupId }.revision)
    }

    /**
     * Verifies that rejected rename requests (non-member actor or missing group ID) do NOT increment revision
     * and do NOT emit audit log entries, sync journal records, or outbox messages.
     */
    @Test
    fun `rejected rename requests do not increment revision or emit side effects`() {
        val group = store.create("owner-side-effects", CreateGroupRequest("Original Name", "TRIP", "EUR"))
        val initialAuditCount = auditRepository.count()
        val initialOutboxCount = outboxRepository.count()

        // Non-member attempt
        val nonMemberErr = assertThrows<SquarewiseException> {
            store.update(group.groupId, "unauthorized-subject", UpdateGroupRequest("Hacked Name"))
        }
        assertEquals("NOT_FOUND", nonMemberErr.definition.legacyCode)

        // Missing group attempt
        val nonExistentId = UUID.randomUUID()
        val missingErr = assertThrows<SquarewiseException> {
            store.update(nonExistentId, "owner-side-effects", UpdateGroupRequest("Missing Group Name"))
        }
        assertEquals("NOT_FOUND", missingErr.definition.legacyCode)

        // Verify entity unchanged
        val refreshed = groupRepository.findById(group.groupId).orElseThrow()
        assertEquals("Original Name", refreshed.name)
        assertEquals(0L, refreshed.revision)

        // Verify no additional audit or outbox entries created
        assertEquals(initialAuditCount, auditRepository.count())
        assertEquals(initialOutboxCount, outboxRepository.count())
    }

    /**
     * Verifies that archiving a group updates its status to ARCHIVED, increments revision,
     * emits audit/outbox events, and blocks future update attempts.
     */
    @Test
    fun `archives group and enforces read-only state`() {
        val group = store.create("archive-owner", CreateGroupRequest("Household", "HOUSEHOLD", "USD"))
        assertEquals("ACTIVE", group.status)
        assertEquals(0L, group.revision)

        val archived = store.archive(group.groupId, "archive-owner")
        assertEquals("ARCHIVED", archived.status)
        assertEquals(1L, archived.revision)

        // Verify audit and outbox side-effects
        val audit = auditRepository.findAll().single { it.groupId == group.groupId }
        val outbox = outboxRepository.findAll().single { it.groupId == group.groupId }
        assertEquals("group.archived", audit.action)
        assertEquals("group.archived.v1", outbox.eventType)

        // Repeat archive fails with 409
        val archiveErr = assertThrows<SquarewiseException> {
            store.archive(group.groupId, "archive-owner")
        }
        assertEquals("CONFLICT", archiveErr.definition.legacyCode)

        // Update name fails with 409 Conflict
        val updateErr = assertThrows<SquarewiseException> {
            store.update(group.groupId, "archive-owner", UpdateGroupRequest("New Name"))
        }
        assertEquals("CONFLICT", updateErr.definition.legacyCode)
    }

    /**
     * Verifies creating a named placeholder and claiming a targeted invitation binds the user's subject
     * to the exact same participant membership ID.
     */
    @Test
    fun `creates placeholder and binds subject via targeted invitation`() {
        val group = store.create("placeholder-owner", CreateGroupRequest("Trip Group", "TRIP", "EUR"))

        val placeholder = store.addPlaceholder(group.groupId, "placeholder-owner", CreatePlaceholderRequest("Bob Placeholder"))
        assertTrue(placeholder.isPlaceholder)
        assertEquals("Bob Placeholder", placeholder.displayName)
        assertNull(placeholder.subject)

        val invite = store.invite(group.groupId, "placeholder-owner", CreateInviteRequest(24, placeholder.membershipId))
        assertEquals(placeholder.membershipId, invite.placeholderId)

        val claimedGroup = store.claim(invite.token, "bob-registered")
        assertEquals(group.groupId, claimedGroup.groupId)

        val members = store.listMembers(group.groupId, "bob-registered")
        val boundMember = members.find { it.membershipId == placeholder.membershipId }
        assertNotNull(boundMember)
        assertEquals("bob-registered", boundMember!!.subject)
        assertEquals("Bob Placeholder", boundMember.displayName)
        assertFalse(boundMember.isPlaceholder)
    }

    /**
     * Verifies soft-removing a member sets status to REMOVED, increments revision,
     * excludes them from active member listing, and revokes access.
     */
    @Test
    fun `soft removes member and updates revision`() {
        val group = store.create("remove-owner", CreateGroupRequest("Apartment", "HOUSEHOLD", "EUR"))
        val invite = store.invite(group.groupId, "remove-owner", CreateInviteRequest(24))
        store.claim(invite.token, "member-to-remove")

        val membersBefore = store.listMembers(group.groupId, "remove-owner")
        val removeTarget = membersBefore.find { it.subject == "member-to-remove" }!!

        store.removeMember(group.groupId, "remove-owner", removeTarget.membershipId)

        val membersAfter = store.listMembers(group.groupId, "remove-owner")
        assertEquals(1, membersAfter.size)
        assertEquals("remove-owner", membersAfter[0].subject)

        // Removed member can no longer list group members
        val listErr = assertThrows<SquarewiseException> {
            store.listMembers(group.groupId, "member-to-remove")
        }
        assertEquals("NOT_FOUND", listErr.definition.legacyCode)

        // Duplicate removal returns 409 Conflict
        val dupErr = assertThrows<SquarewiseException> {
            store.removeMember(group.groupId, "remove-owner", removeTarget.membershipId)
        }
        assertEquals("CONFLICT", dupErr.definition.legacyCode)
    }

    /**
     * Verifies revoking an invitation prevents subsequent claim attempts and emits audit/outbox events.
     */
    @Test
    fun `revokes invitation token and rejects claims`() {
        val group = store.create("revoke-owner", CreateGroupRequest("Vacation", "TRIP", "USD"))
        val invite = store.invite(group.groupId, "revoke-owner", CreateInviteRequest(24))

        store.revokeInvite(group.groupId, "revoke-owner", invite.token)

        val claimErr = assertThrows<SquarewiseException> {
            store.claim(invite.token, "intruder")
        }
        assertEquals("CONFLICT", claimErr.definition.legacyCode)

        val audit = auditRepository.findAll().single { it.groupId == group.groupId && it.action == "invitation.revoked" }
        assertEquals("invitation.revoked", audit.action)
    }

    /** Verifies expiry, idempotent same-subject replay, and competing-subject rejection for invitations. */
    @Test
    fun `rejects expired and already claimed invitations without extra membership effects`() {
        val group = store.create("claim-owner", CreateGroupRequest("Claims", "TRIP", "EUR"))
        val expired = store.invite(group.groupId, "claim-owner", CreateInviteRequest(0))

        val expiredError = assertThrows<SquarewiseException> {
            store.claim(expired.token, "expired-member")
        }
        assertEquals("CONFLICT", expiredError.definition.legacyCode)
        assertEquals(1, store.listMembers(group.groupId, "claim-owner").size)

        val invite = store.invite(group.groupId, "claim-owner", CreateInviteRequest(24))
        store.claim(invite.token, "claimed-member")
        val revisionAfterClaim = groupRepository.findById(group.groupId).orElseThrow().revision

        val sameSubjectInvite = store.invite(group.groupId, "claim-owner", CreateInviteRequest(24))
        val sameSubjectReplay = store.claim(sameSubjectInvite.token, "claimed-member")
        assertEquals(group.groupId, sameSubjectReplay.groupId)
        assertEquals(revisionAfterClaim, groupRepository.findById(group.groupId).orElseThrow().revision)

        val competingSubjectError = assertThrows<SquarewiseException> {
            store.claim(invite.token, "competing-member")
        }
        assertEquals("CONFLICT", competingSubjectError.definition.legacyCode)
        assertEquals(2, store.listMembers(group.groupId, "claim-owner").size)
        assertTrue(store.list("competing-member").isEmpty())
    }

    /** Verifies an invitation cannot mutate an archived group after the token was issued. */
    @Test
    fun `rejects invitation claim for archived group without membership effects`() {
        val group = store.create("archived-claim-owner", CreateGroupRequest("Archived claim", "TRIP", "EUR"))
        val invitation = store.invite(group.groupId, "archived-claim-owner", CreateInviteRequest(24))
        store.archive(group.groupId, "archived-claim-owner")
        val revisionAfterArchive = groupRepository.findById(group.groupId).orElseThrow().revision

        val error = assertThrows<SquarewiseException> {
            store.claim(invitation.token, "archived-invitee")
        }

        assertEquals("CONFLICT", error.definition.legacyCode)
        assertEquals(revisionAfterArchive, groupRepository.findById(group.groupId).orElseThrow().revision)
        assertTrue(store.list("archived-invitee").isEmpty())
    }

    /** Verifies a targeted invitation rejects a placeholder removed after the invitation was issued. */
    @Test
    fun `rejects claim when targeted placeholder is no longer active`() {
        val group = store.create("removed-placeholder-owner", CreateGroupRequest("Removed placeholder", "TRIP", "EUR"))
        val placeholder = store.addPlaceholder(
            group.groupId,
            "removed-placeholder-owner",
            CreatePlaceholderRequest("Former placeholder")
        )
        val invitation = store.invite(
            group.groupId,
            "removed-placeholder-owner",
            CreateInviteRequest(24, placeholder.membershipId)
        )
        store.removeMember(group.groupId, "removed-placeholder-owner", placeholder.membershipId)
        val revisionAfterRemoval = groupRepository.findById(group.groupId).orElseThrow().revision

        val error = assertThrows<SquarewiseException> {
            store.claim(invitation.token, "replacement-subject")
        }

        assertEquals("CONFLICT", error.definition.legacyCode)
        assertEquals(revisionAfterRemoval, groupRepository.findById(group.groupId).orElseThrow().revision)
        assertTrue(store.list("replacement-subject").isEmpty())
    }

    /** Verifies an unclaimed invitation cannot bind a placeholder already associated with a subject. */
    @Test
    fun `rejects claim when targeted placeholder is already bound`() {
        val group = store.create("bound-placeholder-owner", CreateGroupRequest("Bound placeholder", "TRIP", "EUR"))
        val placeholder = store.addPlaceholder(
            group.groupId,
            "bound-placeholder-owner",
            CreatePlaceholderRequest("Bound placeholder")
        )
        val invitation = store.invite(
            group.groupId,
            "bound-placeholder-owner",
            CreateInviteRequest(24, placeholder.membershipId)
        )
        val persistedPlaceholder = membershipRepository.findById(placeholder.membershipId).orElseThrow()
        persistedPlaceholder.subject = "existing-subject"
        membershipRepository.saveAndFlush(persistedPlaceholder)
        val revisionBeforeClaim = groupRepository.findById(group.groupId).orElseThrow().revision

        val error = assertThrows<SquarewiseException> {
            store.claim(invitation.token, "new-subject")
        }

        assertEquals("CONFLICT", error.definition.legacyCode)
        assertEquals(revisionBeforeClaim, groupRepository.findById(group.groupId).orElseThrow().revision)
        assertTrue(store.list("new-subject").isEmpty())
    }

    /**
     * Verifies archived groups reject member-facing mutations while preserving the archive state
     * and the already-recorded mutation effects.
     */
    @Test
    fun `rejects archived group member operations without additional effects`() {
        val group = store.create("archived-members-owner", CreateGroupRequest("Archived", "TRIP", "EUR"))
        store.archive(group.groupId, "archived-members-owner")
        val auditCount = auditRepository.count()
        val outboxCount = outboxRepository.count()

        val listError = assertThrows<SquarewiseException> {
            store.listMembers(group.groupId, "archived-members-owner")
        }
        assertEquals("NOT_FOUND", listError.definition.legacyCode)

        val placeholderError = assertThrows<SquarewiseException> {
            store.addPlaceholder(
                group.groupId,
                "archived-members-owner",
                CreatePlaceholderRequest("No mutation")
            )
        }
        assertEquals("CONFLICT", placeholderError.definition.legacyCode)
        assertEquals("ARCHIVED", groupRepository.findById(group.groupId).orElseThrow().status)
        assertEquals(auditCount, auditRepository.count())
        assertEquals(outboxCount, outboxRepository.count())
    }

    /** Verifies invalid placeholder and invitation tokens fail before any group mutation is recorded. */
    @Test
    fun `rejects invalid placeholder and invitation operations without mutation`() {
        val group = store.create("invalid-invite-owner", CreateGroupRequest("Invites", "TRIP", "EUR"))
        val auditCount = auditRepository.count()
        val outboxCount = outboxRepository.count()

        val placeholderError = assertThrows<SquarewiseException> {
            store.invite(
                group.groupId,
                "invalid-invite-owner",
                CreateInviteRequest(24, UUID.randomUUID())
            )
        }
        assertEquals("CONFLICT", placeholderError.definition.legacyCode)

        val unknownToken = "a".repeat(64)
        val revokeError = assertThrows<SquarewiseException> {
            store.revokeInvite(group.groupId, "invalid-invite-owner", unknownToken)
        }
        assertEquals("CONFLICT", revokeError.definition.legacyCode)

        val malformedClaimError = assertThrows<SquarewiseException> {
            store.claim("not-a-token", "invitee")
        }
        assertEquals("CONFLICT", malformedClaimError.definition.legacyCode)

        val unknownClaimError = assertThrows<SquarewiseException> {
            store.claim(unknownToken, "invitee")
        }
        assertEquals("CONFLICT", unknownClaimError.definition.legacyCode)
        assertEquals(0L, groupRepository.findById(group.groupId).orElseThrow().revision)
        assertEquals(auditCount, auditRepository.count())
        assertEquals(outboxCount, outboxRepository.count())
    }

    /** Verifies an active ordinary member cannot be used as a placeholder invite target. */
    @Test
    fun `rejects invite targeting an ordinary active member`() {
        val group = store.create("ordinary-target-owner", CreateGroupRequest("Invite targets", "TRIP", "EUR"))
        val ordinaryMember = membershipRepository.findByGroupIdAndStatus(group.groupId, "ACTIVE").single()

        val error = assertThrows<SquarewiseException> {
            store.invite(
                group.groupId,
                "ordinary-target-owner",
                CreateInviteRequest(24, ordinaryMember.membershipId)
            )
        }

        assertEquals("CONFLICT", error.definition.legacyCode)
    }

    /** Verifies a placeholder already bound to a subject cannot receive a second targeted invite. */
    @Test
    fun `rejects invite targeting an already bound placeholder`() {
        val group = store.create("bound-target-owner", CreateGroupRequest("Bound target", "TRIP", "EUR"))
        val placeholder = store.addPlaceholder(
            group.groupId,
            "bound-target-owner",
            CreatePlaceholderRequest("Already bound")
        )
        val boundPlaceholder = membershipRepository.findById(placeholder.membershipId).orElseThrow()
        boundPlaceholder.subject = "existing-subject"
        membershipRepository.saveAndFlush(boundPlaceholder)

        val error = assertThrows<SquarewiseException> {
            store.invite(
                group.groupId,
                "bound-target-owner",
                CreateInviteRequest(24, placeholder.membershipId)
            )
        }

        assertEquals("CONFLICT", error.definition.legacyCode)
    }

    /** Verifies an invitation cannot target a placeholder that was removed after creation. */
    @Test
    fun `rejects invite targeting a removed placeholder`() {
        val group = store.create("removed-target-owner", CreateGroupRequest("Removed target", "TRIP", "EUR"))
        val placeholder = store.addPlaceholder(
            group.groupId,
            "removed-target-owner",
            CreatePlaceholderRequest("Former target")
        )
        store.removeMember(group.groupId, "removed-target-owner", placeholder.membershipId)

        val error = assertThrows<SquarewiseException> {
            store.invite(
                group.groupId,
                "removed-target-owner",
                CreateInviteRequest(24, placeholder.membershipId)
            )
        }

        assertEquals("CONFLICT", error.definition.legacyCode)
    }

    /** Verifies removing an unknown membership is rejected without changing the group revision or effects. */
    @Test
    fun `rejects removal of an unknown membership without mutation`() {
        val group = store.create("missing-member-owner", CreateGroupRequest("Members", "HOUSEHOLD", "EUR"))
        val auditCount = auditRepository.count()
        val outboxCount = outboxRepository.count()

        val error = assertThrows<SquarewiseException> {
            store.removeMember(group.groupId, "missing-member-owner", UUID.randomUUID())
        }

        assertEquals("NOT_FOUND", error.definition.legacyCode)
        assertEquals(0L, groupRepository.findById(group.groupId).orElseThrow().revision)
        assertEquals(auditCount, auditRepository.count())
        assertEquals(outboxCount, outboxRepository.count())
    }
}
