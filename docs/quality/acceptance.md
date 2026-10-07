# Acceptance and evidence plan

Owner: DOC-07. This document defines acceptance; execution evidence belongs in
the task progress ledger. The scaffold and several product slices are verified,
but full product acceptance belongs to QA-04 and operational launch evidence to
OPS-10. No passing scaffold check implies a launch-ready product.

## Gates

| Gate | Required evidence |
| --- | --- |
| Documentation / DOC-08 | Valid schemas/examples, GraphQL operation mapping, financial fixtures, task DAG and links, reviewed architecture and JPA decision |
| Scaffold / FND-01–03 | Actual stable dependency resolution, compile/test/package reports, independent app artifacts, module boundaries, contract checks, reserved UI directory |
| Product / QA-01 | Implemented behavior verified against real PostgreSQL/RabbitMQ, authenticated API tests, failures and concurrency tested |
| Public launch / OPS-02 | Measured capacity/cost, restoration and rollout rehearsal, hosting/budget/market retention decisions, monitored availability |

A scaffold can pass without implementing a single product feature. Record missing
checks as not run, including the reason; never count a specification as evidence.

## Financial scenarios

| ID | Scenario and acceptance |
| --- | --- |
| FIN-01 | Split 100 minor units equally three ways: deterministic allocations 34/33/33 by participant-ID tie-break, sum exactly 100 |
| FIN-02 | Exact amounts sum to total; percentages total 10,000 basis points; positive weights use largest remainder. Reject malformed, negative or overflow inputs |
| FIN-03 | Multiple payer contributions sum to total. Participant paid-minus-owed nets sum to zero for every group/currency |
| FIN-04 | Randomized splits preserve sums and deterministic rounding, including currencies with zero/three decimal places and near-limit amounts |
| FIN-05 | Expense edit reverses old postings and applies new ones; deletion reverses once; ledger/audit retain original attribution |
| FIN-06 | Fault after each persistence step rolls back expense, postings, balance, audit, sync revision and outbox together |
| FIN-07 | Repayment by an involved registered active member updates both balances immediately. Reversal is audited and cannot apply twice. No actual funds are moved |
| FIN-08 | Different currencies never net together. Suggested transfers preserve balances without changing the ledger or claiming globally minimal transfers |
| FIN-09 | Independent reconciliation recomputes balances from postings and detects intentionally introduced corruption in an isolated test database |

## Collaboration, offline, and scheduling

| ID | Scenario and acceptance |
| --- | --- |
| COL-01 | Two edits with one expected version: exactly one commits, the other returns an actionable conflict; all active members may edit |
| COL-02 | Concurrent claim of one placeholder permits one linked account and preserves historical participant IDs |
| COL-03 | Removed participants remain visible in historical financial records but cannot submit new writes or read restricted group data |
| OFF-01 | Create offline, lose response after commit, then retry: one expense and one financial effect; conflicting reuse of the key fails |
| OFF-02 | Retry a deleted expense's creation key: do not resurrect it; return contract-defined original/current outcome |
| OFF-03 | Paginated snapshot while expenses change remains internally consistent; applying later changes reaches the authoritative state |
| OFF-04 | Reverse commit timing across concurrent writes: no committed change disappears behind a cursor. Include deletion tombstones |
| OFF-05 | Expired snapshot/cursor requires resynchronization while client queued creations survive. Permission is checked again on replay |
| OFF-06 | Backend harness covers sync semantics now; actual cached UI reads, connectivity indicators and browser persistence await UI work |
| REC-01 | Concurrent workers and a crash after commit generate a scheduled occurrence once |
| REC-02 | Weekly/monthly dates, leap years, month-end clamping, timezone transitions and pauses follow the published schedule policy |
| REC-03 | Invalid membership pauses generation with a visible owner notification; do not silently change bill allocations |
| REC-04 | Service downtime catches up eligible occurrences without duplicate dates, with bounded batches and observable backlog |

## Delivery and realtime

- MSG-01: crash before publication, after broker acceptance, and before published
  marking. Every committed event eventually processes; duplicate deliveries have
  one consumer-local effect.
- MSG-02: unavailable broker does not roll back expenses; leases recover after a
  worker dies. Validate publisher confirms, unroutable returns and queue policies.
- MSG-03: transient failure follows bounded retries; malformed/unsupported events
  park with diagnostic metadata. Replaying parked events preserves original IDs.
- MSG-04: unavailable email provider preserves inbox and delivery work. Ambiguous
  provider timeout may duplicate email; do not promise exactly-once external mail.
- RT-01: clients on two BFF replicas both receive relevant changes. Temporary
  queue loss/reconnect triggers resync, not reliance on message replay.
- RT-02: membership removal prevents future group data access, including through
  existing subscriptions; expired credentials close or require reauthentication.
- RT-03: slow subscribers have bounded buffers; disconnect with recoverable
  resync semantics instead of unbounded server memory.

## Security and export

Reject forged, expired, wrong-issuer and wrong-audience tokens at each service.
Test cross-group reads/writes, guessed participant IDs, unauthorized repayments,
invite reuse/revocation/expiry, and simultaneous invitation acceptance. Never
authorize a request using a caller-supplied user-ID header alone.

Test bounded page sizes, search inputs, oversized requests, GraphQL depth/cost,
alias amplification, batch sizes and WebSocket connection limits. CSV exports
must enforce group authorization and escape spreadsheet formula prefixes. Rate
limit invitation/email actions and deduplicate reminders. A free product needs
abuse limits, not a billing service.

## Future UI acceptance (deferred)

Run separate usability sessions with roommates, couples and travelers. A trained
participant should enter an ordinary equal-split expense in approximately 15
seconds; measure task completion/error rates, not timing alone. Display confirmed
versus pending offline records, allocation previews, currency-separated balances,
auditable changes and recorded-versus-transferred repayment language. A stale
edit must offer recovery without silently overwriting another person's work.

Require keyboard operation, accessible labels, focus/error announcements,
readable contrast, and no color-only debt status. Validate empty/loading/error
states, small screens, international currency/date presentation, long names and
reconnect flows. UI framework selection remains deferred.

## Evidence format

For each executed suite record task ID, commit, toolchain/dependency versions,
command, exit code, reports, fixture/seed, duration and unresolved defects. For
failure/recovery tests also retain an ordered timeline and before/after ledger
counts. Reviewers check outcomes against contracts, not only green test counts.

## ERRC-24 additive error acceptance matrix

Every live REST error assertion must retain the v1 symbolic `code` and verify
the additive `numericCode`, `errorName`, and UUID `requestId` fields. The Bruno
collection owns repeatable request-level contract checks; the live E2E suites
own stateful and fault-injection scenarios.

| Status | Scenario | Primary evidence |
| --- | --- | --- |
| 400 | Invalid profile/allocation/request parameters | `tests/e2e/test_rest_edge_cases.py`; Bruno negative probes |
| 401 | Missing, malformed, expired, wrong-issuer, wrong-audience, and replayed credentials | `tools/bruno/quality/`; `tests/e2e/test_auth_email_delivery.py` |
| 403 | Authenticated cross-scope access where the contract exposes authorization failure | service-specific live authorization suites; retain anti-enumeration 404 where required |
| 404 | Missing and archived groups/resources, including hidden non-member resources | `tools/bruno/accounts/get-profile-by-id.bru`; `tools/bruno/quality/error-attribution.bru`; `tests/e2e/test_rest_edge_cases.py` |
| 409 | Tampered idempotency replay and stale/conflicting mutations | `tests/e2e/test_rest_edge_cases.py`; `tests/e2e/test_concurrency_subscriptions.py` |
| 429 | Login verification and Redis/rate-limit admission thresholds | `tests/e2e/test_auth_email_delivery.py`; `tests/e2e/test_auth_cache_resilience.py`; `tests/e2e/test_auth_rate_limit_surfaces.py` |

The legacy-client compatibility probe projects an additive Problem Details
response down to the v1 fields and verifies that a consumer ignoring the new
fields still deserializes `code`, `status`, and `detail` successfully. Live
execution evidence must be recorded in `docs/tasks/progress.md`; source-level
coverage or a successful local contract scan does not close the live-stack gate.
