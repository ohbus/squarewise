# Exception and error-code compliance review

What this change does: This is a read-only audit of the current Squarewise working tree's exception paths, error catalog use, transport boundaries, and asynchronous failure handling. It covers production Kotlin under `app/` and `libs/`, the error-code contracts and ERRC task records, connected callers and tests, and the repository relationship map produced by Graphify. The tree already contains unrelated in-progress edits, so the findings describe the current filesystem and do not infer a clean baseline.

Date: 2026-10-10
Scope: current working tree, including uncommitted files
Review mode: read-only; no application or existing documentation file was changed by the audit

## Scope and evidence

The audit followed the exception from construction to its boundary:

- servlet REST advice and security handlers;
- WebFlux BFF REST and GraphQL paths;
- RabbitMQ listeners, dead-letter routing, outbox publication, and schedulers;
- domain, persistence, crypto, configuration, and transport adapters;
- callers and tests of every non-governed exception found;
- the six-digit catalog, error-domain registry, error contracts, ERRC task details, and progress ledger.

Graphify was queried for exception-to-boundary relationships. The source scan then verified the returned paths directly because Graphify edges are navigation evidence, not runtime proof.

Current measurements:

| Check | Result | Meaning |
|---|---:|---|
| Compiled Kotlin catalog definitions | 99 | Static catalog is broad and reflection-free. |
| YAML catalog records | 99 | The YAML and Kotlin names/codes have equal counts and no static name/code difference. |
| Deliberate `NullPointerException` throws | 0 | No explicit NPE throw was found. Kotlin null failures remain possible if generic boundary handling is bypassed. |
| Direct generic-family throws | 3 | `IllegalArgumentException`, `IllegalStateException`, and `UnsupportedOperationException`; `ResponseStatusException` is also emitted through `Mono.error`. |
| Production `require`/`check`/`error` expressions | 203 | Kotlin converts most of these to generic `IllegalArgumentException`; only programmer-only invariants should remain at this layer. |
| Legacy `ApplicationException`/`ERR_XX` production references | 0 | The old runtime vocabulary is gone from current `app/` and `libs/` production Kotlin. |

## Must fix

### 1. The active REST handler still copies throwable text into public responses (`libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/http/GlobalErrorHandler.kt:76-174`)

- **What this is:** All three servlet applications import `GlobalErrorHandler`; the safer `GlobalErrorAdvice` exists but is not imported by the applications.
- **Problem:** Malformed-body, binding, type-mismatch, optimistic-lock, `IllegalArgumentException`, and `SquarewiseException` paths return `Throwable.message`, root-cause text, or explicit message overrides. A malformed JSON payload or a database exception can therefore put parser, SQL, resource, or user-controlled text in RFC 9457 `detail`, directly contradicting `SquarewiseException.kt:13-16` and the error-handling guide.
- **Fix:** Make the active handler use static `ErrorDefinition.safeDetail` and bounded structured violations only. Adopt one handler implementation and delete or retire the duplicate; add adversarial tests that assert no cause, SQL, parser text, exception class, or secret appears in REST responses.
- **If we skip it:** A client can receive internal implementation details, and every cataloged exception carrying a message override remains a leakage path.

### 2. Specific catalog identities are available but runtime throws use misleading definitions (`app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:224-241`)

- **What this is:** Several domain failures are translated to `GROUP_NOT_FOUND` or `GROUP_NAME_CONFLICT` even when the failing resource is an expense, schedule, participant set, archive state, or version.
- **Problem:** Missing or deleted expenses at lines 234 and 237 use `GROUP_NOT_FOUND`; stale expense versions at line 241 use `GROUP_NAME_CONFLICT`; recurring schedule failures use `GROUP_NOT_FOUND`; settlement participant validation also uses `GROUP_NOT_FOUND`. The catalog already contains `EXPENSE_NOT_FOUND`, `EXPENSE_VERSION_CONFLICT`, `SCHEDULE_NOT_FOUND`, `SCHEDULE_VERSION_CONFLICT`, `GROUP_ARCHIVED`, and `PARTICIPANT_SET_INVALID`, but the current runtime does not use those identities consistently.
- **Fix:** Map each throw to the existing semantic definition and preserve authorization-hiding rules separately from resource type. Add one contract test per mapping so a missing expense cannot regress to a missing-group code.
- **If we skip it:** Clients, metrics, runbooks, and retry logic act on the wrong error; the six-digit catalog becomes decorative rather than a stable machine contract.

### 3. BFF REST errors bypass the governed reactive boundary (`app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/auth/BrowserSessionController.kt:54`, `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/transport/UpstreamServiceException.kt:4-7`)

- **What this is:** Browser-session controllers return raw `ResponseStatusException`, while gateway failures are `RuntimeException` values handled only by the GraphQL data-fetcher resolver.
- **Problem:** A missing refresh cookie, an upstream login failure, or an Accounts 503 on `/auth/*` is outside `GraphQlExceptionResolver`; WebFlux can therefore emit its default error body and the free-text message `Browser session required` or `Accounts returned HTTP ...` instead of the catalog contract.
- **Fix:** Add one reactive REST error boundary that maps `ResponseStatusException`, `UpstreamServiceException`, timeout, and unexpected non-fatal failures to `BffDomainException`/`PlatformErrors` with safe detail. Replace the direct `ResponseStatusException` with the same governed path.
- **If we skip it:** Browser REST clients receive a different error protocol from GraphQL clients and may see framework or gateway implementation text.

### 4. The BFF's upstream identity-preservation path is dead code (`app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/transport/AccountsGateway.kt:28-33`, `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/errors/UpstreamProblemDecoder.kt:10-27`)

- **What this is:** `BffGraphQLErrorResolver` preserves upstream `numericCode`, `errorName`, request ID, source, and detail only for `UpstreamProblemException`, but every gateway `onStatus` creates status-only `UpstreamServiceException` instead.
- **Problem:** An upstream `GROUP_NOT_FOUND` response is reduced to a status and later reconstructed as a generic BFF error. `UpstreamProblemDecoder` is referenced by tests but not by a production gateway, so ERRC-22's identity-preservation claim is not true on the live call path.
- **Fix:** Centralize gateway error decoding, read the bounded upstream Problem Details body, validate promoted identity fields, and fall back to `UPSTREAM_PROTOCOL_INVALID` with static safe detail when the body is malformed. Keep the existing status-only mapping only for non-problem or legacy upstream responses.
- **If we skip it:** Consumers lose stable domain codes at the BFF boundary and cannot distinguish not-found, conflict, validation, and upstream protocol failures.

### 5. BFF rate-limit and GraphQL-limit failures are hand-built and message-classified (`app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/config/GraphQlRateLimitWebFilter.kt:55-62`, `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/graphql/GraphQlLimitErrorInstrumentation.kt:21-45`)

- **What this is:** The WebFlux filter writes `{"code":"RATE_LIMITED"...}` directly, and the GraphQL instrumentation decides between validation and rate limiting by searching English error messages.
- **Problem:** The filter response has no six-digit `numericCode`, `errorName`, safe `detail`, or standard problem fields. A Spring message change can also turn a query-depth failure into the wrong catalog category; the instrumentation generates a new random request ID instead of retaining the request context.
- **Fix:** Render `PlatformErrors.SECURITY_RATE_LIMITED` through the shared reactive problem writer, and use a typed GraphQL limit signal or known instrumentation error classification rather than message text. Populate the same extensions formatter used by `BffGraphQLErrorResolver`.
- **If we skip it:** Rate-limit responses are contract-incompatible and GraphQL clients receive unstable, misleading classifications.

### 6. Async dead-letter records are built and then discarded (`libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/async/AsyncExecutionTemplate.kt:20-29`, `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/consumer/transport/RabbitNotificationListener.kt:58-73`)

- **What this is:** `AsyncExecutionTemplate` creates a safe `DeadLetterRecord`, but both notification listeners inspect only `result.disposition`; they never publish or attach `result.deadLetter`.
- **Problem:** Invalid envelopes are also parsed outside the template and immediately rejected at lines 71-73 or 54-58 of the auth-email listener, so no metrics or structured catalog identity is recorded. RabbitMQ may route the original message to a DLX, but the repository's declared `dead-letter.schema.json` record is not emitted.
- **Fix:** Put parsing and processing under one governed async adapter, publish the structured record to the configured dead-letter route or attach its bounded fields as broker headers, and use `MESSAGE_ENVELOPE_INVALID`/`NOTIFICATION_PAYLOAD_CORRUPT` for poison input. Test the actual listener-to-DLX message, not only the in-memory template result.
- **If we skip it:** Poison messages are hard to diagnose or replay, and the promised error code, attempt count, and safe reason disappear at the real broker boundary.

### 7. The BFF event listener loses transient events and can permanently deduplicate failed work (`app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/messaging/transport/RabbitBffEventListener.kt:27-40`, `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/messaging/service/BffEventConsumer.kt:30-44`)

- **What this is:** The listener catches every non-fatal `Exception`, logs its raw message, rejects with `requeue=false`, and has no `AsyncExecutionTemplate`; the consumer marks an event seen before fanout succeeds.
- **Problem:** A temporary Redis, database, or subscription-fanout failure is treated as a poison message and lost. Even if the broker redelivers, the in-memory deduplicator already marked the event, so the next delivery returns `DuplicateConsumptionResult` without emitting the update.
- **Fix:** Use the shared retry/dead-letter disposition path and mark the event complete only after fanout succeeds, or track an explicit in-flight/failed state. Log only event ID, catalog identity, and bounded operational fields.
- **If we skip it:** Live-update events disappear during ordinary dependency outages, and reconnecting clients can remain stale without a replay signal.

### 8. Email delivery outcomes are acknowledged as success (`app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/consumer/service/NotificationEventConsumer.kt:64-72`, `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/email/delivery/AuthEmailRabbitListener.kt:39-53`)

- **What this is:** `EmailDispatcher` returns `RETRYABLE_FAILURE` or `PERMANENT_FAILURE` after catching SMTP exceptions, but its caller logs the outcome and still returns an applied consumer result; the auth-email listener treats any normal return from `consumer.consume` as `AsyncExecutionResult.Completed`.
- **Problem:** SMTP outage or retry exhaustion does not throw, so RabbitMQ acknowledges the message. The business inbox or processed-event row can be committed while the email is never retried or parked with a clear terminal code.
- **Fix:** Make the delivery outcome part of the async disposition: requeue transient outcomes, dead-letter permanent ones, and acknowledge only after the chosen terminal state is recorded. Use `EMAIL_DISPATCH_FAILED` for transport failure and `EMAIL_TEMPLATE_INPUT_INVALID` for invalid broker data.
- **If we skip it:** Authentication and notification emails can be silently lost while the system reports successful processing.

### 9. `runCatching` swallows fatal errors and business failures on login and notification paths (`app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/login/LoginStartService.kt:51-65`, `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/consumer/service/NotificationEventConsumer.kt:60-62`)

- **What this is:** Kotlin's `runCatching` catches `Throwable`, not only ordinary `Exception` values.
- **Problem:** Login credential issuance and email sending are converted to an accepted login result even when they fail; recipient resolution suppresses all failures, including fatal JVM/cancellation errors. This violates the repository's fatal-error policy and makes operational failure invisible.
- **Fix:** Catch only expected exception types, explicitly rethrow `FatalErrorClassifier` failures, and return a typed anti-enumeration outcome that still records delivery/credential failure. Apply the same rule to parsing helpers that currently catch all throwables.
- **If we skip it:** The process can continue after fatal conditions, and users can receive an accepted response for work that never happened.

## Should fix

### 10. The generic-throw gate is incomplete and currently fails on the working tree (`tools/qa/check_error_hygiene.py:15-20`, `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/domain/AllocationCalculator.kt:82`)

- **What this is:** The scanner catches only four generic constructor spellings and the current production scan finds one unallowlisted `throw IllegalArgumentException`; it does not cover `require`, `check`, `error`, `UnsupportedOperationException`, `ResponseStatusException`, or custom `RuntimeException`/`IllegalArgumentException` subclasses.
- **Problem:** The repository claims ERRC-13 is complete and its allowlist is empty, but the scanner exits 1 on the allocation parser and its unit test exits 1 because it still expects 23 allowlist entries. A green historical progress entry cannot be reproduced on this tree.
- **Fix:** First make the gate and its test share one current baseline. Then scan the actual policy surface: deliberate generic throws, boundary-visible `require`/`check`, direct response exceptions, and ungoverned custom bases; retain narrowly documented startup/internal invariant exceptions only when they cannot escape.
- **If we skip it:** New generic exceptions can pass review unnoticed, while CI remains either red or falsely documented as green.

### 11. Boundary-visible `require` and custom generic exceptions remain outside the catalog (`app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/sync/persistence/JpaSynchronizationStore.kt:35-80`, `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/email/delivery/AuthEmailDeliveryConsumer.kt:20-21`)

- **What this is:** The production scan found 203 `require`/`check`/`error` expressions. The internal wrappers `AccountsInputException`, `NotificationInputException`, `ExpenseInputException`, and `BffInputException` still inherit from `IllegalArgumentException`, while `InvalidSyncCursorException`, `InvalidEnvelopeException`, `MailException`, and `UpstreamServiceException` inherit from generic runtime types.
- **Problem:** Some calls are valid constructor or startup invariants, but request, broker, and persistence paths can let the same JVM types escape. For example, a sync-store limit or blank identifier can bypass the controller mapper, and auth-email template/expiry checks are classified only as generic permanent failures.
- **Fix:** Keep `require` only for programmer-only invariants; translate trust-boundary failures into existing catalog definitions or typed outcomes. Use one shared governed base for cross-service infrastructure and keep low-level types private when a caller genuinely needs to distinguish them.
- **If we skip it:** A later caller can accidentally expose an `IllegalArgumentException`, map a domain failure as validation, or route a broker bug as a permanent poison message.

### 12. Sync cursor failures use one generic exception and the wrong catalog family (`app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/sync/domain/InvalidSyncCursorException.kt:4`, `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/sync/api/SyncController.kt:38-41`)

- **What this is:** Malformed, group-mismatched, and expired cursors all become `InvalidSyncCursorException`; the controller then wraps the message as `EXPENSE_REQUEST_INVALID`.
- **Problem:** The catalog already distinguishes `SYNC_CURSOR_INVALID` (`261101`) from `SYNC_CURSOR_EXPIRED` (`261102`, HTTP 410), but both runtime cases lose that distinction and publish a free-form message override.
- **Fix:** Return or throw a typed cursor result that distinguishes parse/mismatch from expiry, then map directly to the two existing definitions with safe detail.
- **If we skip it:** Clients cannot know whether to repair the cursor or perform a full resync, and the documented 410 behavior is not reachable.

### 13. Redis rate-limit integrity failures are relabeled as store outages (`libs/security/src/main/kotlin/com/subhrodip/squarewise/security/ratelimit/RedisRateLimiter.kt:45-67`)

- **What this is:** The method deliberately throws `PlatformErrors.DATABASE_DATA_INCONSISTENT` for a missing or malformed Redis decision, then catches it in the following broad `catch (Exception)` and wraps it as `RateLimitStoreUnavailableException`.
- **Problem:** A corrupt response is reported to callers as a dependency outage, so Accounts/BFF can emit an unavailable or fail-closed rate-limit response instead of preserving the integrity failure.
- **Fix:** Re-throw `SquarewiseException` before the vendor catch, and catch only Redis/timeout exceptions that actually mean the store is unavailable.
- **If we skip it:** Alerts, retry behavior, and incident diagnosis point at Redis availability when the real defect is protocol or data corruption.

### 14. Scheduler failure handling pauses work for every exception (`app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/service/RecurringExpenseService.kt:185-203`)

- **What this is:** One broad `catch (Exception)` marks the schedule paused, saves it, emits a notification, and stops the batch.
- **Problem:** A transient database or broker failure has the same result as a permanent data defect; interruption/cancellation can also be converted into a pause. The ERRC-08 contract requires explicit completed, retryable, and terminal outcomes, but this worker has only “pause and break”.
- **Fix:** Catch and classify expected domain/data failures, retry or leave the occurrence pending for transient infrastructure failures, and rethrow interruption/cancellation/fatal conditions. Record the existing `OCCURRENCE_GENERATION_FAILED` definition when a terminal record is actually created.
- **If we skip it:** A short outage can permanently stop recurring expenses until a human notices and resumes them.

### 15. Internal exception messages contain recipient, SMTP, parser, and broker text (`app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/email/smtp/JavaMailSender.kt:40-44,85-88`, `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/consumer/transport/BrokerEnvelopeParser.kt:23-46`, `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/messaging/broker/RabbitBrokerPublisher.kt:60-65`)

- **What this is:** Several adapters build exception messages from SMTP response lines, recipient arrays, parser input, and vendor exception text.
- **Problem:** These values are not public in the normal email path, but broad listeners and logs can later serialize or print them. The messaging contract explicitly forbids provider text, recipient addresses, payloads, and unbounded exception messages in failure records and logs.
- **Fix:** Keep causes for restricted diagnostics, but use static exception text and structured opaque IDs for logs and outcomes. Never concatenate `e.message`, recipient arrays, raw timestamps, or broker response text into a reusable exception.
- **If we skip it:** A future log statement or dead-letter adapter can turn a private failure into PII or provider-detail leakage.

### 16. The external OIDC provider is a production bean whose first token request always throws (`app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/config/AuthSessionConfiguration.kt:56-79`, `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/provider/ExternalOidcTokenProvider.kt:36-38`)

- **What this is:** When `squarewise.security.oidc.external-provider.enabled=true` in production, staging, or local-oidc, the provider replaces the internal signer but `issueAccessToken` is an `UnsupportedOperationException` stub.
- **Problem:** The configuration starts successfully and fails only when login verification or refresh reaches token issuance. The test suite intentionally asserts this unsupported behavior, so the missing runtime capability is documented as a passing test.
- **Fix:** Either implement the configured token exchange and give failures a cataloged upstream/provider definition, or fail startup and refuse the bean while the feature is unsupported. Do not advertise an enabled provider that fails at the user operation.
- **If we skip it:** A deployment can pass health checks and then return a generic 500 for every new login or refresh.

## Nice to have

### 17. Unused catalog definitions should be separated from the active runtime contract (`libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/catalog/*.kt`)

- **What this is:** Static parity is complete, but a source-reference scan found definitions that have no production throw/use outside their catalog declaration.
- **Problem:** Unused entries make it hard to tell whether a failure family is implemented, merely reserved, or accidentally mapped to a neighboring code. Examples include `MESSAGE_ENVELOPE_INVALID`, `MESSAGE_PUBLISH_FAILED`, `BROKER_UNAVAILABLE`, `EMAIL_TEMPLATE_INPUT_INVALID`, `NOTIFICATION_PAYLOAD_CORRUPT`, `EXPENSE_NOT_FOUND`, `SCHEDULE_NOT_FOUND`, `SYNC_CURSOR_INVALID`, `SYNC_CURSOR_EXPIRED`, `SEARCH_QUERY_INVALID`, and `LIVE_UPDATE_EVENT_INVALID`.
- **Fix:** Mark each entry explicitly as active, reserved, or retired in the catalog lifecycle metadata; do not delete published identities. Add source-parity checks for active entries and use the existing definitions in the boundary fixes above.
- **If we skip it:** Future work will keep inventing generic or neighboring codes because the intended existing code is difficult to discover.

## Complete exception inventory

### Compliant or mostly compliant paths

| Family | Current locations | Review result |
|---|---|---|
| `AccountsDomainException`, `ExpenseDomainException`, `NotificationDomainException`, `BffDomainException` | Each application `errors/` package | Correct static catalog dependency. Message overrides still pass through the active unsafe handler. |
| `PlatformDomainException`, `DbPlatformException`, `ObservabilityPlatformException` | `libs/security`, `libs/db`, `libs/observability` | Correct governed base and catalog identity. Catchers must not relabel these as vendor outages. |
| `FatalErrorClassifier` and `AsyncExecutionTemplate` | `libs/errors/.../exceptions` and `.../async` | Correctly rethrows JVM fatal, interruption, and cancellation conditions when the template is actually used. |
| Servlet/reactive security handlers | `libs/security/.../errors` | Use static authentication/access definitions and bounded problem bodies. The BFF controller paths still sit outside this filter boundary. |
| `ArithmeticException` translation | `FinancialArithmetic.kt` | Narrow arithmetic catch and typed `ExpenseInputException`; the public caller still needs safe catalog translation. |
| JWT decoder and duplicate-constraint catches | `FallbackJwtDecoder.kt`, `JpaEventDeduplicator.kt` | Narrow domain/vendor catches with a real recovery decision. |

### Generic or ungoverned production families

| Family | Sites / callers | Status |
|---|---|---|
| Direct `IllegalArgumentException` | `AllocationCalculator.kt:82`; caught by allocation/expense controllers and rewrapped with raw message | Scanner failure and public-message risk. |
| Direct `IllegalStateException` | `HmacCredentialDigest.kt:19-20` | Infrastructure failure is not a cataloged startup/crypto definition. |
| Direct `UnsupportedOperationException` | `ExternalOidcTokenProvider.kt:36-38` | Reachable feature stub; see finding 16. |
| `ResponseStatusException` | `BrowserSessionController.kt:54` | Reactive REST boundary bypass; see finding 3. |
| `require`/`check`/`error` | 203 expressions across configuration, domain, persistence, BFF, messaging, and notification code | Acceptable only for private invariants. Boundary-visible sites need typed translation; the generic scanner does not currently enforce this. |
| `AccountsInputException` | Email normalization, credential envelope, account identity persistence, startup config | Typed name but still an `IllegalArgumentException`; account identity absence is especially misleading as “input”. |
| `NotificationInputException` | Auth-email envelope parsing and configuration | Typed name but still an `IllegalArgumentException`; async classification is supplied externally and no public catalog identity travels with it. |
| `ExpenseInputException` | Financial arithmetic overflow | Useful low-level type, but it must be translated to a stable expense definition before every public boundary. |
| `BffInputException` | Browser origin configuration | Startup/configuration use is reasonable; it should use the platform configuration definition if it can escape bean creation. |
| `InvalidSyncCursorException` | `SyncCursor`, `JpaSynchronizationStore`, `SyncController` | Generic runtime type and wrong catalog mapping; see finding 12. |
| `InvalidEnvelopeException` | Notification broker parser and auth-email parser | Useful poison-message marker, but it bypasses the async toolkit and carries raw parser text; see finding 6. |
| `MailException`/`MailSendException` | SMTP adapter and `EmailDispatcher` | Useful transport classification, but message construction and outcome acknowledgement are unsafe; see findings 8 and 15. |
| `RateLimitStoreUnavailableException` | Redis adapter, Accounts services, BFF rate-limit filter | Correct low-level distinction, but `RedisRateLimiter` catches too broadly and the BFF response is hand-built; see findings 5 and 13. |
| `UpstreamServiceException`/`UpstreamProblemException` | BFF gateways and GraphQL resolver | Internal transport types are reasonable, but REST callers lack a mapper and gateways never use the identity-preserving decoder; see findings 3 and 4. |

### Catch and recovery inventory

The meaningful catch groups were checked individually:

- `catch (Exception)` in `AsyncExecutionTemplate` is acceptable only because it immediately applies `FatalErrorClassifier`; its result must still be consumed by listeners.
- `catch (Exception)` in the BFF Rabbit listener, recurring scheduler, SMTP adapter, broker publisher, and outbox publishers needs the narrower disposition or safe logging described above.
- `runCatching` in login, notification delivery, cursor parsing, auth-email parsing, crypto decoding, watermark parsing, and network-address parsing catches `Throwable`; parsing-only uses should catch expected runtime parse errors, and side-effecting uses must preserve fatal/cancellation behavior.
- `catch (RuntimeException)` in `SyncCursor` and `NotificationEventConsumer` is acceptable only where the complete runtime exception set is intentional; it should not become a substitute for typed outcomes at a trust boundary.
- `catch (InterruptedException)` in `EmailDispatcher` correctly restores the interrupt flag. That pattern should be reused wherever scheduled or retry work can block.

## Error-code status against repository documentation

### Implemented and evidenced

- `contracts/errors/error-catalog.yaml` validates with 99 unique six-digit records.
- The compiled Kotlin catalog contains 99 definitions and matches the YAML names/codes by static comparison.
- `uv run --frozen --no-build python tools/errors/validate_catalog.py` passed.
- `uv run --frozen --no-build python tools/contracts/validate.py` passed.
- Current production Kotlin contains no legacy `ApplicationException` or `ERR_XX` references.
- Static, catalog-backed exception construction and security filter problem bodies are present.

### Present in design but not fully used by the current runtime

- Safe `GlobalErrorAdvice` is present but the applications import the older unsafe `GlobalErrorHandler`.
- GraphQL upstream identity preservation is implemented in a decoder/resolver pair but not wired into gateway `onStatus` paths.
- Async retry/dead-letter value types and fatal tests exist, but listener adoption is incomplete and the dead-letter value is discarded.
- Many precise catalog identities are declared but runtime paths use neighboring generic identities or no identity at all.
- ERRC-13's scanner and test disagree in the current tree: the scanner finds `AllocationCalculator.kt:82`, while the test expects a 23-entry allowlist and the file contains zero entries.
- Registry status says ERRC-13 and ERRC-15 through ERRC-23 are done, but the current source still exhibits the specific runtime gaps above. Those statuses should not be used as proof of current production compliance without rerunning the boundary tests.

## Validation run during this review

| Command | Result |
|---|---|
| `uv run --frozen --no-build python tools/errors/validate_catalog.py` | Passed; 99 unique codes. |
| `uv run --frozen --no-build python tools/contracts/validate.py` | Passed; catalog, schemas, REST, events, GraphQL, and registry validation completed. |
| `uv run --frozen --no-build python tools/qa/check_error_hygiene.py` | Failed; unexpected `generic_throw` at `app/expense-core/.../AllocationCalculator.kt:82`. |
| `uv run --frozen --no-build python -m unittest tests/tools/test_check_error_hygiene.py` | Failed; test expects 23 allowlist entries, actual allowlist has 0. |
| `git diff --check` | No whitespace errors; Git emitted only existing LF-to-CRLF warnings for dirty test files. |

## Review boundary

No application source, existing task tracker, existing review, or in-progress file was changed. The new review document is the only intended deliverable from this audit; the unrelated dirty paths and generated `graphify-out/` remain untouched and are not part of the review commit.

Verdict: fix 1, 2, 3, 4, 5, 6, 7, 8, and 9 before treating the ERRC runtime migration as complete. The catalog validators passing is useful contract evidence, but it does not close the active boundary, retry, semantic mapping, or leakage findings.

Lean: -25 lines possible by deleting the duplicate inactive handler and the unused identity-decoder/test-only path after one governed boundary replaces them.

Not checked: full Gradle tests, hosted CI, live RabbitMQ/Redis behavior, production OIDC token exchange, and production-like load were not rerun because the working tree contains unrelated in-progress changes.
