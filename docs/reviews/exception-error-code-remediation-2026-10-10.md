# Exception and error-code remediation review

Date: 2026-10-10
Scope: current working tree and the findings recorded in `exception-error-code-compliance-review-2026-10-10.md`
Mode: implementation review; unrelated in-progress work and `graphify-out/` were preserved

## Result

The still-valid findings were implemented with the smallest existing patterns:

| Area | Result | Main implementation points |
|---|---|---|
| Public error rendering | Fixed | `ProblemDetailsFactory`, `GlobalErrorAdvice`, `BffReactiveProblemWriter`, and the existing GraphQL extensions formatter now use catalog `safeDetail`, bounded violations, request IDs, and approved headers. |
| Upstream and domain identity | Fixed | `UpstreamProblemDecoder` is an allowlisted anti-corruption layer; expense, schedule, participant, Redis, and cursor paths preserve their existing catalog identities. |
| Async delivery | Fixed | Notification and auth-email listeners use typed retry/dead-letter outcomes; bounded `DeadLetterRecord` values are published, and pending email delivery is durable and retryable. BFF deduplication is released when fanout fails. |
| Fatal and expected failures | Fixed | Async catches are narrowed and cancellation/fatal failures propagate. Unsupported external OIDC configuration fails with `PLATFORM_CONFIGURATION_INVALID` instead of pretending to issue a token. |
| Hygiene policy | Fixed | The scanner covers boundary-visible generic failures and has three explicitly owned, expiring adapter allowlist entries rather than banning private invariants globally. |
| Catalog lifecycle | Deferred | No published code was deleted or renamed. Lifecycle metadata remains a follow-up because runtime identity corrections should land first. |

## Finding disposition

Findings 1–9 were valid and are addressed by the canonical renderers, typed GraphQL limit signals, upstream decoder, async disposition handling, durable email state, and fail-fast OIDC configuration described above.

Findings 10–16 were valid in the current tree and are addressed as follows:

- generic-throw scanning now checks `UnsupportedOperationException`, response-status failures, custom generic bases, and boundary `require`/`check`/`error` usage;
- expense, schedule, participant, cursor, Redis, and archive/version mappings use the existing catalog identities;
- cursor invalidity and expiry are distinct (`SYNC_CURSOR_INVALID` versus `SYNC_CURSOR_EXPIRED`);
- Redis data-integrity failures are no longer relabeled as store outages;
- recurring work rethrows cancellation/fatal failures and leaves transient infrastructure failures retryable rather than permanently pausing the schedule;
- response and log boundaries use governed identities instead of copying parser, SQL, SMTP, gateway, or user-controlled messages.

Finding 17 remains a documentation/lifecycle follow-up. The runtime does not infer that a catalog entry is dead solely because a source scan finds no reference; published entries remain available for reserved or externally consumed identities.

The wording corrections in the original audit are applied in this review: `require` is only a boundary defect when its failure can escape to a caller, broker, acknowledgement, retry decision, or public log/response. Internal invariant checks remain allowed when the scanner can prove they are outside a trust boundary.

## E2E and acceptance review

The later review comments were verified against the current files and fixed:

1. Offline sync now requires Bob’s generated expense ID in `entityId` or serialized change payload; a merely truthy payload cannot satisfy recovery.
2. The acceptance scenario is named `QA-GRAPHQL-HTTP-RESYNC` and documents that it is HTTP coverage. WebSocket handshake/subscription coverage remains in the dedicated E2E suites.
3. Health probes require HTTP 200 and a JSON body with `status: UP`; 401, 404, and other sub-500 responses fail the probe.
4. Chaos notification matching requires a new notification, `expense.created`, the expected expense/aggregate correlation, and the expected recipient inbox.
5. Unauthorized update and repayment checks capture group state and balances before and after the request and assert `GROUP_ACCESS_HIDDEN`.
6. `--variant provider` now fails when any required wrong-issuer, expired, or invalid-subject fixture token is absent. The broader `all` mode retains its explicit available-fixture behavior.
7. The product-journey run registers failure-safe cleanup and archives its generated group. Durable notification records are not deleted by the test.
8. Repository-owned `RepaymentInput` callers and seed data provide `idempotencyKey`, fixing the failing repayment mutation.

The review comment for `InMemorySettlementStore` was checked and skipped: its replay-conflict path already throws `ExpenseErrors.EXPENSE_IDEMPOTENCY_CONFLICT`, and the settlement service test already asserts the numeric code and error name. No redundant edit was made.

## Validation evidence

Passed locally:

- `./gradlew.bat :app:bff:test :app:expense-core:test :app:accounts:test :libs:errors:test --no-daemon --console=plain`
- `./gradlew.bat :app:notifications:test --no-daemon --console=plain`
- `uv run python -m unittest tests.acceptance.test_runner`
- `uv run python -m unittest tests.tools.test_check_error_hygiene`
- `uv run mypy tests/e2e/test_product_journey.py tests/e2e/test_offline_resilience.py tests/e2e/test_chaos_recovery.py tests/e2e/test_oidc_negative.py`
- `uv run python tools/contracts/validate.py`
- `uv run python tools/qa/check_error_hygiene.py`
- `git diff --check`

The live E2E stack was not running in this workspace: ports 28080, 28081, 28082, 28083, 28025, and 28673 were closed. Therefore this review does not claim live Compose, RabbitMQ, Redis, OIDC, hosted CI, or production evidence. The code-level E2E assertions and Python type checks are the available evidence here.

## Remaining risks and follow-up

- External OIDC token exchange is intentionally unsupported; enabling that provider now fails startup until a real exchange adapter exists.
- Three adapter-specific generic exceptions remain on the documented hygiene allowlist with owner and expiry metadata.
- Catalog lifecycle metadata and generated producer/boundary parity checks should be added after the runtime mapping migration, without deleting published codes.
- A live Compose run is still required before treating the repayment fix, broker disposition, durable email delivery, and WebSocket paths as environment-verified.
