# CORE-24: Cover group-rename request and authorization edge cases

## Objective

Cover boundary, malformed, authorization, and no-op group rename requests through the REST and store boundaries.
Verify stable HTTP status codes, ProblemDetail error codes, that accepted renames trim whitespace and support Unicode, and that rejected requests do not change group revisions or emit side effects (audit records, outbox messages).

## Acceptance Criteria

- Blank and whitespace-only names: Rejected through REST boundary with HTTP 400 Bad Request and GlobalErrorHandler ProblemDetail code `VALIDATION_FAILED`. Revision unchanged.
- Boundary lengths: Name with length 120 succeeds and updates group name and revision. Name with length 121 is rejected with 400 Bad Request (`VALIDATION_FAILED`), leaving the group unchanged.
- Whitespace trimming: Leading and trailing whitespace is trimmed upon rename via PATCH and store update.
- Unicode names: Renaming with multi-byte/Unicode characters succeeds and preserves characters.
- Repeated identical renames: Submitting identical rename requests succeeds and increments the revision on each change.
- Missing group ID: Returns HTTP 404 Not Found with ProblemDetail code `NOT_FOUND`.
- Non-member actor: Returns HTTP 404 Not Found with ProblemDetail code `NOT_FOUND` (preventing group enumeration), leaving group unchanged.
- Malformed JSON payload: Returns HTTP 400 Bad Request with ProblemDetail code `VALIDATION_FAILED`, leaving revision unchanged.
- Side effects check: Verified that rejected rename requests (non-member actor, non-existent group) do not increment revision, do not write audit records (`GroupAuditRepository.count()`), and do not append outbox messages (`OutboxRepository.count()`).
- Structured KDoc comments (`/** ... */`) added to test classes and test methods detailing intent, invariants, and edge cases.

## Validation Commands

- `./gradlew :app:expense-core:test --rerun-tasks --no-daemon`
- `git diff --check`

## Evidence

- Tests added to `GroupControllerTest.kt` and `JpaGroupStoreTest.kt`.
- `./gradlew :app:expense-core:test --rerun-tasks --no-daemon` passed with 100% tests passing on 2026-09-18.
- `git diff --check` passed cleanly.
