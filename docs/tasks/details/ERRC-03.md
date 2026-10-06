# ERRC-03: Freeze registries and catalog schema

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 0 — Governance and frozen evidence
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Formalize the machine-readable schema for the six-digit error catalog and finalize domain/module namespace immutability rules. Define the authoritative JSON Schema that governs error definitions in `contracts/errors/error-catalog.schema.json` and freeze `contracts/errors/domains.yaml`.

## Dependencies

- Preceding: [`ERRC-02: Freeze audit and baseline behavior`](ERRC-02.md)

## Owned Paths

- `docs/tasks/details/ERRC-03.md`
- `contracts/errors/domains.yaml`
- `contracts/errors/error-catalog.schema.json`
- `contracts/errors/lifecycle-history.schema.json`
- `docs/architecture/error-code-standard.md`
- `docs/architecture/error-domain-registry.md`

## Architecture & Design Patterns

- **Repository Pattern (Schema & Allocation Authority)**: The catalog schema acts as the single authoritative repository specification for error definitions across all domains.
- **Contract-First & Immutability Pattern**: Strict immutability rules for assigned error codes, names, and namespaces once published. Breaking modifications are structurally impossible.
- **Open/Closed Principle (OCP)**: Schema extensible for new domain modules and error codes via strictly monotonic allocations, while closed to modifications of existing mappings.
- **Strict Typing & Constraint Modeling**: JSON Schema validation with typed enums, strict regex constraints (`^[1-9]{4}(0[1-9]|[1-9][0-9])$`), and required conditional properties.

## Common Libraries & Framework Integration

- **`libs/errors`**: Defines data structures matching the catalog schema (`ErrorCode`, `ErrorDefinition`, transport metadata).
- **`tools/contracts`**: Python-based contract validation ensuring zero schema drift.

## Technical Requirements & Deliverables

1. **Catalog JSON Schema (`contracts/errors/error-catalog.schema.json`)**:
   - Fields: `numericCode`, `errorName`, `legacyCode`, `domain`, `module`, `layer`, `category`, `sequence`, `title`, `safeDetail`, `messageKey`, `owner`, `source`, `component`, `operation`, `transports`, `httpStatus`, `graphqlClassification`, `legacyCompatibility`, `retryPolicy`, `severity`, `requiredHeaders`, `disclosure`, `introducedIn`, `deprecatedIn`, `retiredIn`, `replacedBy`, `runbook`.
   - Conditional requirements:
     - `httpStatus` required if and only if `transports` contains `REST`.
     - `graphqlClassification` required if and only if `transports` contains `GRAPHQL`.
     - `runbook` required if `severity` is `CRITICAL`, or `retryPolicy` is not `NEVER`, or `category` is `6` (Data/Consistency) or `8` (Availability).
2. **Registry Immutability Freeze**:
   - Update `contracts/errors/domains.yaml` status to `frozen-authoritative`.
   - Lock Domains 1 (Accounts), 2 (Expense Core), 3 (Notifications), 4 (BFF), 9 (Platform).
   - Lock Reserved Domains 5–8 for future bounded contexts.
3. **Lifecycle History Schema (`contracts/errors/lifecycle-history.schema.json`)**:
   - Track deprecation dates, sunset policies, and migration replacements for all error codes.

## Acceptance Criteria

1. `contracts/errors/error-catalog.schema.json` is created, fully typed, and validates both existing legacy mappings and new six-digit catalog records.
2. `contracts/errors/domains.yaml` is frozen with exact domain, module, layer, and category mappings.
3. Schema enforcement strictly validates that no sequence code is `00` and all digits are between `1` and `9`.
4. Python validation script parses and validates the schema against sample mock catalogs.
5. All verification commands pass with zero errors.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
git diff --check
```

## Verification Results & Evidence

- **Domains Registry Frozen**: `contracts/errors/domains.yaml` status updated to `frozen-authoritative`. Locked bounded-context domains 1 (Accounts), 2 (Expense Core), 3 (Notifications), 4 (BFF), 9 (Platform), and reserved domains 5–8.
- **Catalog Schema Created**: `contracts/errors/error-catalog.schema.json` authored with JSON Schema Draft 2020-12, validating both legacy `ERR-XX` mappings and complete six-digit `DMLCEE` error records with conditional requirements (`httpStatus` for REST, `graphqlClassification` for GRAPHQL, `runbook` for CRITICAL/retryable/category 6/8).
- **Lifecycle History Schema Created**: `contracts/errors/lifecycle-history.schema.json` authored with JSON Schema Draft 2020-12 tracking deprecation, sunset, and replacements.
- **Tooling and Validation Tests**: `tools/errors/validate_catalog.py` updated with strict Python typing and schema verification; 6 unit tests in `tools/errors/test_validate_catalog.py` verifying mock assertions (sequence `!= 00`, digits 1-9, conditional properties) passed cleanly.
- **Commands Executed**:
  - `uv run python tools/contracts/validate.py` passed (252 tasks, 10 JSON schemas valid).
  - `uv run python tools/errors/validate_catalog.py` passed (frozen registry, 2 JSON schemas, mock assertions, 12 catalog codes).
  - `uv run python -m unittest tools/errors/test_validate_catalog.py` passed (6 tests).
  - `uv run mypy tests tools` passed (56 source files).
  - `git diff --check` passed cleanly.

## Rollout & Rollback Strategy

- Additive contract and schema task.
- Zero production code impact.
- Rollback: Revert JSON schema files if inconsistencies are identified before Phase 1.
