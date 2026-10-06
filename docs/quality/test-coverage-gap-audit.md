# Repository-wide test coverage gap audit

**Task:** QA-10  
**Status:** In progress  
**Last audited:** 2026-10-02
**Scope:** `app/`, `libs/`, `tests/`, `tools/bruno/`, contracts, and the
test/quality documentation.

For a concise evidence-state overview, see the [QA-10 test-gap summary](qa10-gap-summary.md).

## Current baseline

As of 2026-10-05, the freshly regenerated JaCoCo XML baseline records **35
production methods with missed branches** containing **61 missed branches**,
**54 contract operations** (45 REST and 9 GraphQL), and **one concrete
execution-gap record**. Operation source discovery
finds 0 operations without a literal E2E reference and 36 without a literal
Bruno reference. These numbers are backlog signals, not passing-test claims;
the hard branch gate remains red until the production reports are regenerated
against the current source and every record is covered or explicitly classified.
The current environment has Java 25 and the Gradle wrapper successfully ran
the repository-wide test and JaCoCo tasks. This baseline is local evidence;
hosted CI, deployed E2E, and environment-owned release gates remain separate
acceptance requirements.

On 2026-10-02, an isolated `qa10` Docker/OIDC stack supplied fresh local
runtime evidence: the acceptance runner passed, signed Bruno passed 71/71
requests and 78/78 assertions, and the product lifecycle journey passed. The
normalized Bruno artifact credits 18 uniquely attributable contract operations;
36 remain source-only because ambiguous/shared fixtures are deliberately not
credited. This closes neither hosted-CI evidence nor the remaining per-operation
acceptance rows.

The current residual review split is **4 reviewed structural-invariant mappings**
and **31 behavior-covered boundary/instrumentation mappings**. No
missed method is unaccounted for; behavior-covered mappings are explicitly
linked to tests, while structural records remain review work.
The companion [`QA-10 branch-line ledger`](qa10-branch-line-gap-ledger.md)
records the 48 exact JaCoCo source lines that account for those 61 branches;
it is regenerated from the same reports and is not a substitute for behavior
acceptance.
The companion [`QA-10 concrete execution-gap ledger`](qa10-execution-gap-ledger.md)
also records 1 production method with zero covered instructions after
excluding compiler-generated methods, accessors, application entry points, and
interface declarations (`DbTelemetry.measureQuery`). This record catches method-level execution gaps that
branch-only discovery cannot represent. Framework/bootstrap entries require
configuration or integration evidence; domain and transport entries require
direct behavior tests. This ledger is a backlog and review aid, never a reason
to delete implementation or weaken a public contract.

The Accounts RSA provider boundary is covered by generation, complete and
partial PEM loading, blank PEM fallback, malformed PEM rejection, rotation,
and `kid` validation tests. It no longer appears in the current missed-branch
inventory after the full source-revision report was regenerated.

## Purpose and completion rule

This document is the implementation-backed backlog for missing tests. It is
more demanding than a line-coverage report and more specific than the public
operation matrix. Every production branch must have a named assertion at the
lowest useful layer, and every externally observable behavior must also have
the strongest applicable transport or deployed test.

QA-10 is complete only when every row below has either:

1. a passing test linked to the exact production class/method and invariant;
2. a documented, reviewed reason that the branch is generated/structural and
   does not require behavior coverage; or
3. an environment-owned test result with its environment, threshold, artifact,
   and reviewer recorded.

Green compilation, a passing application-context test, a Bruno request, or a
JaCoCo percentage alone never closes a row. Mocked gateway/controller tests
prove mapping only; they do not prove deployed wiring, persistence, broker
delivery, identity-provider behavior, or cross-service side effects.

## Audit evidence and limits

The current source inventory contains 400 Kotlin production files and 199
Kotlin test files under `app/` and `libs/`. The generated local JaCoCo
reports currently report these line-coverage signals:

The exact method-level branch records are maintained in the generated
[`QA-10 current branch-gap ledger`](qa10-current-branch-gap-ledger.md). It is
regenerated from the same reports as this audit and includes the source line,
missed/covered counts, provisional QA row, row-level acceptance criterion,
explicit closure status, and next action for every current record.

| Module | Missed lines | Covered lines | Signal |
| --- | ---: | ---: | --- |
| `app/accounts` | 71 | 1,317 | 94.9% |
| `app/bff` | 78 | 721 | 90.2% |
| `app/expense-core` | 92 | 2,147 | 95.9% |
| `app/notifications` | 30 | 566 | 95.0% |
| `libs/db` | 9 | 253 | 96.6% |
| `libs/errors` | 12 | 174 | 93.5% |
| `libs/ids` | 20 | 1 | 4.8% |
| `libs/observability` | 5 | 68 | 93.2% |
| `libs/security` | 36 | 116 | 76.3% |

These reports are discovery evidence and must be regenerated with the source
revision under review before closure. Kotlin compiler-generated accessors,
main functions, DTO constructors, and constants may explain some missed lines;
they still require an explicit classification rather than silent exclusion.

The operation matrix currently inventories 45 REST operations and 9 GraphQL
root operations. The generated [`QA-10 operation acceptance ledger`](qa10-operation-acceptance-ledger.md)
now records every operation's required acceptance dimensions. That proves
inventory and criteria completeness, not complete validation,
authorization, failure, replay, concurrency, or side-effect coverage.

For repeatable operation-level discovery, run
`uv run --frozen --no-build python tools/coverage/report_operation_test_gaps.py --format markdown`.
The tool compares contract operation IDs and GraphQL root fields with literal
operation-name references in `tests/e2e/` and `tools/bruno/`. The current
inventory contains 54 operations; 0 have no E2E source signal and 36 have no
Bruno source signal. These are discovery signals only: a missing string can be
a naming mismatch, while a present string does not prove authorization,
negative behavior, persistence, messaging, replay, or deployed side effects.
The AST callable scan finds a named Python callable reference for all 54
operations. `CALLABLE-SOURCE-REFERENCE-ONLY` means only that a callable's source
contains the identifier; it does not prove test discovery, invocation, or
assertion execution. `FILE-SOURCE-REFERENCE-ONLY` remains the weaker status for
file-level-only matches.
The request-shaped scan is surface-aware: REST records use normalized contract
paths and GraphQL records use field selections, so duplicate operation IDs across
surfaces are not credited from one another. `REQUEST-SOURCE-REFERENCE-ONLY` is
still static source evidence, not a runner result.
The generated operation ledger now also emits `BRUNO-SOURCE-REFERENCE-ONLY` or
`NO-BRUNO-SOURCE-REFERENCE`, plus `NO-EXECUTION-ARTIFACT-INGESTED` for every
record. The latter is deliberate: this source inventory does not ingest a
runner result, so no operation is represented as executed acceptance evidence.
When a deployed runner supplies a normalized `qa10-operation-execution-v1`
artifact (schema: `contracts/qa10/operation-execution.schema.json`),
`report_operation_test_gaps.py --execution-artifact` validates its
source revision, environment, operation identity, retained artifact path, and
non-empty assertion list before emitting `EXECUTION-ARTIFACT-PASSED`,
`EXECUTION-ARTIFACT-FAILED`, or `EXECUTION-ARTIFACT-BLOCKED`. Invalid, duplicate,
or unknown records are rejected; source references remain non-execution evidence.
References are matched as standalone identifiers rather than arbitrary
substrings, so `group` cannot be falsely credited by an unrelated `groups` or
`groupId` occurrence. The matcher intentionally favors a reviewable false
negative over a false positive that could hide a missing E2E journey.
Each signal must therefore be reconciled against the operation matrix and the
QA10-E2E acceptance rows before closure. Every record is assigned to
`QA10-E2E01`, whose acceptance criterion is the complete signed-persona
per-operation authorization and side-effect matrix.

For repeatable concrete execution-gap discovery, run
`uv run --frozen --no-build python tools/coverage/report_execution_gaps.py --format markdown`.
The current report contains 3 zero-instruction concrete methods across
Accounts and shared libraries. It excludes
interfaces and compiler-generated accessors/scaffolding but deliberately keeps
real domain, transport, security, messaging, and configuration methods visible
until a focused test or reviewed framework-wiring rationale exists.
One record, `DbTelemetry.measureQuery`, is marked `INLINE-EXPANDED`: its
behavior is exercised through inline call-site tests, while JaCoCo cannot mark
the inline declaration method node executed. This is a reviewed compiler
classification, not a reason to add reflection-only coverage or alter the
implementation contract.
`DbTelemetry.acquisition` and `queryDuration` now achieve 100% branch
coverage in `DbTelemetryTest` by asserting metric publication when
`MeterRegistry.timer` returns null as well as valid `Timer` instances.
`DbTelemetry` now has 0 missed branches and 100% branch coverage.

### Current operations without a literal E2E source reference

The current scan identifies no operations without a literal E2E source or
reference. The complete signed-persona operation inventory still requires
acceptance-dimension evidence below; source presence is not closure.
The current scan previously identified the following operations for explicit E2E
implementation or source-reference reconciliation. “No literal reference” is
not proof that an operation is never exercised; it is a reproducible discovery
signal that must be resolved with an operation-specific test name, or with a
reviewed mapping when a shared journey intentionally covers it. Every listed
operation remains open under `QA10-E2E01` until the signed-persona acceptance
matrix records success, authorization denial, validation/failure behavior, and
the required durable or asynchronous side effect.

| Service | Missing E2E operation IDs |
| --- | --- |
| Accounts API | *(none; `startLogin`, `verifyLogin`, and `logout` are now represented by the deployed auth-email journey)* |
| Expense Core API | *(none; recurrence source references exist, but worker and failure acceptance remains open below)* |
| Notifications API | *(none; source references exist, but full acceptance remains open below)* |

### Latest reported live-run failures

The operation inventory is a discovery control, not an execution result. The
latest reported `make acceptance-live` run on 2026-10-01 completed 71 requests
with 65 passing and six failing: Accounts `logout` returned `400` instead of
the asserted `204`/`401`; Expense Core `createExpense` returned `404`;
`updateExpense` and `deleteExpense` returned `500`; `recordSettlement`
returned `400`; and `reverseSettlement` returned `401`. The subsequent
`make e2e-live` run stopped when the signed owner received `403` from
`getProfilesBatch` because the endpoint requires internal workload authority.
Commit `c6e7c59` corrected that journey fixture: owner-only IDs use the signed
user token, while mixed/unknown IDs send the explicit internal workload-role
header. The historical failure is superseded by the fixture correction, but a
fresh live rerun is still required for deployed evidence.

These failures remain open under `QA10-E2E01` and the affected QA10-A06,
QA10-C01, and QA10-C05 rows. The required acceptance evidence is:

| Observed failure | Required replacement evidence before closure |
| --- | --- |
| `logout` returned `400` | Use a valid signed refresh-family fixture and assert successful `204` revocation; separately exercise malformed/unknown credentials and assert the documented `401`/no-op behavior, with the family state and redacted audit evidence captured. |
| `createExpense` returned `404` | Prove the signed subject owns or belongs to the referenced group in the same isolated stack, then assert `201`, postings, revision, idempotency, sync, outbox, and zero-sum state; a missing/foreign group must assert the structured not-found response and no mutation. |
| `updateExpense`/`deleteExpense` returned `500` | Reproduce with persisted expense and matching subject/version fixtures; assert the documented success and durable mutation, plus structured stale/missing/unauthorized failures with unchanged expense, revision, postings, and idempotency state. An unexpected `500` is not a passing negative case. |
| `recordSettlement` returned `400` / `reverseSettlement` returned `401` | Establish valid currency, participants, amount, idempotency identity, authenticated subject, and settlement ownership before asserting `201`/successful reversal, posting reconciliation, balance changes, replay behavior, and exact rejection/no-posting cases. |
| Owner `getProfilesBatch` returned `403` | Fixture correction is committed in `c6e7c59`: owner-only duplicate IDs use the signed user token, and mixed/unknown IDs use the explicit internal workload-role header. A fresh live run must still prove batch isolation, duplicate/empty/unknown/over-limit behavior, and no cross-subject leakage; this historical failure is not closure evidence. |

Until these cases pass with captured durable evidence, the presence of an
operation name in an E2E source file must not be reported as E2E coverage.

### Operation-specific E2E acceptance matrix

The following matrix expands the generic dimensions into the side effects and
authorization invariants required for each missing-operation family. Every
operation ID in the preceding list belongs to exactly one row; the test name,
signed personas, and captured artifacts must be recorded against the individual
operation IDs, not only against the family.

| Operation family and operation IDs | Required E2E acceptance |
| --- | --- |
| Accounts profile: `getMe`, `getProfileById`, `getProfilesBatch`, `updateMe` | A signed subject reads/updates only its permitted profile; owner, non-owner, removed-member, missing-profile, duplicate-batch, empty-batch, malformed-ID, and over-limit cases return the contract error without cross-subject queries or mutations. Batch output is deduplicated and omits unknown IDs exactly as specified. `getMe`, owner/foreign-subject `getProfileById`, owner/foreign-subject `getProfilesBatch`, and a persisted `updateMe` timezone change are exercised in the signed-persona product journey; the remaining family members remain open. |
| Accounts requests: `listExportRequests`, `requestDeletion`, `requestExport` | A signed subject creates and lists only its own durable request rows; repeated requests follow the documented idempotency/state transition, authorization failures create no row or outbox event, and export/deletion state is visible with the correct redaction and audit evidence. `requestExport`, `listExportRequests`, and final-lifecycle `requestDeletion` are exercised in the signed-persona product journey; repeated/isolation transitions and durable deletion-state assertions remain open. |
| Passwordless authentication: `startLogin`, `verifyLogin` | LINK and CODE journeys use a test mailbox/Mailpit artifact, issuance and delivery failures remain generic, rate limits return bounded `Retry-After`, credentials are single-use/expiry-bound, replay and wrong-subject redemption are indistinguishable, and successful verification creates exactly one stable identity/session. `tests/e2e/test_auth_email_delivery.py` now exercises real CODE delivery, one-time verification, and replay rejection; LINK, rate-limit, wrong-subject, expiry, and redaction dimensions remain open. |
| Session ownership: `logout` | The signed subject can revoke only its own refresh-token family; blank, unknown, expired, mismatched, replayed, and deleted-account cases produce the documented no-op or unauthorized result, mutate no unrelated family, and leave an auditable redacted revocation event. `tests/e2e/test_auth_email_delivery.py` exercises authenticated logout, refresh-family rejection, and idempotent logout replay; cross-family, mismatch, expiry, deletion, and audit-redaction dimensions remain open. |
| Expense groups/membership: `archiveGroup`, `claimInvite`, `createInvite`, `createPlaceholder`, `getGroup`, `listGroupMembers`, `listGroups`, `removeGroupMember`, `revokeInvite` | Owner/member/non-member/removed-member personas exercise lifecycle and object hiding; invite expiry, revocation, duplicate/concurrent claim, placeholder binding, archive restrictions, membership revision/audit, sync change, and notification/outbox side effects are asserted transactionally. The signed-persona journey covers create/claim invite, successful placeholder creation and soft removal, member listing, owner-visible group listing/detail; the REST edge suite covers authorized `archiveGroup` and `revokeInvite` followed by claim rejection. Placeholder binding, removal authorization/replay, and broader lifecycle edges remain open. |
| Expense financial/search: `deleteExpense`, `exportExpenses`, `getBalances`, `getSettlementSuggestions`, `listExpenses`, `previewAllocation`, `recordSettlement`, `reverseSettlement`, `searchExpenses`, `updateExpense` | Valid and rejected writes prove authorization, validation, stale-version/idempotency, zero-sum ledger/postings, balance and suggestion consistency, search cursor/limit/filter behavior, CSV formula safety, audit/sync/outbox effects, rollback, and no cross-group visibility. The REST-edge suite compares the member expense collection before and after rejected non-member/unauthenticated creates and update/delete attempts; the signed-persona journey covers `getBalances`, REST `getSettlementSuggestions`, `listExpenses`, description-based `searchExpenses`, successful filtered `exportExpenses`, a valid equal-split `previewAllocation`, durable REST settlement/reversal, and optimistic-version update/delete lifecycle alongside GraphQL repayment/suggestion checks; replay/conflict and rollback edges remain open. |
| Recurrence: `createRecurringSchedule`, `getRecurringSchedule`, `listRecurringSchedules`, `pauseRecurringSchedule`, `resumeRecurringSchedule`, `updateRecurringSchedule` | Schedule ownership, date/time-zone/month-end policy, missing/archived group, pause/resume/update idempotency, concurrent worker claim, bounded catch-up, deterministic occurrence identity, duplicate prevention, failed occurrence rollback, and notification/outbox/sync effects are captured. The signed-persona journey now covers successful create/list/get/pause/resume/update using a future-dated schedule; worker execution, catch-up, duplicate prevention, failure rollback, and asynchronous effects remain open. |
| Synchronization: `getChanges`, `getSnapshot` | Signed group members receive ordered revisions/tombstones and opaque cursors; empty/first/last/expired/malformed/decreasing/cross-group cursors, membership loss, and rejected-mutation revision behavior are asserted with no stale strong read. The signed-persona journey now covers `getSnapshot` followed by `getChanges` with its opaque continuation cursor; ownership, tombstone, expiry, malformed/decreasing, and rejected-mutation acceptance remain open. |
| Notifications: `getPreferences`, `listInbox`, `markAsRead`, `updatePreferences` | Preferences and inbox rows are isolated by subject, defaults/version conflicts are enforced, invalid page/cursor and duplicate mark-read behavior is stable, broker delivery/deduplication/retry reaches the durable inbox, and unauthorized requests create no mutation or notification side effect. |

The following operations already have a literal E2E source signal, but are
listed explicitly so source presence cannot be mistaken for complete
acceptance evidence:

| Operation | Required E2E acceptance |
| --- | --- |
| REST `refreshToken` | A signed refresh request rotates only the caller's valid session family; expired, revoked, replayed, mismatched, and deleted-account tokens are rejected or no-op exactly as contracted, with one durable family transition and no token leakage. |
| REST `createExpense` | Owner/member personas prove validation, idempotency, posting/revision/audit/outbox effects, rollback, zero-sum ledger state, and no cross-group mutation for rejected or replayed requests. |
| REST `createGroup` | The authenticated subject creates exactly one durable group with the documented owner membership, revision, audit, and event effects; duplicate, malformed, and unauthorized requests create no partial state. |
| REST `updateGroup` | Owner/member authorization, optimistic version behavior, archived-group policy, audit/revision/sync effects, and rejected-request immutability are asserted with signed personas. |
| REST `getPreferences` | A signed subject receives default preferences, reads its own persisted update, and cannot observe another subject's settings; unauthenticated access remains rejected. |
| REST `updatePreferences` | A signed subject updates only its own preference row and receives the documented no-content response; unauthenticated updates create no persistence mutation. |
| REST `listInbox` | A signed subject receives only its own delivered notifications with stable cursor pagination; invalid bounds/cursors and unauthenticated access are rejected without leakage. |
| REST `markAsRead` | A signed subject marks its own notification once, receives 204, observes the durable read flag, and cannot mark another subject's notification. |
| GraphQL `createExpense` | The GraphQL mutation preserves REST financial invariants, maps catalog errors and request IDs, propagates authentication and causal context, and exposes no forbidden group or ledger data. |
| GraphQL `createGroup` | The mutation enforces signed-subject authorization, creates the documented durable owner state once, maps failures consistently, and emits the expected asynchronous event without duplication. |
| GraphQL `recordRepayment` | Valid, replayed, conflicting, unauthorized, and invalid repayment mutations assert exact GraphQL errors, posting/balance/revision effects, idempotency, and zero-sum preservation. |
| GraphQL `updateGroup` | Owner/member, removed-member, archived, stale-version, and malformed-input outcomes preserve the same authorization, mutation, audit, and error contracts as the REST path. |
| GraphQL query `group` | Signed owner/member/removed-member/non-member personas prove object hiding, field authorization, causal consistency, and no cross-group data exposure. |
| GraphQL query `groups` | Results are subject-scoped and stable under pagination, empty/invalid cursors, membership removal, and concurrent changes; hidden groups and sensitive fields never appear. |
| GraphQL query `me` | The token subject resolves only its own profile/session view; absent, invalid, revoked, and deleted identities return the documented redacted result without mutation. |
| GraphQL query `settlementSuggestions` | Suggestions respect group membership, ledger state, currency and pagination boundaries, expose no private data, and remain consistent after settlement/reversal. |
| GraphQL subscription `groupChanged` | A signed subscriber receives only permitted group changes, reconnects from an explicit cursor without duplicates or loss, and is terminated after membership revocation or invalid protocol state. |

The GraphQL roots currently have literal E2E references, but those references
still require the dimension checks above and in the operation matrix; source
presence is not acceptance evidence by itself. Regenerate this list with
`uv run --frozen --no-build python tools/coverage/report_operation_test_gaps.py --format markdown`.

For each operation, the E2E ledger must carry these dimensions separately:

| Dimension | Required evidence |
| --- | --- |
| Authentication | Valid signed persona reaches the intended boundary; missing, expired, wrong-issuer, and invalid-signature tokens are rejected without a side effect. |
| Authorization | Owner/member, removed member, non-member, and workload persona outcomes are recorded where the contract permits them; object hiding and exact error code are asserted. |
| Input and failure behavior | Required, blank, malformed, boundary, oversized, duplicate, stale-version, and unsupported values assert exact status/schema/problem code. |
| Durable state | PostgreSQL rows, revisions, audit records, idempotency state, and ledger invariants are checked after success and after rejection/rollback. |
| Asynchronous state | Outbox, broker acknowledgement/requeue/DLQ, inbox deduplication, Mailpit delivery, and eventual state are checked where applicable. |
| Replay and concurrency | Repeated and concurrent requests prove one durable outcome, no duplicate side effect, and the documented conflict/rate-limit behavior. |
| Isolation and redaction | Other subjects/groups cannot observe or mutate the result; credentials, tokens, and sensitive upstream details are absent from responses and logs. |

An operation with a source-reference signal is not closed until these dimensions
are evidenced. The operation inventory has no missing literal E2E references,
implementation/reconciliation work, while the remaining operations still need
the same dimension review rather than being inferred closed from a string match.

### Exhaustive current branch inventory

The regenerated JaCoCo XML contains **39 methods with at least one missed
branch**. This is the exhaustive discovery set for this revision; the summary
below prevents a high-level module percentage from hiding a small but important
method. Every method in this set must be assigned to a backlog row, tested, or
classified as generated/structural with reviewer approval.

| Module | Classes with missed lines | Classes with missed branches | Methods with missed branches |
| --- | ---: | ---: | ---: |
| `app/accounts` | 25 | 6 | 6 |
| `app/bff` | 16 | 3 | 5 |
| `app/expense-core` | 36 | 8 | 22 |
| `app/notifications` | 7 | 1 | 1 |
| `libs/db` | 7 | 2 | 2 |
| `libs/errors` | 1 | 1 | 1 |
| `libs/ids` | 3 | 0 | 0 |
| `libs/observability` | 2 | 1 | 2 |
| `libs/security` | 2 | 0 | 0 |
| **Total** | **99** | **22** | **39** |

The exact class, source file, method, source line, missed-branch count, and
covered-branch count are in the current files
`app/*/build/reports/jacoco/test/jacocoTestReport.xml` and
`libs/*/build/reports/jacoco/test/jacocoTestReport.xml`. Regenerate them with
`./gradlew.bat test jacocoTestReport --rerun-tasks --no-daemon`, then inspect
every `<class>/<method>/<counter type="BRANCH">` where `missed > 0`.
This is intentionally a fail-open discovery report: a missed branch is not
automatically a defect, but it is never silently treated as covered.

For a stable per-method inventory, run:

```text
uv run --frozen --no-build python tools/coverage/report_branch_gaps.py --format markdown
uv run --frozen --no-build python tools/coverage/report_branch_gaps.py --format json
# Closure gate: this must exit 0 only after every gap is closed or removed
# through an explicitly reviewed structural classification.
uv run --frozen --no-build python tools/coverage/report_branch_gaps.py --format json --fail-on-gaps
```

The JSON array is the machine-readable assignment set. Its record count must
equal the `Methods with missed branches` total above (**39**), and the sum of
its `missed_branches` fields must equal the current missed-branch total
(**78**). Each object carries the module, production class, source file,
method, source line, missed/covered branch counts, originating JaCoCo report,
provisional QA-10 row, assignment basis, and the row's machine-readable
`acceptance_criteria`. The record count and branch-count
sum are both regression-tested so a changed JaCoCo baseline cannot silently
replace one uncovered branch with another while appearing stable by method
count alone.
The tool emits no synthetic exclusions and returns all methods with `missed > 0`.
The provisional row is path-based accountability, not closure evidence;
reviewers must confirm the classification and then link each object to a
passing test or an explicitly reviewed generated/structural rationale.

### Per-record closure contract

The JSON inventory is the authoritative assignment set, but a QA row is not a
test record. For every inventory object, the closure ledger must record the
production module, class, source file, method, source line, missed and covered
branch counts, test file and test name, evidence layer, and the invariant or
failure outcome asserted. The linked test must exercise the behavior represented
by the missed branch; merely executing the method, increasing line coverage, or
asserting a generic HTTP status is insufficient. Persistence, messaging, and
deployed rows must additionally record durable state, broker acknowledgement or
retry state, token persona, and externally observable response that prove the
side effect.

If a regenerated report still contains a record, its method is not closed even
when a nearby test passes. A record may leave the open inventory only when the
next report shows no missed branches, or when the exact JaCoCo mapping is
reviewed and the ledger records why the remaining instrumentation is generated
or structurally unreachable, which source invariant makes it unreachable, and
the test that proves that invariant. Deleting code, weakening a guard, adding a
JaCoCo exclusion, or changing a contract does not satisfy this rule.

### Current per-record ledger status

The repository currently has the exact 39-record JSON discovery inventory and
row-level acceptance matrix, but it does **not** yet have closure evidence for
all 39 records. The A07 and E02 tables provide supplemental method-level review
detail; the remaining records still require one of the following to be
recorded against the exact class/method/source line: a passing unit test, a
persistence/messaging integration test, a deployed E2E artifact, or a reviewed
structural rationale. This is an intentional open deliverable, not an implied
claim that the aggregate row counts close every branch.

The generated branch and source-line ledgers now include an explicit evidence
target for every record. These targets identify the existing unit, controller,
service, persistence, or integration boundary that must supply the proof; they
are not assertions that the named test currently executes the exact JaCoCo
branch. `RecurrenceSchedule` intentionally targets the recurring-service test
boundary because compiler-generated constructor paths must not be covered by
reflection-only tests. The ProfileController target remains open design because
source search found no production caller for the private helper.

Each ledger row also has record-level acceptance criteria in addition to the
broader QA-row criteria. A row cannot be closed by naming a test file alone:
the named test must prove the listed input boundary, invariant, durable state,
or deployed side effect, or the row must receive an evidence-backed structural
classification.

Current provisional assignment workload (39 records):

| QA row | Branch-gap records | Primary missing evidence |
| --- | ---: | --- |
| QA10-A01 | 0 | Local publisher slice is complete; broker/deployed delivery remains required. |
| QA10-A02 | 0 | Auth-email sender, retry, parking, stale-event, and durable handoff branches are locally covered; real broker/deployed delivery remains required. |
| QA10-A03 | 1 | Shared Redis atomicity, outage, public 429 behavior, and local multi-replica admission now have execution evidence; hosted atomicity/concurrency and production failover remain open, with one structurally unreachable resolver fallback retained. |
| QA10-A04 | 0 | Explicit external identity-provider path; constructor and unsupported-delegation behavior are locally covered, deployed provider exchange remains required. |
| QA10-A05 | 1 | Local and non-local OIDC decoder selection, discovery, and algorithm wiring; the original decoder terminal branch is retained and needs explicit test evidence. |
| QA10-A06 | 1 | Profile controller and JPA persistence authorization boundary; restored private mapper requires classification or direct evidence. |
| QA10-A07 | 2 | Session, credential, replay, and cleanup behavior; database-backed identity projections are now covered by integration and defensive unit tests, while session-policy/database-invariant branches, concurrent database race, and deployed session evidence remain open. |
| QA10-A08 | 1 | Email canonicalization and malformed-input boundaries; restored explicit domain checks require direct boundary evidence. |
| QA10-B01 | 1 | BFF upstream transport and gateway failure behavior; bearer/watermark propagation now includes numeric advancement over a lower existing LSN, while timeout, partial-response, malformed-body, and deployed failure evidence remain open. |
| QA10-B02 | 0 | GraphQL resolver, error, scalar, and limit behavior; resolver classification, DateTime and MoneyMinor literal acceptance/rejection including nullable AST values, blank-bearer context omission, empty mutation invalidation behavior, required messaging startup validation, bounded deduplicator capacity validation, acceptance-fault filter boundary, empty settlement amount fallback, and subscription admission validation are covered. |
| QA10-B03 | 3 | Realtime fanout and broker consumer behavior; fanout configuration, input, queue, delivery, expiry, and nullable broker-channel listener boundaries are now covered, while generated revocation predicates, broker acknowledgement, reconnect/replay, and deployed WebSocket evidence remain open. |
| QA10-B04 | 1 | Browser origin, CSRF, cookie, and session filters; canonical parsing and malformed-origin behavior are covered, while the constructor's residual collection mapping is structurally reviewed. |
| QA10-C01 | 5 | Expense persistence, transaction, ledger, idempotency, and outbox behavior; allocation-preview malformed/negative totals, overlong category validation, multi-group lookup selection, missing/repeated delete boundaries, update lookup/participant replacement, durable duplicate-event append preservation, broker-message value semantics, in-memory/durable outbox retry/state validation, publisher delivery-policy and confirmation-timeout validation, durable claim eligibility, cleanup retention/batch boundaries, blank/unknown-category defaulting, explicit-null category handling, single and simultaneous payer/allocation-count bounds, custom recurring request mapping, missing/foreign schedule lookup boundaries, recurring membership authorization, amount parsing, JPA search filtering for blank, matching, and non-matching queries, persistent adapter limit bounds, filtered CSV export mapping including formula-prefix, comma, quote, newline, and carriage-return escaping, blank cursor and non-positive CSV-bound validation, unknown-event acknowledgement no-op behavior, group-controller rollback/fanout acceptance faults, missing-group creation, duplicate payer/allocation participant rejection, idempotency-key payload conflict, identical duplicate-ID replay, and mixed existing/new participant updates now have persistence or transport assertions. The recurring controller now covers both one-sided request mappings; null-principal forwarding mappings remain invariant-governed because membership validation rejects the request first. |
| QA10-C02 | 0 | Pure calculator/validator slice is branch-complete; fixed-seed generated invariant tests now cover varied totals, participant counts, weights, percentage partitions, conservation, non-negativity, and permutation determinism. |
| QA10-C03 | 6 | Recurring schedules, claims, locking, and occurrence failures; creation now covers explicit IDs and valid day-of-month boundaries, monthly fallback to the source day, both payer/allocation membership rejection directions, service update mapping covers both one-sided custom specifications, occurrence-date deduplication is proven independently of occurrence-ID equality, the worker zero-budget boundary leaves due state untouched, expense-store generation failure now proves schedule pause plus `generation_error` notification, and the optional-outbox failure flow proves safe no-op behavior. One compiler-generated range branch and remaining date/membership/build fallbacks remain retained for review. |
| QA10-C04 | 9 | Group, invite, membership, expiry, and revocation behavior; archived claims, removed/bound/ordinary-member placeholder targets, already-bound placeholder invitation rejection, removed-placeholder invitation rejection, repository-missing claim outcomes, and normal/targeted invitation claim races now have explicit assertions. |
| QA10-C05 | 1 | Settlement, balance, reconciliation, and rollback behavior; duplicate participant/currency balance aggregation, authenticated/blank-subject authorization, the controller's optional suggestion-engine fallback, one-sided debtor/creditor corruption snapshots, the active-group missing-settlement reversal boundary, and the service boundary rejecting an idempotency key without an authenticated actor are covered, while the engine's strictly-positive transfer guard and independent rollback evidence remain open. |
| QA10-C06 | 1 | Sync revisions, cursors, ordering, and membership boundaries; durable empty-snapshot, blank-group, and malformed/blank/numeric/expired cursor validation are covered, while cursor ownership, causal, and deployed evidence remain open. |
| QA10-D01 | 0 | Auth-email broker parsing, retry, deduplication, and delivery; key configuration, envelope framing, numeric metadata validation, explicit-null/empty-body rejection, null parser-result rejection, invalid-Base64 rejection, and channel-safe ack/reject/requeue boundaries are covered locally, while real broker retry/redelivery and deployed Mailpit evidence remain open. |
| QA10-D02 | 0 | Notification event transaction and acknowledgement coupling, envelope conversion, recipient fallback, and channelless delivery rejection are branch-covered locally; broker acknowledgement/retry and transaction-coupling evidence remain environment-owned. |
| QA10-D03 | 1 | SMTP/Mailpit delivery and retry classification; `SimpleMailMessage` value/accessor/rendering behavior is fully unit-covered, while the compiler-generated dispatcher loop-exit branch is retained for structural classification and external Mailpit/SMTP failure evidence remains open. |
| QA10-D04 | 0 | Local inbox/preferences persistence and controller branches are covered, including blank listing subjects and overlength event/message rejection before persistence; deployed subject-isolation and database-failure evidence remain open. |
| QA10-E01 | 1 | Error mapping, framework failures, headers, and correlation cleanup; invalid-status defensive fallback is exercised, while valid catalog status arms remain governed by the enum invariant. |
| QA10-E02 | 2 | Database routing, reader health, fallback, and operational lifecycle. |
| QA10-E03 | 0 | Servlet/reactive OIDC decoder construction and key-validation paths are locally covered; deployed issuer/provider behavior remains environment evidence. |
| QA10-E04 | 0 | IDs/constants have no current missed-branch methods; static contract checks remain required. |
| QA10-E05 | 0 | Bounded observability labels and metric behavior have direct local invocation evidence; DbTelemetry achieves 100% branch coverage with null and registry-backed timers, and AUTH-09 adds local rate-limit dashboard/alert assets. Deployed scrape, routing, and cardinality remain operational evidence. |

This table is regenerated from the JSON assignment output; it is not a
coverage claim. A row closes only when its acceptance criteria and required
evidence layers pass, and the next regenerated inventory removes or classifies
its records. The `--fail-on-gaps` mode is the repository-level no-missed-branch
gate and must be part of the final QA-10 validation package.

### Reviewed structural branch candidates

The following residual JaCoCo branches have been reviewed against the current
source invariants. They remain present in the discovery inventory until a
regenerated report and reviewer sign-off records the classification; this table
does not delete code or create a coverage exclusion.

| Production target | Structural rationale | Required proof before classification |
| --- | --- | --- |
| `app/bff/.../BrowserOriginPolicy.kt:16`, constructor wildcard predicate | `BrowserOriginPolicyTest` exercises valid non-wildcard lists, wildcard-first and wildcard-after-valid configurations, and an empty native-only list. The remaining JaCoCo mapping is the Kotlin collection/lambda iterator short-circuit for `none`, not an untested origin acceptance rule. | Retain the exact-origin, wildcard, malformed-host, and empty-list tests; classify only this generated collection mapping after each full report regeneration. Do not weaken wildcard rejection or allow arbitrary origins to change the metric. |
| `app/bff/.../BffGatewayFilters.kt:26`, gateway context/downstream-watermark conjunction | `BffGatewayFiltersTest` exercises a valid exchange with a valid watermark, a valid exchange with absent or malformed watermarks, and a missing exchange with a valid downstream watermark. The remaining JaCoCo mapping is the short-circuit path where both the exchange and downstream watermark are absent; the second operand is not evaluated when the exchange is null, and it has no distinct externally observable behavior. | Retain the no-exchange, absent-watermark, malformed-watermark, and numeric-ordering tests; classify only this short-circuit mapping after report regeneration. Do not add reflection or synthetic context corruption to alter the metric. |
| `libs/errors/.../GlobalErrorHandler.kt:143`, `applicationException` | Every production `ErrorCode.httpStatus` value resolves through `HttpStatus.resolve`; the fallback `when` arms are defensive against an enum value that cannot exist at runtime. The test suite now also exercises the invalid-status fail-safe `else` path with a mocked catalog value; the valid catalog mapping test remains the source-of-truth invariant. | Reconfirm the enum/status mapping from the current source, retain both the exhaustive catalog-status test and invalid-status fallback test, and do not simplify the defensive guard without a reviewed contract decision. |
| `app/accounts/.../FallbackJwtDecoder.kt:29`, `decode` | The decoder list is required non-empty. Each loop iteration either returns a `Jwt` or catches a `JwtException` and assigns `lastFailure`; after the loop, `lastFailure` is therefore non-null. Existing tests cover first success, later success, and final failure. | Retain the constructor invariant and three outcome tests; do not simplify the terminal guard. |
| `app/accounts/.../ClientAddressResolver.kt:89`, `normalizeToPartition` | `InetAddress.getByName` returns an `Inet4Address` or `Inet6Address` for the supported address families; the final `else` is defensive for a future JDK subtype. Existing tests cover IPv4, IPv6, malformed, and missing addresses. | Reconfirm the JDK address-family invariant and retain the family/malformed boundary tests. |
| `app/accounts/.../SessionPolicy.kt:82`, `isExpired` | `SessionExpiry` enforces `idleExpiresAt <= absoluteExpiresAt`. If the first short-circuit operand (`now + skew >= idle`) is false, the absolute boundary is necessarily later and the second operand is also false; if the absolute boundary is reached, idle expiry has already made the first operand true. Existing tests cover both observable expiry boundaries and skew. | Retain the `SessionExpiry` ordering invariant and the idle, absolute, and skew tests; confirm the JaCoCo residual is the unreachable short-circuit path after regeneration. |
| `app/expense-core/.../JpaGroupStore.kt:84,106,134,167,195,206,226,253,303` | The group lookup fallbacks occur after an active-membership check or from a newly created group. In a valid persisted state, the membership foreign key and the transactional create path make an orphaned membership/group lookup impossible; `addMembership` is called immediately after creating a group and therefore cannot observe an existing membership for that new group. The `displayName.orEmpty()` mapping is fed by the non-null placeholder request name. Existing `JpaGroupStoreTest` cases cover missing/non-member groups, archived operations, unknown memberships, successful creation, and the new already-bound placeholder invite boundary. | Reconfirm the group/membership foreign-key constraints and retain those public lifecycle tests. Do not manufacture orphan rows or delete defensive fallbacks to alter JaCoCo; classify only these exact mappings after reviewer sign-off, while the invitation predicate itself remains behavior-tested. |
| `app/expense-core/.../SettlementSuggestion.kt:53` | The `transferAmount` value is the minimum of a strictly positive debtor debt and strictly positive creditor credit, so its `<= 0` branch cannot occur for valid queue entries. New tests cover one-sided debtor and creditor snapshots at the service boundary, while balanced, zero, duplicate-row, and multi-currency inputs remain covered. | Retain the zero-sum/posting invariant and the one-sided corruption-detection tests; classify only this exact positive-transfer mapping after report regeneration. Do not delete the guard or invent an impossible queue entry solely to change JaCoCo. |
| `app/accounts/.../ProfileController.kt:56,82` | The private `problem()` helper and its `mapErrorCode` mapper have no callers in the current source; public controller failures throw `ApplicationException` and are rendered by the shared `GlobalErrorHandler`. A reflection-only test would exercise private implementation structure rather than a public contract. | Before closure, either route a real controller failure through this helper and assert the public problem envelope for `ERR_02`, `ERR_03`, `ERR_05`, and the default mapping, or remove the dead helper in a separately justified cleanup change with a behavior review. Do not delete it or add reflection-only coverage solely to change JaCoCo. |
| `app/accounts/.../LoginVerificationService.kt:60`, `verify` | `EmailAddress.parse` requires a non-empty local part, so `redeemed.canonicalEmail.substringBefore("@").ifBlank { "User" }` cannot select its fallback for a valid `LoginCredentialService` redemption. Existing tests cover invalid/replayed credentials, new enrollment, and existing-identity reuse. | Retain the email value-object invariant and the redemption/enrollment/reuse tests; classify only the generated `ifBlank` branch after full JaCoCo regeneration. |
| `app/notifications/.../EmailDispatcher.kt:46`, `dispatch` | `maxAttempts` is normalized with `coerceAtLeast(1)`, and every reachable loop body path either returns a delivery outcome or continues to another attempt. The false `hasNext` branch and post-loop fallback are therefore defensive compiler mapping for an unreachable state; existing tests cover successful first/subsequent sends, exhausted transient retry, permanent/unknown failures, interruption, zero-attempt normalization, and disabled delivery. | Retain the `maxAttempts >= 1` invariant and the complete dispatcher outcome matrix; classify only the exact loop-exit mapping after each full JaCoCo regeneration. Do not remove the terminal fallback or weaken retry behavior to alter the metric. |
| `app/expense-core/.../SyncController.kt:39`, invalid-cursor message fallback | `InvalidSyncCursorException` has a fixed non-null message in its production constructor. Existing `SyncControllerTest` cases exercise malformed, cross-group, and expired cursors through both public routes, so the public `ERR_02` mapping is covered; only the nullable-message fallback is unreachable under the exception contract. | Retain both public sync routes and the malformed/cross-group/expired cursor tests; classify only the null-message fallback after report regeneration. Do not weaken the exception message contract or use reflection to manufacture a null message. |
| `app/expense-core/.../SearchController.kt:73`, `search$lambda$0$0` | `SearchControllerTest` exercises authenticated search, reader-policy context, filtering, pagination, invalid limits/cursors, and non-member rejection. The remaining JaCoCo mapping belongs to the telemetry/context lambda generated around the approved query; it has no separate public result beyond the surrounding search request. | Retain the controller authorization, policy-context, validation, and result tests; classify only the generated lambda mapping after report regeneration. Do not add synthetic context corruption or alter telemetry behavior for metric closure. |
| `app/expense-core/.../ExpenseSearch.kt:93`, `csv` | `ExpenseSearchTest` covers populated exports, max-row rejection, formula/delimiter/newline/quote safety, and a valid zero-match header-only export. The remaining JaCoCo arm is the generated empty/non-empty iteration mapping inside CSV construction, not an untested export contract. | Retain the populated and header-only export assertions plus max-row and serialization safety tests; classify only the generated iteration mapping after report regeneration. Do not delete the export loop or change CSV output to affect JaCoCo. |

The private `ProfileController.mapErrorCode` record is intentionally **not**
listed here: it is currently unreferenced rather than structurally unreachable.
It remains an open review item; deleting it or weakening the controller merely
to change JaCoCo would violate QA-10’s coverage-through-tests rule.

Methods named `<init>`, `equals`, `hashCode`, `toString`, Kotlin `$lambda$`,
and compiler-generated value/boxing methods require structural classification
only when the underlying production behavior is covered by an explicit test.
For example, testing a data class's equality behavior is valid; excluding all
`equals` methods merely because they are generated is not. Methods with domain,
transport, persistence, messaging, security, or configuration behavior must
receive a normal QA10 row even when JaCoCo reports partial coverage.

The 39-method inventory is a discovery baseline, not closure evidence. QA-10
cannot move to done until the inventory is rerun after each test increment and
the count is zero or every residual entry has a reviewed structural rationale.

### Closure increment: QA10-B02 bearer authorization boundary

`app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/graphql/BearerAuthorizationTest.kt`
specifies the local contract for GraphQL bearer handling: the authorization
scheme is case-insensitive, surrounding whitespace is removed, missing or
malformed credentials are rejected, supported Spring Security principal forms
are extracted consistently, and blank or unknown subjects are not admitted.
The test must pass without forwarding a blank credential or treating an
unrecognized principal as authenticated. This closes only the pure helper
boundary; deployed GraphQL authentication, authorization, redaction, and
subscription-limit evidence remain required under QA10-B02 and QA10-E2E01.

`app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/graphql/GraphQlLimitErrorInstrumentationTest.kt`
specifies that maximum query depth and complexity become `RATE_LIMITED` with a
retry hint, unrelated execution failures become `VALIDATION_FAILED`, and
already classified errors are preserved. This is instrumentation evidence;
the deployed depth/complexity admission thresholds and cancellation behavior
still require the GraphQL transport and E2E suites.

`GraphQlScalarConfigurationTest` also rejects unsupported literal types for
`MoneyMinor` and `DateTime`, in addition to malformed values. The scalar
acceptance remains a local coercion contract; schema-level request handling
must still be exercised through GraphQL transport tests.

### Closure increment: QA10-D01 broker-envelope validation boundary

`app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/consumer/transport/BrokerEnvelopeParserTest.kt`
specifies the envelope contract for required UUID/string/integer/timestamp
metadata, positive schema and revision values, supported scalar payload
conversion, malformed JSON, and non-object payload rejection. It proves
invalid messages fail before notification delivery; RabbitMQ acknowledgement,
requeue, encryption, redaction, and Mailpit delivery remain separate
integration/E2E acceptance requirements.

`DeliveryPolicyTest` and `RetryPolicyTest` retain explicit local acceptance for
both enabled-channel combinations, duplicate suppression, success,
permanent-failure, bounded retry, and invalid attempt/configuration values.
These policy tests do not close the required durable inbox, broker, or SMTP
delivery evidence.

`EmailDispatcherTest` also specifies that unknown sender failures are permanent,
zero configured attempts still perform exactly one send, and recipient
whitespace is normalized before dispatch. SMTP timeout/authentication and
Mailpit retry evidence remain required at the integration/E2E layer.

`ExpenseSearchTest` now explicitly covers the public `ExpenseSearch.page`
cursor boundary: null cursors remain absent, valid opaque cursors decode to the
stored key, and malformed or blank values return the catalogued validation error.
The standalone `decodeSearchCursor` helper is also covered for null, valid,
malformed, and blank values. Pagination ordering,
subject/group authorization, durable query behavior, and export transport
evidence remain separate acceptance requirements.

The same search unit suite now asserts all formula prefixes (`=`, `+`, `-`,
`@`) and CSV comma/quote/newline escaping. This protects local export
serialization against formula injection without claiming that authorized
deployed export behavior or durable query isolation is covered.

Search pagination now also rejects zero/over-limit pages and malformed
currency filters at the domain boundary; controller validation and authorized
PostgreSQL query behavior remain separate evidence layers. The export suite
also covers a valid zero-match request and asserts the header-only CSV response.
The JaCoCo `csv` residual remains in the exact inventory because this
acceptance case did not change the compiler mapping; it is not a reason to
remove or restructure the export implementation.

### Closure increment: QA10-E01 error/correlation boundary

`libs/errors/src/test/kotlin/com/subhrodip/squarewise/errors/GlobalErrorHandlerTest.kt`
now checks all twelve catalog status mappings, problem metadata, the bounded
rate-limit retry header, and the bodyless 406 response. The new
`RequestIdContextAndFilterTest` checks valid propagation, invalid-ID replacement,
generated UUID response headers, cleanup after normal execution, and cleanup
after exceptions. The focused command passed 11 tests and the regenerated
module report now shows 99.4% line coverage and no missed branch methods in
the fresh report; deployed framework wiring and redaction branches remain
environment evidence requirements.

`AllocationCalculator` is the first branch-complete production slice after the
increment: its regenerated report has 0 missed lines, 0 missed branches, and 0
missed methods across `equal`, `exact`, `percentage`, `weightedShares`, and
`calculate`. At that first increment, the surrounding `ExpenseValidator` and
property-based financial arithmetic criteria remained open under QA10-C02.

The follow-up `ExpenseValidatorTest` increment now also reports 0 missed lines,
0 missed branches, and 0 missed methods for `parseAndValidateAmount`,
`validatePayers`, `mapDomainPayers`, and `mapDomainAllocations`. Financial
property tests outside these two pure components remain open.

The `AuthEmailOutboxPublisherTest` increment now covers the four local
publication outcomes: empty claim, successful JSON envelope publication and
acknowledgement, broker failure with bounded retry, and serialization failure
without a send. The focused publisher test passes, and the regenerated
Accounts report shows 0 missed lines, 0 missed branches, and 0 missed methods
for `AuthEmailOutboxPublisher.publishOne`. This is unit-level adapter evidence;
QA10-A01 remains open for real RabbitMQ confirmation/redelivery and the
deployed Notifications auth-email journey.

The `OutboxAuthEmailSenderTest` increment covers protection with the exact
recipient/template context and append failure propagation using the real
`AuthEmailOutboxService` against a repository double. The Spring
`AuthPersistenceTest` now also invokes the production sender and verifies a
real transactional outbox row contains only a decryptable protected envelope,
the expected template/expiry, and `PENDING` status. The target sender reports
0 missed lines and methods. QA10-A02 still requires a failure-in-transaction
rollback assertion if the broader transaction boundary changes; the current
integration test proves persistence and cryptographic handoff, not broker
delivery.

The `RedisRateLimitBucketStoreTest` increment covers the adapter's atomic
script invocation, hex-key handoff, epoch arguments, bounded TTL, allow result,
null-result fail-closed path, and Redis-exception wrapping. The regenerated
Accounts report shows 0 missed lines, branches, and methods for
`acquireAtomically`. This remains mocked-adapter evidence; QA10-A03 still
requires a live shared-Redis concurrency/window-reset/outage test and public
multi-replica 429 evidence.

The `DbOperationPolicyTest` increment now executes under the Gradle wrapper and
covers valid writer/reader routes, operation-name grammar rejection, reader
eligibility for non-query kinds, and the strong-consistency/reader conflict.
The remaining constructor record is retained for structural review; it is not
closed by deleting or simplifying the policy invariants.

### Current QA10-E02 residual acceptance targets

The current regenerated inventory contains two E02 records. They remain
explicitly open until the following evidence is attached:

| Production target | Current evidence | Required closure evidence |
| --- | --- | --- |
| `DbReaderHealth.state` | Open-circuit before-expiry, exact-deadline, and after-expiry behavior are now directly asserted; residual JaCoCo branches require source/bytecode classification. | Preserve all three timing boundaries and classify only compiler/nullability-generated paths after reviewing the report mapping, or add a behavior test if a reachable state is identified. |
| `DbOperationPolicy::<init>` | All policy invariants and valid writer/reader routes are asserted in `DbOperationPolicyTest`. | Review the constructor branch mapping; retain the invariant tests and classify only generated short-circuit/data-class instrumentation, never remove a policy guard to change the count. |

### Current QA10-A07 method-level residual ledger

The following two records are the exact A07 assignment from the current JaCoCo
inventory. Counts are discovery values, not closure claims. Each row must be
rechecked after the corresponding tests run; a test specification does not
remove a record until a regenerated report does so. Previously listed
`AesGcmCredentialEnvelopeProtector` and `TokenSessionService` records are no
longer present in the regenerated inventory and are retained only in earlier
progress history.

| Production target and source line | Method | Missed branches | Required evidence or classification |
| --- | --- | ---: | --- |
| `LoginVerificationService.kt:60` | `verify` | 1 | Execute invalid/replayed credential and existing-identity reuse versus new enrollment with durable session state. |
| `SessionPolicy.kt:82` | `isExpired` | 1 | Existing `SessionPolicyTest` covers exact idle/absolute expiry and clock-skew boundaries; classify only the remaining short-circuit path if the regenerated mapping proves it unreachable under the constructor invariant. |

## Required evidence ladder

Each production behavior is assigned the minimum evidence needed:

| ID | Layer | Required proof |
| --- | --- | --- |
| U | Unit/domain | Deterministic rule, boundary, exception, and invariant assertions without Spring or I/O |
| T | Controller/transport | HTTP/GraphQL status, schema/problem mapping, authentication, authorization, and request validation |
| P | Persistence integration | Real PostgreSQL/Flyway constraints, transaction boundaries, locking, versioning, and durable side effects |
| M | Messaging integration | Real RabbitMQ confirmation, ack/reject/requeue, deduplication, DLQ, retry, and outbox state transitions |
| E | Deployed E2E | Signed identity, real service boundaries, public response, durable state, and asynchronous effects |
| O | Operations/environment | Multi-replica, capacity, failover, restore, rotation, scan, alert, and rollback artifacts |

For a row marked `U+T+P+E`, all four layers are required. A unit test may be
listed as supporting evidence but cannot replace the higher layer.

## Concrete test destinations

Each backlog row has an owned test destination. Existing files are extended
only when their responsibility matches; otherwise the named file is the
planned new test. This prevents a broad suite from absorbing an unrelated gap.

| Row | Unit/transport destination | Integration/E2E destination |
| --- | --- | --- |
| QA10-A01 | `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/auth/delivery/service/AuthEmailOutboxPublisherTest.kt` | Extend `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/email/AuthEmailRabbitListenerTest.kt`; add `tests/e2e/test_auth_email_delivery.py`. |
| QA10-A02 | `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/auth/delivery/service/OutboxAuthEmailSenderTest.kt` | `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/auth/AuthPersistenceTest.kt`; add caller-transaction rollback coverage. |
| QA10-A03 | `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/auth/abuse/RedisRateLimitBucketStoreTest.kt` | Retain the existing `tests/e2e/test_auth_cache_resilience.py`, `test_auth_login_replicas.py`, and `test_auth_bff_replicas.py` artifacts, then add hosted atomic-concurrency/proxy-chain evidence. |
| QA10-A04 | Add `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/auth/provider/ExternalOidcTokenProviderTest.kt` | Extend `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/auth/login/DeployedPasswordlessTokenIntegrationTest.kt` with explicitly enabled-provider startup/issuance. |
| QA10-A05 | `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/security/OidcSubjectValidatorTest.kt`; add deployed security configuration tests | `tests/e2e/test_oidc_negative.py` and signed multi-service decoder probes. |
| QA10-A06 | `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/profile/ProfileControllerTest.kt` | Add signed-persona profile authorization cases to `tests/e2e/test_rest_edge_cases.py`. |
| QA10-A07 | `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/auth/session/TokenSessionServiceTest.kt` and `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/auth/login/LoginVerificationServiceTest.kt` | Extend passwordless journey in `tests/e2e/test_product_journey.py`; assert durable replay/revocation state. |
| QA10-A08 | `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/auth/identity/EmailAddressTest.kt` | Not a deployed boundary; retain deterministic unit/property coverage. |
| QA10-B01 | `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/RestGatewayTest.kt`, `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/BffFanoutTest.kt`, and `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/transport/BffGatewayFiltersTest.kt` | Add upstream timeout/malformed-response cases to `tests/e2e/test_rest_edge_cases.py`. |
| QA10-B02 | `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/config/BearerTokenContextWebFilterTest.kt`, `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/graphql/BearerAuthorizationTest.kt`, `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/graphql/GraphQlLimitErrorInstrumentationTest.kt`, `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/graphql/GraphqlHttpTransportTest.kt`, and `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/graphql/GraphQlScalarConfigurationTest.kt` | `BearerTokenContextWebFilterTest` now proves case-insensitive trimmed bearer capture, valid watermark capture, malformed-header/watermark omission, and exchange retention. Extend GraphQL HTTP and WebSocket suites with every limit/error dimension; prove bearer extraction, subject normalization, and public limit-error mapping at the unit boundary. |
| QA10-B03 | `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/LiveUpdateFanoutTest.kt`, `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/messaging/BffEventConsumerTest.kt`, and `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/messaging/RabbitBffEventListenerTest.kt` | Extend `tests/e2e/test_concurrency_subscriptions.py` and add reconnect/replay cases. |
| QA10-B04 | `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/config/BrowserCsrfWebFilterTest.kt`, `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/config/BrowserOriginPolicyTest.kt`, and cookie filter tests | Add browser-cookie mutation and WebSocket upgrade cases to the public E2E harness. |
| QA10-C01 | `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/expenses/JpaExpenseStoreTest.kt`, `JpaExpenseStoreTransactionRollbackTest`, controller tests, and outbox tests | `JpaExpenseStoreTest` now covers actor-scoped active membership identifiers, missing-group rejection with no financial side effects, and duplicate/inactive participant rejection before mutation; `JpaExpenseStoreTransactionRollbackTest` injects an outbox-port failure and asserts expense, idempotency, postings, sync, and group revision rollback; outbox tests cover retry policy, state boundaries, and publisher delivery-policy validation. Extend `tests/e2e/test_product_journey.py` with deployed write-failure rollback and ledger reconciliation. |
| QA10-C02 | `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/expenses/AllocationCalculatorTest.kt` and `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/expenses/ExpenseValidatorTest.kt` | Fixed-seed generated and table tests now exercise deterministic conservation and validation invariants; retain reproducibility and extend only when a new arithmetic rule is introduced. |
| QA10-C03 | `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/recurring/RecurringExpenseServiceTest.kt`, `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/recurring/RecurringExpenseWorkerTest.kt`, and `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/recurring/RecurringExpenseControllerTest.kt` | Unit/integration coverage now proves a zero catch-up budget produces no occurrence or schedule advance; add multi-worker and bounded catch-up cases to the Docker E2E suite. |
| QA10-C04 | `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/groups/JpaGroupStoreTest.kt` and `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/groups/GroupControllerTest.kt`; the JPA suite now covers archived member operations, invalid placeholder/invite tokens, and unknown membership removal without additional effects. | Extend signed-persona lifecycle coverage in `tests/e2e/test_product_journey.py`. |
| QA10-C05 | `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/settlements/JpaSettlementStoreTest.kt`, `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/settlements/SettlementServiceTest.kt`, and `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/settlements/PostgresSettlementReconciliationTest.kt` | Existing JPA and opt-in PostgreSQL tests cover settlement/reversal postings, replay, cardinality, directional signs, and zero-sum totals. Extend financial lifecycle E2E and reconciliation operations with intentional-corruption detection and rollback/failure evidence. |
| QA10-C06 | `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/sync/JpaSynchronizationStoreTest.kt`, `SynchronizationTest.kt`, and `SyncControllerTest.kt`; the suites now cover blank identifiers, invalid limits, malformed cursor fields, and blank authenticated subjects. | Extend `tests/e2e/test_offline_resilience.py` and causal cursor probes. |
| QA10-D01 | `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/consumer/transport/BrokerEnvelopeParserTest.kt`, `AuthEmailRabbitListenerTest.kt`, `AuthEmailDeliveryConsumerTest.kt`, envelope tests, and `AuthEmailSecurityConfigurationTest.kt` | Key configuration, envelope framing, numeric metadata validation, explicit-null/empty-body rejection, context-bound decryption, template mapping, expiry/type rejection, and plaintext non-dispatch are covered locally. Add broker/Mailpit auth-email delivery to `tests/e2e/test_auth_email_delivery.py`; consumer tests must still prove listener ack/requeue, broker retry/redelivery, and deployed delivery. |
| QA10-D02 | `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/consumer/RabbitNotificationListenerTest.kt`, `BrokerEnvelopeConversionTest.kt`, `NotificationEventConsumerTest.kt`, `NotificationEventConsumerUnitTest.kt`, and `RedisDeliveryRateLimiterTest.kt` | The conversion test now covers malformed notification-ID fallback, blank-field safe fallbacks, recipient/description mapping, and bounded fields; add real RabbitMQ ack/retry/DLQ and shared-Redis TTL/concurrency cases to the chaos E2E suite. |
| QA10-D03 | `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/email/EmailDispatcherTest.kt`, `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/email/smtp/SmtpJavaMailSenderTest.kt`, and `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/email/smtp/SimpleMailMessageTest.kt` | Add Mailpit failure/retry assertions to deployed notification E2E. |
| QA10-D04 | `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/inbox/InboxControllerTest.kt`, `JpaNotificationInboxStoreTest.kt`, and `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/preferences/JpaPreferenceStoreTest.kt`; `tests/e2e/test_rest_edge_cases.py` now proves signed default retrieval, update persistence, and subject isolation for preferences. | Extend signed inbox listing/mark-read and database-failure cases in REST-edge E2E. |
| QA10-E01 | `libs/errors/src/test/kotlin/com/subhrodip/squarewise/errors/GlobalErrorHandlerTest.kt` and `libs/errors/src/test/kotlin/com/subhrodip/squarewise/errors/request/RequestIdContextAndFilterTest.kt` | Assert public error envelopes and correlation behavior in REST/GraphQL E2E; the current JaCoCo report is fresh, while deployed framework wiring, redaction, and transport serialization remain open. |
| QA10-E02 | `libs/db/src/test/kotlin/com/subhrodip/squarewise/db/config/DbAutoConfigurationTest.kt`, `libs/db/src/test/kotlin/com/subhrodip/squarewise/db/policy/DbOperationPolicyTest.kt`, and health/routing tests | `DbAutoConfigurationTest` now covers normal reader-pool construction and `DbReaderHealthTest` covers the exact circuit deadline; `DbRouteGuardTest` asserts exception restoration. Extend `tests/e2e/test_causal_watermark.py` and replica failure/recovery suites. |
| QA10-E03 | `libs/security/src/test/kotlin/com/subhrodip/squarewise/security/OidcJwtDecoderFactoryTest.kt`, `ReactiveOidcJwtDecoderFactoryTest.kt`, and policy tests | Local servlet/reactive discovery, JWKS signature, issuer, audience, temporal, subject, algorithm, and configuration guards are covered. Extend signed invalid-token, key-rotation, missing metadata/JWKS, and security-header E2E across all services. |
| QA10-E04 | `libs/ids/src/test/kotlin/com/subhrodip/squarewise/ids/UuidGeneratorTest.kt`; add static contract assertions | Contract validator and public-surface validation remain the integration destination. |
| QA10-E05 | `libs/observability/src/test/kotlin/com/subhrodip/squarewise/observability/db/DbTelemetryTest.kt` now asserts registry-backed metric names, bounded labels, negative-duration clamping, and exact counts; AUTH-09 adds bounded rate-limit outcome dashboard/alert assets and local metric assertions. | Retain deployed scrape/cardinality and alert-routing evidence under QA10-E2E06/E2E07. |

### Deployed E2E and environment destinations

| Row | Existing suite to extend | Dedicated destination or artifact still required |
| --- | --- | --- |
| QA10-E2E01 | `tests/e2e/test_product_journey.py`, `test_rest_edge_cases.py`, and `test_oidc_negative.py` provide partial signed-persona and negative-path evidence. | `docs/quality/qa10-operation-acceptance-ledger.md` now names all 54 operations and their required assertions. Add/extend `tests/e2e/test_operation_authorization_matrix.py` to execute every row for signed personas; a source reference alone does not close an operation. |
| QA10-E2E02 | `tests/e2e/test_auth_email_delivery.py` proves CODE delivery, one-time verification, replay rejection, refresh-family rejection, and logout revocation through Mailpit. | Extend the existing suite for LINK delivery, expiry, wrong-subject redemption, rate-limit/error redaction, broker retry/DLQ, and log/output secret absence. Do not describe this as a missing file. |
| QA10-E2E03 | `tests/e2e/test_auth_cache_resilience.py`, `test_auth_login_replicas.py`, `test_auth_bff_replicas.py`, `test_auth_rate_limit_surfaces.py`, and `test_auth_notification_redis_outage.py` provide local shared-Redis, multi-process, public-surface, and outage evidence. | Extend the local matrix with normalized proxy-identity/concurrent same-subject assertions and retain hosted artifacts proving the same behavior, bounded `Retry-After`, and production failover. |
| QA10-E2E04 | `tests/e2e/test_concurrency_subscriptions.py` and `test_oidc_websocket_negative.py` provide authenticated subscription, malformed-operation, reconnect, invalid-token, and local membership-revocation/reconnect evidence. | Extend protocol coverage for malformed RFC frames, duplicate subscription IDs, heartbeat timeout, sustained backpressure, reconnect cursor recovery, and no duplicate/lost invalidation; retain hosted broker/reconnect artifacts. |
| QA10-E2E05 | `tests/e2e/test_causal_watermark.py`, replica smoke scripts, and `test_concurrency_subscriptions.py` provide partial watermark, routing, and fanout evidence. | Preserve artifacts from multi-replica runs proving lag/fallback/recovery, watermark monotonicity, no stale strong reads, no sticky-session dependency, and fanout consistency; the current local scripts do not close this environment gate. |
| QA10-E2E06 | `tests/load/k6/`, `tests/performance/capacity-smoke.sh`, and documented production-validation procedures exist as harnesses; the local two-BFF GraphQL admission run recorded 176 requests at 35.06/s, 0% failures, and 13.58 ms p95. | Execute an approved production-like workload and retain p50/p95/p99, error-rate, pool, queue, and reconciliation artifacts for the declared thresholds; local execution is not production/SLO evidence. |
| QA10-E2E07 | `tests/e2e/test_chaos_recovery.py`, `tests/performance/recovery-drill.sh`, and release-gate tooling provide partial local probes. | Produce reviewed production-like failover, PITR restore, rotation, scanning, rollback, alert, RPO/RTO, and zero-loss artifacts; local chaos scripts alone do not close this gate. |

## Missing or insufficient unit/transport/integration coverage

The following rows were identified by production-source inspection and missed
JaCoCo classes. The acceptance criteria are deliberately behavior-based so a
test cannot close a row by merely executing a line.

### Accounts and authentication

| ID | Production target | Missing/weak evidence | Required acceptance criteria |
| --- | --- | --- | --- |
| QA10-A01 | `AuthEmailOutboxPublisher.publishOne` | Unit coverage now exercises the complete local claim/serialize/send state machine; real RabbitMQ confirmation, redelivery, and deployed auth-email delivery remain open. | `U+M`: empty claim returns `EMPTY`; valid event has the exact event type, schema version, recipient, encrypted credential, expiry, message ID, exchange, and routing key; successful publish acknowledges exactly once; AMQP and serialization failures reject with retry timing and bounded attempts; no plaintext credential is serialized or logged. The unit increment passes all four cases and reports 0 missed lines, branches, and methods for `publishOne`; a real-broker/deployed test must still prove confirmation, retry/redelivery, and downstream delivery. |
| QA10-A02 | `OutboxAuthEmailSender.send` | Unit and Spring persistence coverage now prove context-bound encryption, protected-only durable storage, `PENDING` state, expiry/template mapping, and append failure propagation. A broader caller transaction rollback test and real broker path remain open. | `U+P`: recipient/template are used as AAD context; credential is encrypted before append; the raw credential never reaches the outbox; append failure leaves no successful result; returned status is `QUEUED` only after the append call. The current increment passes the unit and Spring persistence cases; add a caller transaction rollback test if sender invocation is part of a larger credential transaction. |
| QA10-A03 | `RedisRateLimitBucketStore.acquireAtomically` | Unit adapter coverage plus local shared-Redis and multi-replica probes now prove public cap, outage/recovery, HMAC-derived keys, and bounded store-error telemetry; hosted atomicity and production evidence remain open. | `P+E`: first request, window reset, cooldown denial, maximum denial, atomic concurrent callers, Redis nil result, and Redis exception are covered; keys are HMAC-derived/hex encoded, TTL is bounded, and store failure maps to fail-closed `429 RATE_LIMITED` with `Retry-After`. Retain hosted multi-replica/concurrency artifacts and production failover evidence. |
| QA10-A04 | `ExternalOidcTokenProvider.issueAccessToken`, `AuthSessionConfiguration` | The default deployed path now selects `AsymmetricJwtTokenProvider` and `DeployedPasswordlessTokenIntegrationTest` proves RS256 issuance/validation, but explicitly enabling `squarewise.security.oidc.external-provider.enabled=true` still selects the throwing external adapter. The optional external-provider path is therefore not a completed OIDC exchange. | `U+T+E`: either implement and exercise the real exchange/delegation contract, or reject/disable the external-provider property at startup until it is implemented; no runtime request may reach the current `UnsupportedOperationException`; production never silently falls back to an internal/test provider. Acceptance must include default and explicitly enabled-provider profiles, bean selection, startup behavior, token issuance, and deployed validation. |
| QA10-A05 | `ProductionSecurityConfig`, `OidcSubjectValidator`, JWT decoder wiring | Configuration classes have missed lines and current focused tests mostly validate policy helpers. | `T+E`: valid issuer/audience/algorithm/signature/subject succeeds; wrong issuer, audience, algorithm, signature, expiry, not-before, blank/oversized subject, missing JWKS, and unavailable issuer fail closed; all four deployed services use the intended decoder and no fallback decoder is active outside an explicitly test-only profile. |
| QA10-A06 | `ProfileController` profile/deletion/export routes and `JpaDeletionRequestStore` | Several branches remain uncovered, especially nullable principals, object authorization, query context, and error mapping. `JpaRequestStoresTest` and `JpaDeletionRequestStoreTest` now specify null results for missing deletion records, exact invalid-subject rejection, terminal cancellation/completion state transitions, and a valid request with no resolvable profile account where session revocation is skipped. | `T+P+E`: authenticated self-read/update succeeds; missing profile, blank/missing principal, foreign account, missing account, internal workload role, duplicate batch IDs, empty batch, over-limit batch, deletion request, export request, listing, missing deletion record, terminal state transitions, invalid subject, and store failure each assert exact status/code and no unauthorized query or mutation side effect. |
| QA10-A07 | `AsymmetricJwtTokenProvider`, `LoginStartService`, `LoginVerificationService`, `TokenSessionService`, `LoginCredentialService`, cleanup and identity stores | Partial line coverage does not demonstrate blank issuer/audience guards, existing-identity reuse, generic rate-limit failure, credential-delivery failure, replay, subject mismatch, deletion, expiry, or transaction behavior together. `AsymmetricJwtTokenProviderTest` now specifies both constructor guards; `LoginStartServiceTest` specifies both delivery templates, generic issuance failure, rate-limit denial, and rate-limit-store outage; `LoginVerificationServiceTest` specifies reuse of an enrolled identity without provisioning a second account. The full Java 25 wrapper test/report has now executed these suites; residual JaCoCo records are the exact database-invariant mappings listed in the ledger. | `U+P+E`: token configuration rejects blank issuer/audience; an enrolled identity reuses its stable account and subject; one-time credential is single-use and expiry-bound; login admission and delivery failures remain generic and use stable `ERR_11`; wrong recipient/subject, replay, session mismatch, revoked/deleted account, refresh rotation/reuse, logout, cleanup, and concurrent redemption produce exactly one durable outcome and the required redacted audit event. The Accounts persistence suite now also proves expired-session family revocation, deletion-request denial with family revocation, missing-identity fail-closed rejection without an unintended family mutation, matching-subject logout revocation, and blank/unknown/missing-identity logout no-ops; deployed replay/concurrency evidence remains open. |
| QA10-A08 | `EmailAddress.parse` | Existing tests cover canonicalization and common malformed input; boundary tests now add exact maximum local/complete lengths and invalid IDN label separators. The current full JaCoCo report still records 11 mapped branches in this value object; the residual mappings require structural review rather than weakened validation. | `U`: NFC/root-locale canonicalization and IDN conversion are deterministic; empty/overlong local parts, total length, control/whitespace characters, malformed separators, empty or repeated domain labels, invalid IDN input, and labels over 63 characters all reject with `IllegalArgumentException`; accepted output contains exactly one canonical separator and no raw credential material. |

### BFF, GraphQL, and realtime

| ID | Production target | Missing/weak evidence | Required acceptance criteria |
| --- | --- | --- | --- |
| QA10-B01 | `AccountsGateway`, `ExpenseCoreGateway`, `RestGateway`, `BffGatewayFilters` | `AccountsGatewayTest` now exercises optional bearer omission/presence, profile decoding, and upstream status redaction; `BffFanoutTest` covers the main Expense Core HTTP-double paths; `BffGatewayFiltersTest` covers bearer/watermark forwarding, numeric advancement over a lower LSN, greatest-valid watermark retention, blank values, malformed and absent downstream watermarks, and missing exchange context. Running-BFF timeout/connection and complete operation failure evidence remain open. | `U+T+E`: bearer token and request ID propagate, required watermark is forwarded and greatest valid downstream watermark is returned, no-context requests remain credential-free, an absent or malformed downstream watermark leaves response state unchanged, 2-second timeout/connection/malformed JSON/non-2xx/empty body/partial result map to stable GraphQL extensions, and sensitive upstream details are redacted. |
| QA10-B02 | `BearerTokenContextWebFilter`, `BearerAuthorization`, `GraphQlExceptionResolver`, `GraphQlLimitErrorInstrumentation`, scalar configuration | `BearerTokenContextWebFilterTest` covers bearer/watermark context capture and malformed-input omission; `GraphQlExceptionResolverTest` now covers every catalog code, all upstream status mappings, and fallback exception classes; `GraphQlScalarConfigurationTest` covers DateTime and MoneyMinor string-literal acceptance/rejection, including nullable AST values. GraphQL HTTP/WebSocket transport and subscription-limit evidence remain open. | `U+T+E`: bearer extraction accepts only a non-blank case-insensitive Bearer value, supported principal forms normalize to one non-blank subject, reactive context retains the exchange and only valid causal watermarks, every catalog error preserves public code, safe detail, classification, and request metadata; depth/complexity, per-subject query, mutation, and subscription caps reject deterministically; cancellation releases admission; scalar invalid/null/overflow values are rejected without resolver execution. |
| QA10-B03 | `LiveUpdateFanout`, `BffEventConsumer`, `RabbitBffEventListener` | Fanout configuration, input, bounded queue, expiry, membership revocation, and malformed removal payload unit boundaries are covered. The producer/consumer event-name mismatch was corrected: Expense Core publishes `member.removed.v1`, and the BFF regression test now exercises that exact value. Generated predicate mappings, duplicate/poison/transient broker outcomes, reconnect replay, and deployed WebSocket behavior remain open. | `U+M+E`: duplicate event IDs produce one invalidation, unrelated groups/subjects receive nothing, membership removal terminates active subscriptions, malformed/poison/transient messages are acked/rejected/requeued according to policy, and reconnect requires explicit cursor recovery with no duplicate financial event. The unit evidence must retain exact subscription counts, queue ordering/drop behavior, revocation completion, expiry cleanup, broker acknowledgement/requeue assertions, and deployed signed-persona WebSocket artifacts. |
| QA10-B04 | `BrowserOriginPolicy`, `BrowserOriginWebFilter`, `BrowserCsrfWebFilter`, cookie/session filters | `BrowserOriginPolicyTest` explicitly covers absent origin, case normalization, default and non-default ports, malformed values, credentials, paths, queries, fragments, unsupported schemes, and wildcard rejection. In-process filter tests still do not prove the complete browser-cookie mutation chain. | `T+E`: allowed origin plus matching CSRF succeeds; missing/mismatched token, disallowed origin, unsafe method, access-cookie mutation, bearer-only native client, preflight, and WebSocket upgrade follow the documented policy; no cookie/token is leaked in logs or responses. |

### Expense Core financial, scheduling, and sync logic

| ID | Production target | Missing/weak evidence | Required acceptance criteria |
| --- | --- | --- | --- |
| QA10-C01 | `AllocationPreviewController`, `ExpenseController`, `JpaGroupStore`, `JpaExpenseStore`, and outbox adapters | Allocation-preview tests now exercise non-numeric and negative totals before calculator invocation. Report still shows missed lines in the largest financial adapter; controller create/update financial-input validation, actor-scoped create/update/delete membership, participant, archived-group validation, invitation expiry/claim replay, duplicate-ID payload comparison, update lookup/soft-delete rejection, payer/allocation replacement, injected outbox-failure rollback, in-memory/durable outbox retry-policy validation, durable claim eligibility, blank/explicit-null category defaulting, payer/allocation-count short-circuit boundaries, custom recurring request mapping, missing/foreign schedule lookup boundaries, recurring membership authorization, amount parsing, and overlong category validation now have persistence or transport assertions, but append/acknowledge residuals, recurring update mapping, null-principal forwarding branches, and successful lifecycle E2E/rollback evidence remain open. | `U+T+P+E`: create/update/idempotent replay/tampered replay, stale version, missing/archived group, missing/deleted expense, unauthorized actor, malformed/non-positive payer amounts, payer currency mismatch or sum mismatch, invalid exact allocation, active membership identifiers, payer/allocation replacement, expired/revoked/claimed invitation, same-subject idempotent claim, competing-subject claim conflict, duplicate/inactive participants, conflicting duplicate-ID description/currency/payer/allocation payloads, retry max-attempt and delay validation across adapters, claim eligibility, category defaulting including explicit null, participant-count lower/upper and simultaneous payer/allocation bounds, recurring custom payer/allocation mapping, missing/foreign schedule lookup/pause/resume, recurring invalid amount and membership outcomes, unknown-event no-op, audit row, sync revision, postings, balance, outbox, and rollback-after-each-write-failure are asserted; rejected validation leaves no revision, posting, idempotency, or expense row and the ledger remains zero-sum. |
| QA10-C02 | `ExpenseValidator`, `AllocationCalculator`, `FinancialArithmetic` | `AllocationCalculatorTest` now exercises fixed-seed generated cases plus explicit boundaries for permutation invariance, non-negative allocations, and minor-unit conservation across equal, weighted, and percentage modes. `ExpenseValidatorTest` covers parsing, payer sums, currency mismatch, and DTO mapping. Deployed evidence is not required for this pure deterministic slice. | `U`: property/table tests cover zero, negative, maximum, overflow, fractional/unknown currency, duplicate participants, missing payer, percentages not summing to 100, exact minor-unit remainder distribution, deterministic ordering, and zero-sum preservation with reproducible seeds. |
| QA10-C03 | `RecurringExpenseService`, `RecurringExpenseController`, worker/claim locking | Coverage exists for basic lifecycle but not all date, catch-up, lock, and generated-expense failure branches. `RecurrencePolicyTest` now proves a monthly schedule without an explicit day falls back to the source occurrence day. `RecurringExpenseServiceTest` exercises explicit schedule IDs, valid lower/upper day-of-month boundaries, zero catch-up budget without state advance, create/update missing or cross-group outcomes, validation guards, both payer/allocation membership rejection directions, both reachable one-sided create/update custom-specification paths, and derived payer/allocation behavior; `RecurringExpenseControllerTest` now verifies both one-sided request mappings; `RecurringExpenseFailureTest` now proves a downstream expense-store failure pauses the schedule, creates no occurrence, and emits `generation_error`. Remaining date/membership/build fallback mappings remain open. | `U+P+E`: weekly/monthly/month-end clamp/timezone/end-date policy, pause/resume idempotency, missing/cross-group schedule, concurrent worker claims, bounded catch-up including zero budget, deterministic occurrence IDs, duplicate prevention, failed occurrence rollback, and outbox/sync effects are asserted. |
| QA10-C04 | `JpaGroupStore`, invite/member lifecycle | Existing journey covers the happy path; persistence/unit coverage now also proves expired invitations, same-subject idempotent claim from a second invite, competing-subject rejection, archived-group rejection, removed/bound/ordinary-member placeholder targets, already-bound placeholder invitation rejection, removed-placeholder invitation rejection, repository-missing group/placeholder outcomes, and normal/targeted atomic claim races. One invitation predicate branch is now closed; the remaining JaCoCo mappings remain for review, while concurrent and deployed lifecycle evidence remain open. | `U+P+E`: invite expiry boundary, revoked/claimed/unknown token, same-subject replay semantics, simultaneous normal and targeted-placeholder claims, placeholder binding, member removal, removed-member token/session, archived group, revision/audit/outbox effects, repository race/missing-row outcomes, and non-member indistinguishable not-found behavior are asserted. |
| QA10-C05 | `JpaSettlementStore`, `SettlementService`, `SettlementController`, balances/suggestions/reconciliation | Settlement success, replay, and PostgreSQL posting reconciliation are covered; JPA replay comparison now covers both participants and amount, duplicate participant/currency balance rows aggregate before suggestion matching, one-sided debtor/creditor snapshots produce no invented transfer, missing/archived groups and active-group missing-settlement reversal fail closed without postings, service-level validation and the controller's nullable suggestion-engine fallback are covered, and missing/blank REST subjects fail closed with `UNAUTHENTICATED`; one strictly-positive transfer guard remains for invariant classification, alongside rollback evidence. | `U+P+E`: valid settlement/reversal, same participant, negative/non-numeric/overflow amount, missing/cross-group settlement, authenticated idempotency identity and conflict, blank/invalid idempotency inputs, blank reversal reason, duplicate balance aggregation, nullable suggestion engine, missing/blank/non-member principal authorization, concurrent reversal, balance recomputation from postings, intentional corruption detection, and zero-sum invariant are asserted. |
| QA10-C06 | `JpaSynchronizationStore`, sync controllers/cursors | `JpaSynchronizationStoreTest` now asserts an empty durable snapshot has no continuation cursor and no remaining changes; current E2E proves common cursor recovery, not all cursor ownership and transaction boundaries. | `U+P+E`: snapshot/change ordering, empty page, first/last page, limit bounds, malformed/expired/decreasing cursor, cross-group cursor, tombstone, membership loss, concurrent revision allocation, and no revision advance after rejected mutation are asserted. |

### Notifications and event delivery

| ID | Production target | Missing/weak evidence | Required acceptance criteria |
| --- | --- | --- | --- |
| QA10-D01 | `AuthEmailRabbitListener`, `AuthEmailDeliveryConsumer`, envelope parser | Parser/ack branches have partial coverage; listener behavior depends on RabbitMQ channel semantics. | `U+M+E`: valid encrypted envelope is consumed once; missing/wrong-type/malformed/expired payload is rejected without requeue; transient SMTP/decrypt failure requeues once then parks; duplicate event is acknowledged without a second email; no credential appears in logs or DLQ payloads. |
| QA10-D02 | `RabbitNotificationListener`, `NotificationEventConsumer`, `TransactionalNotificationEventProcessor`, `RedisDeliveryRateLimiter`, broker parser | Existing persistence tests cover applied/duplicate/concurrent inbox outcomes; broker conversion tests now prove subject precedence, group fallback, message fallback, and bounded fields; `NotificationEventConsumerTest` now also rejects blank and overlength subject, event-type, and message fields before either inbox or processed-event persistence. `NotificationEventConsumerUnitTest` specifies recipient trimming/validation, preference/rate suppression, durable constraint-duplicate acknowledgement, non-duplicate constraint propagation, preference/dispatcher failure isolation, and broker-retry propagation. Explicit transaction/ack coupling, poison/retry matrix, and production Redis admission evidence remain open. `RedisDeliveryRateLimiterTest` covers atomic key/arguments, allow/deny, null decisions, and Redis failure propagation. | `U+M+E`: applied, duplicate, malformed, poison, transient, and permanent failures produce exact DB transaction and manual ack/reject/requeue outcome; Redis admission uses the subject-scoped key, configured TTL/limit, atomic increment, and fail-closed store behavior; inbox, processed-event, preference, and email side effects commit together or roll back together. |
| QA10-D03 | `SmtpMailSender`/mail adapter, `EmailDispatcher`, `SimpleMailMessage` | `SmtpJavaMailSenderTest` exercises the local SMTP wire sequence, configured sender/optional-field fallbacks, rejected responses, and premature response streams; `EmailDispatcherTest` covers validation, retry, interruption, nested permanent causes, and direct I/O classification; `SimpleMailMessageTest` covers array equality/hash behavior, null-vs-empty semantics, empty-recipient lookup, property assignment, and safe rendering. External Mailpit/SMTP failure evidence remains open. | `U+M+E`: valid message maps recipient/template/body correctly; invalid recipient and missing preference are suppressed; SMTP timeout/auth/rejection is classified as retryable/permanent; retry count, DLQ/parking, metrics, and redaction are asserted. |
| QA10-D04 | Notification inbox/preferences stores/controllers | Local branch coverage is complete. `JpaPreferenceStoreTest` proves blank and overlength preference subjects are rejected before persistence. `InboxControllerTest` proves blank listing/mark-read subjects and valid cursors beyond stored data; `JpaNotificationInboxStoreTest` proves blank/overlength subjects plus blank and overlength event/message payloads are rejected before any row is created. Deployed isolation and database-failure evidence remain open. | `T+P+E`: subject isolation, default preferences, update versioning, blank/overlength subject rejection before query, invalid page/cursor bounds, valid cursor exhaustion, unknown notification, duplicate mark-read, missing membership/context, reader fallback, and database failure map to exact public errors without changing another subject’s rows. |

### Shared libraries and cross-cutting infrastructure

| ID | Production target | Missing/weak evidence | Required acceptance criteria |
| --- | --- | --- | --- |
| QA10-E01 | `GlobalErrorHandler`, `RequestIdFilter`, `RequestIdContext` | The current full JaCoCo report is fresh and still records the defensive `applicationException` mapping; unit evidence covers every catalog status plus the invalid-status fallback, while deployed framework wiring, redaction, and transport serialization are not proven by these unit tests. | `U+T`: every catalog error, framework validation, malformed body, missing binding, type mismatch, media negotiation, optimistic conflict, unexpected exception, and rate-limit response asserts status, content type, stable code, source, request ID, bounded detail, and `Retry-After`; valid/invalid/oversized request IDs are propagated or replaced and MDC is cleared. |
| QA10-E02 | `DbAutoConfiguration`, `DbRoutingDataSource`, `DbOperationPolicy`, reader health/lag | Focused policy, pool-bound, reader-health, and scheduler tests exist and the current JaCoCo report is freshly regenerated; configuration wiring, live datasource failure, and replica behavior still require persistence/deployed evidence. | `U+P+E`: writer/reader route policy, command/strong/eventual query classification, fallback on lag/disconnect, recovery, causal watermark capture/validation, Flyway writer datasource, bounded pool acquisition, scheduler lifecycle, and no reader use for writes/locks/claims are asserted. |
| QA10-E03 | `OidcJwtDecoderFactory`, reactive decoder, headers | Local servlet/reactive decoder parity now includes signed issuer-discovery/JWKS validation and wrong-audience rejection. Deployed provider behavior, rotation overlap, metadata/JWKS failure, and security-header parity remain open. | `U+T+E`: issuer, audience, algorithm, key source, temporal claims, invalid subject, key rotation overlap, missing metadata/JWKS, and security headers are identical across servlet and reactive services; failures never use a permissive decoder. |
| QA10-E04 | `ApiEndpoints`, event constants, IDs, error catalog | `UuidGeneratorTest` covers UUIDv7 generation, `EventConstantsTest` covers non-blank/versioned event metadata and unique routing keys, `ApiEndpointsTest` covers the previously unrepresented archive, placeholder, member-removal, and invite-revocation path builders, the locked Python audit verifies every OpenAPI REST path has a literal or named composed constant in `ApiEndpoints.kt`, a second audit assertion requires exact error-code parity between `error-catalog.yaml` and `ErrorCode.kt`, and a third assertion checks envelope required fields/header vocabulary/version floor. Full event-type/routing enumeration and duplicate/drift validation remain missing. | `U`: contract/static tests enumerate every endpoint/event/error code exactly once, detect duplicate or drifted paths/routing keys, validate UUID/ID generation invariants, and document generated/accessor-only classes as excluded only with evidence. |
| QA10-E05 | `DbTelemetry`, observability adapters | Missed metric branches can hide unbounded labels or incorrect timing. | `U+T+O`: success/failure/slow query, pool acquisition, fallback, lock-wait, deadlock, and subscription metrics use bounded labels only; counters/timers increment exactly once and dashboards/alerts consume the same names. |

### Event-type and routing inventory still requiring closure

The source scan found these event values in production or their direct
producer/consumer tests: `auth.email.requested.v1`, `group.renamed.v1`,
`group.archived.v1`, `member.placeholder_added.v1`, `member.removed.v1`,
`invitation.revoked.v1`, `invitation.claimed.v1`, `expense.created`,
`expense.updated`, `expense.deleted`, and `recurring.schedule.paused`.
`EventConstants` centrally defines the exchange, wildcard subscriptions, and
the auth-email routing key. The production source inventory is now protected by
a static regression guard that enumerates the eleven documented event values;
the complete event-type registry and producer/consumer mapping are not yet
centralized.

This remains an open QA10-E04/D01/D02 acceptance item. Closure requires a
versioned registry that maps every producer event to its schema, routing key,
consumer, compatibility policy, and redaction rule; static validation that every
literal producer/consumer value appears exactly once in that registry; unit
assertions for envelope and payload shape; PostgreSQL/outbox assertions for
atomic audit, sync, and publication state; and RabbitMQ integration evidence
for routing, acknowledgement, retry, deduplication, and dead-letter behavior.
The auth-email path additionally requires proof that encrypted credentials never
appear in logs or parked messages. The source inventory guard and local listener
tests do not satisfy those deployed broker criteria. The BFF member-removal path
now consumes the versioned `member.removed.v1` value emitted by Expense Core;
the focused regression test does not replace RabbitMQ or deployed WebSocket
evidence.

## Missing deployed E2E and environment tests

These cannot be closed by adding more in-process tests:

| ID | Missing E2E/operations proof | Acceptance criteria |
| --- | --- | --- |
| QA10-E2E01 | Complete per-operation authorization matrix | For every REST/GraphQL operation, signed owner/member/removed-member/non-member/workload personas prove allow/deny, object hiding, and no forbidden side effect; evidence names operation, token subject, status, error code, and durable state. |
| QA10-E2E02 | Auth email passwordless journey | Start login, consume the real outbox/broker path, receive Mailpit message, verify once-only code/link, expiry, replay denial, refresh/logout/revocation, and redacted logs using the provider actually enabled in the target profile. |
| QA10-E2E03 | Distributed rate-limit behavior | Run multiple service replicas against shared Redis; concurrent requests from the same and different normalized proxy addresses produce one atomic bucket, fail closed on Redis outage, recover after Redis restart, and retain bounded `Retry-After`. |
| QA10-E2E04 | WebSocket protocol completeness | Real `graphql-transport-ws` tests cover malformed RFC frames, malformed GraphQL payloads, duplicate subscription IDs, heartbeat timeout, sustained backpressure, membership revocation, reconnect/resubscribe cursor recovery, and no duplicate/lost invalidation. |
| QA10-E2E05 | Multi-replica and causal reads | Route requests across replicas, verify reader lag/fallback/recovery, writer watermark propagation, no stale strong reads, no sticky-session dependency, and event fanout consistency. |
| QA10-E2E06 | Capacity and failure domains | Approved production-like load runs record p50/p95/p99, error rate, pool saturation, queue depth, and reconciliation; thresholds are p50 <100ms, p95 <500ms, p99 <1s, error <0.1% for the documented mix. |
| QA10-E2E07 | Failover, restore, rotation, scanning, rollback, alerts | Staging/production-like artifacts prove database/broker failover, PITR restore, JWT/DB/broker secret rotation, image/dependency/secret scans, backward-compatible rollback, alert delivery, RPO <1 minute, RTO <5 minutes, and zero financial data loss. |

## Test implementation order

1. Close `QA10-E01`, `QA10-E02`, and `QA10-A04` first because every public
   assertion depends on correct error and authentication behavior.
2. Add the pure arithmetic, allocation, cursor, policy, and parser tests
   (`QA10-C02`, `QA10-C06`, `QA10-D01`, `QA10-E03`) before changing adapters.
3. Add PostgreSQL/RabbitMQ integration tests for transaction and message
   invariants (`QA10-A01`, `QA10-C01`, `QA10-C03`, `QA10-C05`, `QA10-D02`).
4. Extend operation-matrix evidence and live signed-persona E2E (`QA10-A06`,
   `QA10-B01`, `QA10-E2E01`–`QA10-E2E05`).
5. Execute environment-owned release gates only after the lower layers are
   green (`QA10-E2E06`–`QA10-E2E07`).

## QA-10 completion checklist

QA-10 may move to `done` only when every item below has a recorded command,
artifact, source revision, and reviewer in `docs/tasks/progress.md`:

1. Regenerate all application/library JaCoCo reports from the exact revision
   under review.
2. Run `report_branch_gaps.py --format json --fail-on-gaps`; it exits zero and
   the report contains no unclassified production branch method.
3. Execute the focused U/T/P/M tests for every non-zero QA10-A01..E05 row,
   asserting the row's invariant, failure behavior, and durable side effects.
4. Reconcile all 54 REST/GraphQL operations against the operation matrix and
   attach signed-persona authorization, negative-path, replay, pagination,
   concurrency, and side-effect evidence to QA10-E2E01.
5. Execute QA10-E2E02..E2E05 on the real OIDC/Compose topology, preserving
   broker, Mailpit, Redis, WebSocket, replica, and causal-read artifacts.
6. Execute QA10-E2E06 and QA10-E2E07 in the approved production-like
   environment, preserving threshold, failover, restore, rotation, scanning,
   rollback, alert, RPO/RTO, and reconciliation artifacts.
7. Re-run contract, strict Python typing, security/architecture, diff, and
   repository test gates; record unavailable checks as unavailable rather than
   inferred passes.

The hosted Gradle workflow produces JaCoCo reports in a per-module matrix.
The former artifact-only aggregate inventory job was removed because it did
not enforce a gate or feed another job. Regenerate the repository-wide
inventory locally after restoring the module reports when performing the QA-10
audit; `--fail-on-gaps` remains the eventual blocking closure step. A single
matrix shard is insufficient evidence for a repository-wide no-missed-branch
claim.

## Closure evidence required per increment

Each increment must record exact Gradle/Python/Compose commands, test counts,
artifact paths, source revision, and limitations in `docs/tasks/progress.md`.
The relevant operation-matrix rows and task detail must be updated in the same
increment. A failed or unavailable Docker/hosted run is recorded as unrun or
blocked; it is never converted into a mocked pass. JaCoCo must be regenerated
after tests are added, and every remaining missed class must be classified as
behavioral gap, infrastructure gap, or generated/structural code with a reason.
