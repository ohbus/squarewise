# SEC-01B: Make durable identity independent of email

## Status

Complete. Implemented and verified under SEC-01B.

## Objective

Resolve finding SEC-002 by separating identity authentication keys from user contact
data. In the target model:
- Durable identity is represented as `(issuer, subject)`.
- Email is verified contact data, not an authorization key or a substitute subject.
- Accounts owns the identity mapping table (`account_identities`) mapping
  `(issuer, subject)` -> `account_id` with state and lifecycle timestamps.
- Passwordless verification resolves or links an identity rather than synthesizing
  `internal:$email` as the durable subject.
- `/me` queries resolve only already-linked accounts; implicit profile creation on
  lookup is eliminated.

## Owned Paths

- `app/accounts/src/main/kotlin/com/squarewise/accounts/profile/`
- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/identity/`
- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/login/LoginVerificationService.kt`
- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/session/TokenSessionService.kt`
- `app/accounts/src/main/resources/db/migration/`
- `docs/tasks/details/SEC-01B.md`

## Acceptance Criteria

1. Dedicated `account_identities` table created via Flyway migration with unique
   constraint on `(issuer, provider_subject)`.
2. Existing accounts migrated deterministically with forward-only backfill.
3. Changing email does not change the durable account subject or unlink existing
   sessions.
4. Lookup on `/me` for an unknown or unmapped subject returns 401 Unauthorized / 404
   Not Found rather than implicitly provisioning a new profile.
5. Passwordless login creates or resolves identity records via explicit enrollment,
   issuing tokens with the stable account identifier.
6. Comprehensive unit and persistence integration tests verify mapping, uniqueness,
   and lifecycle transitions.

## Verification Commands
 
- `./gradlew :app:accounts:test --rerun-tasks --no-daemon`
- `uv run python tools/contracts/validate.py`
- `uv run python tools/ops/check_security_hygiene.py`
- `uv run python tools/ops/check_architecture.py`
- `make python-typecheck`

## Implementation Notes

- Created `account_identities` table (`V10__create_account_identities.sql`) with unique constraint on `(issuer, provider_subject)` and indexed `account_id` and `email`.
- Included forward-only portable backfill from `account_profiles` populating initial identity records.
- Implemented `AccountIdentityEntity`, `AccountIdentityRepository`, and `JpaAccountIdentityStore` adhering to strict SOLID file separation.
- Decoupled `ProfileStore`: refactored `ProfileQueryStore.get(subject): ProfileResponse?` into a pure query and `ProfileCommandStore.create(...)` into an explicit command, eliminating implicit profile provisioning on `/me` lookups (satisfying CQS).
- Updated `LoginVerificationService` to explicitly enroll identities via `accountIdentityStore.enrollIdentity(...)` with durable `sqw:$accountId` subjects, decoupled from email.
- Verified identity lookup, email updates without altering subject, Flyway V10 execution, and profile failure behaviors in `JpaAccountIdentityStoreTest` and `ProfileControllerTest`.
