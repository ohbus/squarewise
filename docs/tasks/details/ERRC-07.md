# ERRC-07: Define GraphQL and WebSocket error contracts

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 1 — Contract-first compatibility
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Establish the authoritative GraphQL extension error schema and WebSocket lifecycle error contracts for the GraphQL BFF (`app/bff`). Ensure that upstream errors from REST domain services preserve their canonical identities (`numericCode`, `errorName`, `requestId`, `source`) without loss or mutation, and specify distinct error behavior for GraphQL query/mutation resolvers and WebSocket subscription channels.

## Dependencies

- Preceding: [`ERRC-04: Allocate and review the complete error catalog`](ERRC-04.md)
- BFF context guide: [`docs/architecture/errors/bff.md`](../../architecture/errors/bff.md)

## Owned Paths

- `docs/tasks/details/ERRC-07.md`
- `contracts/graphql/errors.graphqls`
- `contracts/graphql/README.md`
- `docs/architecture/errors/bff.md`
- `docs/architecture/error-flow.md`

## Architecture & Design Patterns

- **Anti-Corruption Layer (ACL)**: The GraphQL BFF gateway acts as an ACL protecting web and mobile clients from upstream network topology while faithfully propagating domain errors.
- **Identity Preservation Pattern**: Upstream REST Problem Details (`code`, `numericCode`, `errorName`, `requestId`, `source`) are preserved byte-for-byte in GraphQL `extensions`. The BFF must not fabricate synthetic request IDs or synthesize errors based purely on HTTP status codes.
- **Ports & Adapters (Transport Decoupling)**: Distinguishes gateway-introduced transport errors (e.g. timeout, DNS resolution failure) from upstream business errors.
- **Reactive Context Propagation**: Ensures Spring WebFlux and GraphQL subscription contexts preserve tracing IDs, tenant boundaries, and correlation tokens across asynchronous boundaries.

## Common Libraries & Framework Integration

- **`libs/errors`**: Provides the data models for GraphQL exception handling and `GraphQLError` extensions formatting.
- **`libs/observability`**: Reactor context correlation for request IDs in GraphQL and WebSocket pipelines.

## Technical Requirements & Deliverables

1. **GraphQL Error Extensions Contract (`contracts/graphql/errors.graphqls`)**:
   - Define standard GraphQL error shape:
     ```graphql
     """
     Standard extensions attached to all GraphQL error responses.
     """
     type ErrorExtensions {
       code: String!
       numericCode: String
       errorName: String
       requestId: String!
       source: String!
       timestamp: String!
       violations: [ValidationError!]
     }

     type ValidationError {
       field: String!
       message: String!
     }
     ```
2. **WebSocket Lifecycle & Close Codes Contract**:
   - Define custom WebSocket close codes (4400-4499 range) for subscription connection termination:
     - `4401`: Unauthorized / Expired authentication token.
     - `4403`: Forbidden / Access revoked to requested group subscription.
     - `4408`: Connection idle timeout.
     - `4429`: Subscription rate limit exceeded.
   - Document GraphQL-over-WebSocket protocol (`graphql-transport-ws`) error frame formats.
3. **Upstream Error Preservation Rules**:
   - Upstream 4xx business error -> Formatted GraphQL error in `errors` array, partial data returned where query fields are nullable.
   - Upstream 5xx service outage -> Classified as `GATEWAY_UPSTREAM_ERROR` with numeric code `428701` if upstream problem is unparseable; otherwise preserves upstream problem details.

## Acceptance Criteria

1. `contracts/graphql/errors.graphqls` is created and integrated into the schema validation suite.
2. All 9 GraphQL root operations validate successfully with the updated schema files.
3. Exact mapping rules between HTTP status, Problem Details, and GraphQL `extensions` are codified in `docs/architecture/errors/bff.md`.
4. WebSocket close codes and error frame specifications are fully documented and review-approved.
5. Verification commands execute cleanly.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- Clean GraphQL schema validation output across all `.graphqls` files.
- Complete mapping table from upstream REST responses to GraphQL extensions.

## Rollout & Rollback Strategy

- Schema and documentation specification milestone.
- Client-compatible; additions to GraphQL `extensions` are non-breaking.
- Rollback: Revert schema additions if GraphQL client tooling compatibility checks flag issues.
