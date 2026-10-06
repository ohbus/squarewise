# CORE-19: Complete group lifecycle and membership administration

Implement the missing MVP group archive, named placeholders, member removal,
and invitation revocation behavior with durable authorization and history
preservation. Depends on CORE-09 and CORE-17. Owns the Expense Core group module,
its migrations/tests, and the related REST contract. Validate focused tests,
PostgreSQL integration, contracts, and public-interface authorization scenarios.

## Implementation details

- **Flyway Database Migration (`V9__add_group_archive_placeholders_revocation.sql`)**:
  - Added `status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'` with constraint `status IN ('ACTIVE', 'ARCHIVED')` to `expense_groups`.
  - Added `display_name VARCHAR(120)`, `is_placeholder BOOLEAN NOT NULL DEFAULT FALSE`, and `status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'` with constraint `status IN ('ACTIVE', 'REMOVED')` to `group_memberships`.
  - Made `group_memberships.subject` nullable for unclaimed named placeholders.
  - Added `revoked_at TIMESTAMP WITH TIME ZONE` and `placeholder_id UUID REFERENCES group_memberships` to `group_invitations`.

- **Domain Models & Repositories (`GroupEntity`, `GroupMembershipEntity`, `GroupInvitationEntity`, `GroupRepository`, `GroupMembershipRepository`, `GroupInvitationRepository`)**:
  - Updated entities to reflect new schema fields (`status`, `displayName`, `isPlaceholder`, `revokedAt`, `placeholderId`).
  - Added status-aware query methods: `existsByGroupIdAndSubjectAndStatus`, `findAllBySubjectAndStatusOrderByMembershipId`, `findByGroupIdAndStatus`, `findByMembershipIdAndGroupId`.
  - Added atomic `revokeIfAvailable` and updated `claimIfAvailable` queries on `GroupInvitationRepository`.

- **Persistence Adapters (`JpaGroupStore`, `InMemoryGroupStore`, `JpaExpenseStore`, `JpaSettlementStore`)**:
  - `archive`: Transitions active group to `ARCHIVED` status, increments group revision, and emits audit (`group.archived`), sync journal, and outbox event (`group.archived.v1`). Rejects subsequent write mutations on archived groups with 409 Conflict.
  - `addPlaceholder`: Creates a named placeholder member (`isPlaceholder = true`, `displayName = ...`, `subject = null`) in an active group, increments revision, and emits audit (`member.placeholder_added`), sync journal, and outbox event.
  - `removeMember`: Soft-removes a member (`status = "REMOVED"`) while preserving `membershipId` and historical ledger postings/financial records. Excludes removed members from active member queries and revokes API access.
  - `invite`: Generates invitation tokens, optionally targeting a specific `placeholderId`.
  - `revokeInvite`: Atomically revokes pending invitations and emits audit (`invitation.revoked`) and outbox event (`invitation.revoked.v1`).
  - `claim`: Atomically claims token and, if a `placeholderId` is present on the invite, binds the claimant's `subject` directly to the placeholder's existing `membershipId`, preserving historical participant allocations and balance ledger postings.

- **REST Endpoints & OpenAPI Contract (`GroupController`, `contracts/rest/expense-core.openapi.json`)**:
  - Added `POST /groups/{groupId}/archive` (`archiveGroup`).
  - Added `POST /groups/{groupId}/placeholders` (`createPlaceholder`).
  - Added `DELETE /groups/{groupId}/members/{membershipId}` (`removeGroupMember`).
  - Added `POST /groups/{groupId}/invites/{token}/revoke` (`revokeInvite`).
  - Updated `CreateInvite`, `Invite`, `Group`, and `GroupMember` schemas.

## Verification results

- `make contracts`: Verified all OpenAPI 3.1, GraphQL, and event schemas.
- `make test-unit`: All unit tests passed cleanly across all applications and shared libraries.
- `make check`: Full CI suite passed cleanly (contracts, Spotless formatting, unit tests, JaCoCo test coverage verification, and artifacts build).
