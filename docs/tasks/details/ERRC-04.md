# ERRC-04: Allocate and review the complete error catalog

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 0 — Governance and frozen evidence
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Allocate the complete, authoritative six-digit error catalog in `contracts/errors/error-catalog.yaml` covering every audited production throw site and predictable failure across Accounts, Expense Core, Notifications, BFF, and Platform libraries. Review each error for least disclosure, security hiding, retry policy, and operational ownership. Formally retire `ERR-12` without replacement.

## Dependencies

- Preceding: [`ERRC-03: Freeze registries and catalog schema`](ERRC-03.md)
- Context guides: [`docs/architecture/errors/`](../../architecture/errors/)

## Owned Paths

- `docs/tasks/details/ERRC-04.md`
- `contracts/errors/error-catalog.yaml`
- `docs/architecture/errors/README.md`
- `docs/architecture/errors/accounts.md`
- `docs/architecture/errors/expense-core.md`
- `docs/architecture/errors/notifications.md`
- `docs/architecture/errors/bff.md`
- `docs/architecture/errors/platform-libraries.md`

## Architecture & Design Patterns

- **Domain-Driven Design (Bounded Context Ownership)**: Each error is owned by its specific bounded context (Accounts, Expense Core, Notifications, BFF, Platform) and capability module.
- **Principle of Least Disclosure / Security Shielding**: Ensure public error titles and safe details reveal no sensitive information, credentials, IDs, or database schemas. Anti-enumeration returns standard 404 for unauthorized accesses.
- **YAGNI (You Aren't Gonna Need It)**: Allocate errors only when client remediation, observability routing, retry strategy, or authorization boundaries differ. Collapse identical technical failures into shared semantic codes.
- **Immutable Historical Registry**: Every record is assigned an immutable numeric code (`DMLCEE`) and globally unique `SCREAMING_SNAKE_CASE` error name.

## Common Libraries & Framework Integration

- **`libs/errors`**: Prepares the definitions that will be codified into Kotlin constants in Phase 2.
- **`libs/ids`**: Aligns operation attribution with `ApiEndpoints` constants.

## Technical Requirements & Deliverables

1. **Complete Authoritative Catalog (`contracts/errors/error-catalog.yaml`)**:
   - Allocate six-digit codes across all domains:
     - Domain 1 (Accounts): Modules 11 (Profile), 12 (Auth), 13 (Session), 14 (Lifecycle), 15 (Identity).
     - Domain 2 (Expense Core): Modules 21 (Groups), 22 (Membership), 23 (Expenses), 24 (Settlements), 25 (Recurrence), 26 (Sync), 27 (Search), 28 (Outbox).
     - Domain 3 (Notifications): Modules 31 (Inbox), 32 (Delivery), 33 (Preferences), 34 (Email).
     - Domain 4 (BFF): Modules 41 (GraphQL), 42 (Transport), 43 (Live Update).
     - Domain 9 (Platform): Modules 91 (Errors), 92 (Security), 93 (Persistence), 94 (Messaging), 95 (Observability), 96 (IDs).
   - Each entry defines all required metadata (numericCode, errorName, legacyCode, domain, module, layer, category, sequence, title, safeDetail, messageKey, owner, source, component, operation, transports, httpStatus, graphqlClassification, retryPolicy, severity, requiredHeaders, disclosure, runbook).
2. **Legacy Code Mapping**:
   - Explicitly map all current `ERR-01` through `ERR-11` codes to their new canonical identities.
   - Designate `ERR-12` (the HTTP 200 governance check) as `RETIRED` with no six-digit replacement.
3. **Synchronization with Context Guides**:
   - Update all markdown guides under `docs/architecture/errors/` to achieve 100% parity with `error-catalog.yaml`.

## Acceptance Criteria

1. `contracts/errors/error-catalog.yaml` is created and passes validation against `contracts/errors/error-catalog.schema.json`.
2. Every audited call site from `ERRC-02` maps directly to an allocated catalog entry.
3. Zero codes use sequence `00` or invalid digits. All codes match regex `^[1-9]{4}(0[1-9]|[1-9][0-9])$`.
4. No two entries share the same numeric code or symbolic error name.
5. All security, anti-enumeration, and sensitive text disclosures are reviewed and verified clean of PII, internal paths, or SQL tokens.
6. Validation commands run cleanly.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
git diff --check
```

## Verification Results & Evidence

- **Authoritative Catalog Allocated**: `contracts/errors/error-catalog.yaml` created with 99 unique six-digit error codes across all 5 domains (Accounts: 18, Expense Core: 33, Notifications: 10, BFF: 11, Platform: 27), fully mapping 100% of the 131 audited throw sites from `ERRC-02`.
- **Legacy Mappings & ERR-12 Retirement**: Mapped `ERR-01` through `ERR-11` to canonical identities; formally retired `ERR-12` without replacement in `contracts/errors/lifecycle-history.yaml`.
- **Context Guides Parity**: Synchronized all guides in `docs/architecture/errors/` (`README.md`, `accounts.md`, `expense-core.md`, `notifications.md`, `bff.md`, `platform-libraries.md`) to authoritative status.
- **Validation Commands Executed**:
  - `uv run python tools/contracts/validate.py` passed (252 tasks, 10 JSON schemas valid).
  - `uv run python tools/errors/validate_catalog.py` passed (99 unique error codes validated against frozen domains and JSON Schema).
  - `uv run python -m unittest tools/errors/test_validate_catalog.py` passed (7/7 unit tests OK).
  - `uv run mypy tests tools` passed (57 source files, 0 issues).
  - `git diff --check` passed cleanly.

## Rollout & Rollback Strategy

- Specification and contract allocation milestone.
- Zero runtime code impact.
- Rollback: Revert catalog additions if reviews request reallocations before code generation in Phase 2.
