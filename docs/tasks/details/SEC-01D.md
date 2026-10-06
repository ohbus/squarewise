# SEC-01D: Close browser mutation and subscription time-of-check gaps

## Status

Done. Committed.

## Objective

Resolved findings SEC-003 (WebSocket continuous authorization) and SEC-006 (browser
mutation CSRF protection).

## Implementation Notes

### SEC-006: Browser Mutation CSRF Protection

**`BrowserCsrfWebFilter`** was extended to cover cookie-authenticated GraphQL mutations:

- Previously only `/auth/token/refresh` and `/auth/logout` POST endpoints were guarded.
- Now any POST to `/graphql` when the access cookie (`squarewise_access`) is present
  **and** no explicit `Authorization` header is set triggers the double-submit CSRF check
  (`X-CSRF-Token` must match `SW_CSRF` cookie).
- Pure bearer-authenticated requests (explicit `Authorization: Bearer ...` header)
  bypass CSRF cleanly: native/CLI clients are unaffected.
- Non-POST requests (GraphQL queries via GET) are unaffected.
- **Critical ordering note**: the CSRF filter runs at `HIGHEST_PRECEDENCE+1`, _before_
  `BrowserAccessCookieWebFilter` (`HIGHEST_PRECEDENCE+2`), so the `Authorization` header
  is not yet injected from the cookie at CSRF check time. `isCookieAuthenticated()` checks
  the raw access cookie directly, not the `Authorization` header.

### SEC-003: WebSocket Continuous Authorization

**`LiveUpdateFanout`** now maintains per-subscription revocation signals:

- A `ConcurrentHashMap<String, Sinks.One<Void>> revocationSignals` is maintained
  alongside the existing `subscriptions` map.
- `subscribe()` creates both the `Subscriber` and a `Sinks.one()` revocation signal.
- `revokeUser()`, `revokeUserFromGroup()`, `unsubscribe()`, and `removeExpired()` all
  call `tryEmitEmpty()` on the removed subscription's signal, completing its `Mono<Void>`.
- New public method `revocationSignal(subscriptionId: String): Mono<Void>` exposes the
  signal to downstream consumers; returns `Mono.empty()` for unknown IDs.

**`GroupGraphqlController.groupChanged`** was updated to use `takeUntilOther`:

```kotlin
liveFanout.invalidations()
    .filter { it.groupId == groupId }
    .takeUntilOther(liveFanout.revocationSignal(subscription.id))
```

When `BffEventConsumer` processes a `member.removed` event and calls
`fanout.revokeUserFromGroup(removedSubject, groupIdStr)`, the revocation signal completes
and the subscriber's `Flux` terminates immediately. No changes were needed in
`BffEventConsumer` since `revokeUserFromGroup` already handles the revocation.

## Owned Paths

- `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/config/BrowserCsrfWebFilter.kt`
- `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/realtime/LiveUpdateFanout.kt`
- `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/graphql/GroupGraphqlController.kt`
- `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/config/BrowserCsrfWebFilterTest.kt`
- `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/LiveUpdateFanoutTest.kt`
- `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/graphql/GroupGraphqlControllerTest.kt`

## Acceptance Criteria

1. ✅ An established WebSocket subscription terminates or suppresses events as soon as
   the subscriber loses group membership.
2. ✅ GraphQL mutations called with cookie authentication require the CSRF double-submit
   header (`X-CSRF-Token` matching `SW_CSRF`).
3. ✅ GraphQL queries or bearer-authenticated requests remain unaffected.
4. ✅ Comprehensive tests cover midstream revocation, CSRF mismatch, missing token,
   and allowed bearer mutations.

## Verification Commands

- `./gradlew :app:bff:test --rerun-tasks --no-daemon`
- `uv run python tools/contracts/validate.py`
- `uv run python tools/ops/check_security_hygiene.py`

## Evidence

| Command | Result |
|---|---|
| `./gradlew :app:bff:test --rerun-tasks --no-daemon` | BUILD SUCCESSFUL (18 tasks) |
| `uv run python tools/contracts/validate.py` | valid (207 tasks) |
| `uv run python tools/ops/check_security_hygiene.py` | passed (1031 files) |

## Known Limitations

- The `invalidationSink` is a `directBestEffort` multicast; slow downstream
  consumers may lose invalidation events. This is pre-existing and out of scope.
- WebSocket re-authentication on token refresh is not yet implemented (separate task).
