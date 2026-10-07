# ERRC-31: Optional future major-version numeric `code`

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 5 — Clients, acceptance, scale, rollout
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Design and prepare the future major-version API breaking change (API v2) where the primary `code` property becomes the canonical six-digit machine code (`"213201"`), and `legacyCode` is retained only as an optional transition field. Establish the architectural decision, client SDK migration guides, and parallel-run test suites for this breaking evolution. This task is NOT part of the v1 migration and requires a separate major version release.

## Dependencies

- Preceding: [`ERRC-30: Retire legacy infrastructure, not the v1 field`](ERRC-30.md)
- Requires explicit future API major-version charter and product approval.

## Owned Paths

- `docs/tasks/details/ERRC-31.md`
- `contracts/rest/v2/` (future v2 specifications)
- `docs/architecture/decisions/adr-v2-error-code-breaking-change.md`
- `docs/api/migration-notices/v2-numeric-error-code-migration.md`

## Architecture & Design Patterns

- **Semantic Versioning & Breaking Change Management**: Strictly confines breaking field semantics to a new major API version (`/v2/`), ensuring API v1 continues serving existing mobile and web clients without forced upgrades.
- **Side-by-Side Dual Routing**: API Gateway / BFF routes `/v1/` requests to legacy-compatible responses and `/v2/` requests to the canonical numeric-code responses.
- **Client SDK Portability**: Provides strongly-typed SDK models for Kotlin, TypeScript, and Swift supporting both v1 and v2 error contracts.

## Common Libraries & Framework Integration

- **`libs/errors`**: Provides dual ProblemDetails response serializers (v1 vs v2).
- **API Versioning**: URL-path or header-based API version dispatching in Spring Web MVC and BFF.

## Technical Requirements & Deliverables

1. **Architectural Decision Record (`docs/architecture/decisions/adr-v2-error-code-breaking-change.md`)**:
   - Documents the rationale, client impact, and deprecation roadmap for replacing symbolic `code` with numeric `code`:
     ```json
     {
       "code": "213201",
       "errorName": "GROUP_NOT_FOUND",
       "legacyCode": "NOT_FOUND",
       "title": "Group not found",
       "status": 404,
       ...
     }
     ```
2. **Prototype v2 OpenAPI Contracts (`contracts/rest/v2/`)**:
   - Draft OpenAPI specs demonstrating v2 endpoints and error shapes.
3. **Client Migration Guide (`docs/api/migration-notices/v2-numeric-error-code-migration.md`)**:
   - Comprehensive upgrade guide for frontend web and mobile developers with code examples in TypeScript and Kotlin.
4. **Parallel-Version Contract Verification**:
   - Tests asserting that enabling v2 endpoints does not alter or break v1 endpoint responses.

## Acceptance Criteria

1. ADR clearly documents the v2 error design, deprecation schedule, and parallel routing strategy.
2. Prototype v2 Problem Details schema validates cleanly and passes contract validation.
3. API v1 remains 100% operational and undisturbed by v2 prototype additions.
4. Client migration guide is complete with code samples.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- Formal ADR signed off by architecture and API governance.
- Validated prototype v2 contracts in `contracts/rest/v2/`.

## 2026-10-07 readiness audit

ERRC-31 remains intentionally unstarted. The prerequisite ERRC-30 task is still
in progress because the ERRC-29 production-effective promotion and preceding
ERRC-28/ERRC-26 evidence are not available. Independently, this task requires an
explicit future API-major charter and product approval before a breaking `code`
change is designed. No ADR, `/v2/` contract, or client migration guide is therefore
authorized or present. The current v1 contract remains the implementation-backed
boundary and must not be changed by this task.

## Rollout & Rollback Strategy

- Future major version planning milestone.
- Does not affect current production deployments.
- Rollback: Revert v2 prototype specifications if product direction changes.
