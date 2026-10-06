# End-to-End & Chaos Test Suite

The end-to-end test suites run against the live local environment (`infra/local/docker-compose.dev.yml`) and verify complete multi-service functionality, distributed resilience, concurrency, and fault tolerance across Accounts, Expense Core, Notifications, and GraphQL BFF.

### Test Suites

1. **Product Journey Lifecycle (`test_product_journey.py`)**:
   - Explicit local-fixture profile enrollment via Accounts and GraphQL BFF (`me`).
   - Group lifecycle via GraphQL BFF (`createGroup`, `group`).
   - Invitations and membership claiming across users via Expense Core.
   - Multi-participant expense creation with equal allocation splits.
   - Real-time balance calculations, zero-sum invariant verification, and currency netting.
   - Multi-currency settlement suggestions engine via GraphQL BFF.
   - Repayments recording via GraphQL BFF (`recordRepayment`).
   - Outbox relay transactional event publishing to RabbitMQ and consumption into Notifications Inbox.
   - Offline synchronization feed snapshot & change tracking.
   - When invoked with `--evidence-output`, emits success-only QA-10 evidence
     for the 42 operations asserted by this journey; login/session, archive,
     revoke/update-group, and notification-preference operations remain
     explicitly outside this suite.

2. **Offline Client Sync & Replay Resilience (`test_offline_resilience.py`)**:
   - Client offline mutation queueing with client-generated UUIDs and unique `Idempotency-Key` headers.
   - Reconnect and batch replay via GraphQL BFF (`createExpense`).
   - Retransmission idempotency: identical retries succeed with 0 balance changes and 0 duplicate postings.
   - Conflicting idempotency reuse: modified payload with existing key correctly rejected with HTTP 409 (`ERR_06` / `CONFLICT`).
   - Offline sync cursor gap recovery via `/sync/changes?cursor=...`.
   - When invoked with `--evidence-output`, emits retained QA-10 evidence only
     after the suite passes for `createGroup`, `createExpense`, `getBalances`,
     `getSnapshot`, and `getChanges`.

3. **Concurrent Member Edit Conflicts & Real-Time Invalidation (`test_concurrency_subscriptions.py`)**:
   - Real-time WebSocket connection to GraphQL BFF via RFC 6455 and `graphql-transport-ws`.
   - Subscription to `groupChanged(groupId: ID!)` with immediate delivery of revision and `changeId` invalidation events.
   - Concurrent race testing: simultaneous PUT updates to the same expense version. Exactly 1 succeeds (version increments to 2), competing update receives HTTP 409 Conflict (`ERR_06`).
   - Conflict resolution: stale client fetches latest state and reapplies cleanly.
   - When invoked with `--evidence-output`, emits retained QA-10 evidence only
     after all subscription and concurrency assertions pass.

4. **Message Broker Outage Chaos & Transactional Outbox Recovery (`test_chaos_recovery.py`)**:
   - Fault injection: pauses Expense Core and verifies GraphQL `groups` returns a
     structured upstream error without fabricated data, then verifies the
     query recovers after Expense Core is restored.
   - Fault injection: pauses RabbitMQ broker container.
   - Verifies Expense Core local ACID isolation: expense writes, balance postings, and audit entries continue to succeed without error.
   - PostgreSQL inspection: outbox records held safely in `PENDING` state.
   - Fault healing: unpauses RabbitMQ; verifies outbox relay daemon drains `PENDING` records to `PUBLISHED`.
   - End-to-end verification: Notifications service receives and confirms delivered events.

5. **Redis Authentication Rate-Limit Resilience (`test_auth_cache_resilience.py`)**:
   - Shared Redis admission reaches the refresh limit, evicts only the
     `squarewise:rl:v1:*` namespace, and admits again after targeted cleanup.
   - Redis outage fails closed with HTTP 429 for both refresh and passwordless
     login-start, and restart recovers with no PostgreSQL or process-local
     fallback.
   - When `BEARER_TOKEN` is supplied, the probe also verifies bounded
     `store_error` counters increase for both policies during the outage.

6. **GraphQL Rate-Limit Surfaces (`test_auth_rate_limit_surfaces.py`)**:
   - Exercises the public `/graphql` HTTP boundary through the configured
     120-request window and verifies the next request returns HTTP 429.
   - Exercises 20 public GraphQL WebSocket handshakes and verifies the next
     handshake returns HTTP 429 under the distinct WebSocket policy.
   - Stops Redis and verifies both public GraphQL admission paths fail closed
     with HTTP 429 before restarting it.
   - Clears only `squarewise:rl:v1:*`; it requires a real `BEARER_TOKEN` for
     the WebSocket handshake and must run against the dedicated local/CI stack.

7. **BFF Replica Rate-Limit Sharing (`test_auth_bff_replicas.py`)**:
   - Alternates authenticated GraphQL HTTP requests across two disposable BFF
     containers sharing one Redis instance and verifies the shared cap plus one
     response is HTTP 429.
   - Repeats the same check for WebSocket handshakes across both containers.
   - This proves local cross-process admission only; revoked-token reconnect,
     production topology, and capacity evidence remain separate.

8. **Local JWT identity lookup isolation (`test_auth_no_accounts_lookup.py`)**:
   - Stops Accounts after token acquisition and verifies authenticated Expense
     Core and BFF group reads still succeed through local JWT validation.
   - This proves no request-time Accounts call is required; it is local
     dependency-isolation evidence and does not replace SQL/query telemetry.

9. **Auth-email delivery admission (`test_auth_notification_rate_limit.py`)**:
   - Requires the test deployment overrides `SQUAREWISE_AUTH_LOGIN_RESEND_COOLDOWN_SECONDS=0`,
     `SQUAREWISE_NOTIFICATIONS_DELIVERY_MAX_PERMITS=1`, and
     `SQUAREWISE_NOTIFICATIONS_DELIVERY_WINDOW_SECONDS=2`.
   - Emits real same-recipient login events through the Accounts outbox and
     RabbitMQ, verifies Notifications delivers the first, suppresses the
     second before provider dispatch, and delivers again after the Redis
     window expires.
   - The overrides are test-only; production defaults remain unchanged.

9. **Auth-email Redis outage (`test_auth_notification_redis_outage.py`)**:
   - Queues a real auth-email event, stops Redis only while Notifications consumes
     it, and requires the event to reach the auth-email DLQ without Mailpit dispatch.
   - Restarts Redis and requires a subsequent real auth-email event to be delivered.

10. **Cross-process login admission (`test_auth_login_replicas.py`)**:
   - Starts the disposable `docker-compose.auth-replicas.yml` overlay with two
     Accounts containers sharing PostgreSQL and Redis.
   - Alternates six login-start requests between the two published replica
     ports and requires five `202` responses followed by one shared `429`.
   - This proves local distributed-window behavior only; it is not a production
     scale or multi-zone capacity result.

11. **Concurrent refresh rotation (`test_auth_refresh_concurrency.py`)**:
    - Obtains one real passwordless session, submits two concurrent refresh
      requests with the same token, and requires exactly one `200` plus one
      reuse `401`.
    - Presents the winning child token again and requires `401`, proving the
      PostgreSQL-authoritative family revocation path after reuse detection.

12. **Passwordless Auth-Email Delivery (`test_auth_email_delivery.py`):**
   - Real Accounts outbox/RabbitMQ/Notifications/Mailpit CODE delivery.
   - One-time credential redemption and replay rejection.
   - Refresh-family revocation after logout and idempotent logout replay.
   - Remaining acceptance work is explicit: LINK delivery, expiry, wrong-subject
     redemption, rate-limit/error redaction, broker retry/DLQ, and log/output
     secret absence.
   - When invoked with `--evidence-output`, emits success-only QA-10 evidence
     for `startLogin`, `verifyLogin`, `logout`, and `refreshToken`.

### Running Test Suites via Makefile

```sh
# Run individual test suites
make e2e-live
make e2e-auth-cache
make e2e-auth-surfaces
make e2e-auth-no-accounts
make e2e-offline
make e2e-concurrency
make e2e-chaos

# Run all suites in sequence
make e2e-all
```

The live HTTP helpers use a ten-second request timeout. The raw WebSocket
client uses a ten-second TCP/protocol-setup timeout and switches to blocking
event reads only after `connection_ack`; this prevents unavailable services
from hanging a test process indefinitely. These client timeouts do not claim
that application-level timeout or retry policy is production-proven.
The local OIDC token helper enrolls the three Keycloak service-account subjects in
the local Accounts database before the suites run. This is fixture setup only:
application profile reads remain non-provisioning, and production/staging never
seed or implicitly create profiles from bearer-token reads.

The product journey exercises Accounts batch lookup in both permitted modes:
owner-only requests use the signed user token, while mixed or unknown profile IDs
send the explicit `X-Squarewise-Workload-Role: internal-service` header. This
matches the fail-closed authorization boundary; ordinary users are not granted
bulk profile access merely to test deduplication.
