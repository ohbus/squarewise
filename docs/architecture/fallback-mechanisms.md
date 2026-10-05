# Fallback mechanism audit

This document records fallback, retry, defaulting, suppression, and degradation
behavior found in the application. It is an audit of current behavior, not an
approval that every fallback is appropriate.

## Classification

| Classification | Meaning |
|---|---|
| Fail-closed | The operation stops or returns an error when a required dependency or security decision is unavailable. |
| Bounded retry | A transient operation is retried a finite number of times, then remains observable as failed or is dead-lettered. |
| Domain default | Missing business data is initialized according to an explicit product rule. |
| Compatibility default | Missing protocol or execution context uses a conservative technical value. |
| Silent suppression | Work is skipped or an error is hidden while the surrounding operation succeeds. This requires special review. |
| Test/local-only | Behavior exists only for tests or local development and must not be packaged as a production adapter. |

## Security and authentication

| Location | Behavior | Reason | Assessment |
|---|---|---|---|
| `app/accounts/.../LoginStartService.kt` | Login start returns the same accepted response when the credential is not issued or email delivery fails | Prevents account/email enumeration | Enumeration protection is intentional; delivery failure is silently suppressed and should be observable/retryable without changing the external response |
| `app/accounts/.../AuthController.kt` | Blank/unusable client address maps to `default-partition` | Ensures an abuse-control partition always exists | Compatibility default; review concentration risk |
| `app/accounts/.../LoginCredentialService.kt` | Credential lifetime and maximum attempts have policy constants | Defines passwordless credential policy | Domain policy, not dependency fallback |
| `app/accounts/.../TokenSessionService.kt` | Refresh-token lifetime has a policy constant | Defines session lifetime | Domain policy, not dependency fallback |
| `app/accounts/.../TestIdentityProviderConfiguration.kt` | Test-only HMAC token provider | Allows isolated authentication tests | Test/local-only; absent from production source |
| `app/accounts/.../AuthSessionConfiguration.kt` | Deployed profiles require external OIDC/Keycloak | Production token authority is external | Fail-closed dependency wiring |

## Rate limiting and databases

| Location | Behavior | Reason | Assessment |
|---|---|---|---|
| `libs/security/.../RedisRateLimiter.kt` | Redis failure propagates as rate-limit service unavailability | Prevents accepting requests when the limiter cannot decide | Fail-closed; required |
| `app/notifications/.../RedisDeliveryRateLimiter` | Shared Redis failure suppresses delivery | Prevents delivery-rate bypass across replicas | Fail-closed; required |
| `libs/db/.../DbRoutingDataSource.kt` | Reader failure is not redirected to the writer | Prevents hidden consistency/topology changes | Fail-closed; required |
| `libs/db/.../DbReaderHealth.kt` | Lagging, disconnected, or open readers return failure | Prevents stale or unsafe reads | Fail-closed; required |
| `libs/db/.../DbContextHolder.kt` | Missing context uses an unclassified command/writer context | Conservative route for undeclared operations | Compatibility fail-safe; retain telemetry |
| `libs/db/.../DbAutoConfiguration.kt` | Metrics registry is optional | Allows operation without Micrometer | Observability fallback; business operations still work |
| `libs/db/.../DbCausalWatermarkFilter.kt` | Invalid causal watermark is ignored; writer-LSN read failure returns no watermark | Keeps HTTP flowing | Silent consistency degradation; should become a structured error when guarantees are required |
| `app/bff/.../RestGateway.kt` | Invalid downstream watermark is ignored | Keeps BFF flowing | Silent consistency degradation; should be rejected or marked unavailable |

PostgreSQL rate-limit tables and repositories were removed. PostgreSQL remains
authoritative for business state, identity, sessions, audit, and financial data;
it is not a rate-limit fallback.

## Messaging, email, and event processing

| Location | Behavior | Reason | Assessment |
|---|---|---|---|
| `app/notifications/.../EmailDispatcher.kt` | Transient SMTP failures retry up to `maxAttempts` | Handles temporary SMTP outages | Bounded retry; alert after exhaustion |
| `app/notifications/.../AuthEmailRabbitListener.kt` | Malformed messages reject; transient failures retry once and repeated failures go to DLQ | Separates poison messages from temporary failures | Bounded retry/DLQ; requires monitoring |
| `app/bff/.../RabbitBffEventListener.kt` | Failed messages reject without requeue | Prevents poison-message loops | Bounded failure; verify DLQ/alert behavior |
| `app/notifications/.../NotificationEventConsumer.kt` | Preference-store errors suppress email; unexpected delivery errors are swallowed | Prevents consumer failure from rolling back event processing | Silent suppression; high risk of lost notifications; replace with durable retry/outbox state |
| `app/notifications/.../NotificationEventConsumer.kt` | Rate-limited delivery is skipped | Enforces recipient policy | Intentional policy; emit metrics/alerts |
| `app/expense-core/.../OutboxMessagingConfiguration.kt` | Production outbox requires RabbitMQ and configured properties | Prevents in-memory publication | Fail-closed dependency wiring |
| `app/bff/.../BffMessagingConfiguration.kt` | Production fanout requires RabbitMQ and configured properties | Prevents process-local cross-replica fanout | Fail-closed dependency wiring |

## Domain defaults

These initialize or shape business data rather than substitute for required
infrastructure:

- `JpaProfileStore` provisions missing profiles with UTC and EUR defaults.
- `JpaPreferenceStore` creates default notification preferences.
- Inbox APIs provide a bounded default page size.
- Migrations initialize statuses, attempt counters, and booleans.
- `UpstreamExpense` supplies presentation text when a description is null.
- Recurring-expense worker settings default scheduling off and bound catch-up.
- BFF deduplication supplies a bounded memory capacity; distributed fanout still
  requires RabbitMQ.

These values are product or operational policy and require explicit review when
changed.

## Parsing, validation, and tooling

`runCatching`, `getOrNull`, and `orElse(null)` are used for validation or optional
data handling in email addresses, encrypted handoffs, cursors, UUIDs, event
envelopes, dates, and optional headers. Invalid required payloads are converted
to structured errors or rejected messages. The causal-watermark cases are
different: silently dropping them can weaken read-after-write guarantees.

Docker Compose, IntelliJ, Bruno, and operational scripts use localhost endpoints
and local-only credentials for clone-and-run development. These defaults must not
be inherited by production.

## Stale observability references

The reader-to-writer fallback was removed, but obsolete references remain in:

- `infra/observability/rules/squarewise.yml`
- `infra/observability/grafana/dashboards/squarewise-overview.json`
- `docs/operations/cqrs-replica.md`

Replace them with reader failure, lag, and fail-closed read metrics. Alerts must
not imply that an unavailable fallback still operates.

## Review rules

1. Required security, rate-limit, persistence, and messaging dependencies must
   fail startup or fail the request explicitly; never silently switch stores.
2. Retries must be bounded, observable, and terminate in durable failure or DLQ.
3. Generic external responses may hide account existence, but internal telemetry
   must retain failure attribution without sensitive data.
4. Domain defaults must be documented as product policy, not infrastructure fallback.
5. Test doubles and in-memory implementations belong in test source sets only.
