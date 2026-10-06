# ERRC-17: Implement GraphQL mapper and upstream problem client

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 3 — Transport boundaries
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Implement the GraphQL exception resolver and typed upstream WebClient problem decoder in `app/bff` and `libs/errors`. Ensure upstream REST Problem Details preserve their original `code`, `numericCode`, `errorName`, `requestId`, and `source` when surfaced in GraphQL `extensions`. Distinguish gateway transport outages from upstream business errors, and eliminate string message matching on subscription errors.

## Dependencies

- Preceding: [`ERRC-07`](ERRC-07.md), [`ERRC-12`](ERRC-12.md), [`ERRC-14`](ERRC-14.md)

## Owned Paths

- `docs/tasks/details/ERRC-17.md`
- `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/errors/`
- `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/errors/`
- `libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/graphql/`

## Architecture & Design Patterns

- **Anti-Corruption Layer (ACL) & Identity Preservation**: The BFF translates upstream HTTP failures into GraphQL errors while preserving upstream error identities without mutation or loss.
- **Ports & Adapters (WebClient Error Decoder Port)**: Encapsulates upstream HTTP error decoding behind a clean interface, parsing RFC 9457 Problem Details into typed domain representations.
- **Reactive Stream Error Handling**: Leverages Project Reactor operators (`onErrorResume`, `onErrorMap`) to ensure non-blocking, asynchronous error propagation without losing trace context or thread-local MDC data.
- **Strict SOLID File Separation**: Separate files for GraphQL Exception Resolver, Upstream Problem Decoder, Upstream Service Exception, and GraphQL Extensions Formatter.

## Common Libraries & Framework Integration

- **`libs/errors`**: GraphQL extensions builder and core exception hierarchy.
- **Spring GraphQL & WebFlux**: `DataFetcherExceptionResolver` and WebClient filter functions.

## Technical Requirements & Deliverables

1. **Upstream Problem Decoder (`UpstreamProblemDecoder.kt`)**:
   - WebClient filter function that parses 4xx/5xx responses:
     - Parses JSON body into `ProblemDetailsDto`.
     - Preserves upstream `code`, `numericCode`, `errorName`, `requestId`, and `source`.
     - Throws strongly-typed `UpstreamProblemException`.
2. **GraphQL Exception Resolver (`BffGraphQLErrorResolver.kt`)**:
   - Implements Spring GraphQL `DataFetcherExceptionResolver`:
     - Catches `UpstreamProblemException` -> Formats `GraphQLError` with upstream fields in `extensions`.
     - Catches `SquarewiseException` -> Formats `GraphQLError` using BFF catalog definition.
     - Catches unhandled exceptions -> Emits safe `GATEWAY_INTERNAL_ERROR` (`419901`).
3. **GraphQL Extensions Formatter (`GraphQLExtensionsFormatter.kt`)**:
   - Builds extensions map:
     ```json
     {
       "code": "NOT_FOUND",
       "numericCode": "213201",
       "errorName": "GROUP_NOT_FOUND",
       "requestId": "c1a2...",
       "source": "expense-core",
       "timestamp": "2026-10-06T21:00:00Z"
     }
     ```
4. **Integration & Upstream Fault Tests (`BffErrorResolverTest.kt`)**:
   - WireMock upstream failure simulations (upstream 400, 404, 500, network drop, timeout).
   - Verify upstream `requestId` is retained; no new request ID is fabricated for upstream errors.

## Acceptance Criteria

1. Every resolver, formatter, and exception resides in its own isolated `.kt` file.
2. Upstream REST errors are mirrored into GraphQL `extensions` with 100% field preservation.
3. Network timeouts to upstream services emit `BFF_UPSTREAM_TIMEOUT` (`428701`) rather than generic 500.
4. No English-language message string matching is used to classify GraphQL or subscription errors.
5. All integration tests pass with zero errors.

## Validation Commands

```powershell
./gradlew.bat :app:bff:test :app:bff:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JaCoCo test report showing >95% coverage on BFF error translation components.
- GraphQL query response logs proving byte-for-byte preservation of upstream error properties.

## Rollout & Rollback Strategy

- Deployed to BFF service.
- Transparent to clients; extensions additions are purely additive.
- Rollback: Revert resolver changes if GraphQL schema mapping errors occur.

## Implementation Notes and Evidence

- Added typed `UpstreamProblemException`, structural `UpstreamProblemDecoder`,
  additive `GraphQLExtensionsFormatter`, and `BffGraphQLErrorResolver`.
- Upstream `code`, `numericCode`, `errorName`, `requestId`, `source`, and
  timestamp are preserved in GraphQL extensions; local catalog failures use
  compiled BFF definitions, and `TimeoutException` maps to the frozen catalog
  `UPSTREAM_TIMEOUT` (`426801`). No English message matching is used.
- The existing resolver delegates governed/upstream typed failures to the new
  boundary while retaining legacy `ApplicationException` compatibility.
- Focused `BffErrorResolverTest` and the existing `GraphQlExceptionResolverTest`
  passed on 2026-10-07. Contract validation and `git diff --check` passed.
- The full `:app:bff:test` run remains open: 25 existing Spring-context tests
  fail during DataSource bean startup in this environment. This task remains
  `in_progress` until the declared full BFF gate is rerun with its required
  test database configuration.
