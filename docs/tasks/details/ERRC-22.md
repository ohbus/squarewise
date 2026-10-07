# ERRC-22: Migrate BFF definitions and failures

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 4 — Bounded-context migration
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Migrate all error handling, GraphQL resolvers, WebClient upstream communications, and WebSocket subscription handlers in the GraphQL BFF (`app/bff`) to use strongly-typed definitions from `BffErrors`. Eliminate the 4 legacy `ApplicationException(ErrorCode...)` call sites, eliminate string-matching logic in subscription error handlers, and ensure upstream error identities are faithfully preserved without re-minting synthetic request IDs.

## Dependencies

- Preceding: [`ERRC-17: Implement GraphQL mapper and upstream problem client`](ERRC-17.md)
- BFF guide: [`docs/architecture/errors/bff.md`](../../architecture/errors/bff.md)

## Owned Paths

- `docs/tasks/details/ERRC-22.md`
- `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/`
- `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/`
- `tools/qa/error_hygiene_allowlist.yaml` (prune BFF entries)

## Architecture & Design Patterns

- **Anti-Corruption Layer (ACL) & Identity Preservation**: The BFF preserves upstream REST Problem Details byte-for-byte in GraphQL `extensions`, passing `numericCode`, `errorName`, `requestId`, and `source` directly to clients.
- **Domain-Driven Design (BFF Bounded Context)**: Errors created directly by the BFF strictly use Domain 4 (`41xxxx` GraphQL, `42xxxx` Transport, `43xxxx` Live Update).
- **Reactive Error Composition**: Uses WebFlux reactive operators (`onErrorMap`, `onErrorResume`) without blocking, retaining Reactor context and MDC tracing attributes.
- **Strict SOLID File Separation**: Every new exception, resolver adapter, and client filter is placed in its own dedicated file.

## Common Libraries & Framework Integration

- **`libs/errors`**: `BffErrors`, `SquarewiseException`, `BffGraphQLErrorResolver`.
- **`libs/observability`**: Tracing correlation and WebSocket metric counters.
- **Spring GraphQL & WebFlux**: Data fetcher exception resolvers and reactive WebClient.

## Technical Requirements & Deliverables

1. **Replace 4 Production `ApplicationException` Usages**:
   - GraphQL module: `GRAPHQL_QUERY_TOO_COMPLEX` (`411101`), `GRAPHQL_VARIABLE_VALIDATION_FAILED` (`411102`).
   - Transport module: `UPSTREAM_SERVICE_UNAVAILABLE` (`428701`), `UPSTREAM_PROTOCOL_ERROR` (`427701`).
   - Live Update module: `SUBSCRIPTION_GROUP_ACCESS_REVOKED` (`437501`), `WEBSOCKET_RATE_LIMIT_EXCEEDED` (`431801`).
2. **Remove Fragile String-Matching Logic**:
   - Replace English message parsing in subscription disconnect handlers with structured error codes and WebSocket close codes.
3. **Preserve Upstream Trace & Request Context**:
   - Guarantee that when upstream returns an error, the upstream `requestId` is reflected in GraphQL `extensions.requestId` rather than a newly generated UUID.
4. **Prune Error Hygiene Allowlist**:
   - Remove all BFF entries from `tools/qa/error_hygiene_allowlist.yaml`.
5. **Service Test Suite Updates**:
   - Update WebTestClient and GraphQL test suites to verify that upstream error extensions contain `code`, `numericCode`, `errorName`, `requestId`, and `source`.

## Acceptance Criteria

1. Zero references to legacy `ErrorCode` or generic `ApplicationException` remain in `app/bff/src/main/`.
2. Upstream REST errors retain their original `code`, `numericCode`, `errorName`, `requestId`, and `source` in GraphQL extensions.
3. No string message matching exists in WebSocket subscription or resolver code.
4. BFF test suite passes cleanly with 100% test success and zero regression.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :app:bff:test :app:bff:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/qa/check_error_hygiene.py
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JaCoCo test report showing complete coverage across BFF GraphQL resolvers and client adapters.
- Verification log from `check_error_hygiene.py` demonstrating zero residual BFF debt.

## Rollout & Rollback Strategy

- Deployed to GraphQL BFF service.
- Transparent to clients; extensions additions are purely additive.
- Rollback: Standard Git revert of service branch if regressions occur.

## Implementation Notes and Evidence

- Replaced the four BFF production legacy throw sites with `BffDomainException`
  and governed `BffErrors`/`PlatformErrors` definitions. Browser-origin input
  validation now uses the typed `BffInputException` boundary.
- Reworked `GraphQlExceptionResolver` to classify local and transport failures
  through compiled definitions. Upstream `UpstreamProblemException` documents
  continue through the identity-preserving decoder/resolver path, retaining
  `code`, `numericCode`, `errorName`, `requestId`, and `source`.
- Removed all BFF entries from the error-hygiene allowlist; no legacy exception,
  error-code, or message-matching references remain in BFF production sources.
- Focused resolver, upstream-identity, and GraphQL controller tests passed.
  The declared full BFF command remains blocked by the pre-existing Spring test
  context/DataSource bean-creation failure: 25 of 183 tests fail before request
  execution. ERRC-17 owns closure of that environment gate, so this task remains
  `in_progress` pending a clean full-suite/JaCoCo run.
