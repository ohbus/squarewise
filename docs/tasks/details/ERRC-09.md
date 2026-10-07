# ERRC-09: Upgrade contract validation and breaking-change gates

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 1 — Contract-first compatibility
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Build, type, and integrate rigorous Python-based validation tools to enforce error catalog consistency, OpenAPI schema parity, breaking-change detection, and immutable allocation rules. Upgrade `tools/contracts/` and `tools/errors/` with strict static typing (`mypy`), ensuring that any illegal code sequence, unmapped throw site, duplicate code, or schema regression breaks CI immediately.

## Dependencies

- Preceding: [`ERRC-03`](ERRC-03.md), [`ERRC-05`](ERRC-05.md), [`ERRC-06`](ERRC-06.md), [`ERRC-07`](ERRC-07.md), [`ERRC-08`](ERRC-08.md)

## Owned Paths

- `docs/tasks/details/ERRC-09.md`
- `tools/errors/validate_six_digit_catalog.py`
- `tools/contracts/validate_openapi_parity.py`
- `tools/contracts/detect_breaking_error_changes.py`
- `tests/tools/test_error_validators.py`
- `Makefile`

## Architecture & Design Patterns

- **Defense in Depth & Static Analysis Gate**: CI acts as an automated compiler and gatekeeper rejecting any contract deviation before code merges.
- **Pure Functions & Fail-Fast Pattern**: Validation scripts are implemented as pure, deterministic Python functions with explicit return codes and structured diagnostic error messages.
- **Strict Static Typing Rule**: 100% compliant with repository guidelines requiring full Python type annotations (`mypy --strict`), eliminating untyped `dict` and `list` abstractions via typed `dataclass` or `NamedTuple` definitions.
- **Immutable History Verification**: Cryptographically checks historical registry files to prevent rewriting or deleting previously assigned error codes.

## Common Libraries & Framework Integration

- **`uv` / `pyproject.toml`**: Isolated environment and fast test execution for repository Python tooling.
- **`Makefile`**: Integration into `make check`, `make contracts`, and `make lint`.

## Technical Requirements & Deliverables

1. **Six-Digit Catalog Validator (`tools/errors/validate_six_digit_catalog.py`)**:
   - Validates `contracts/errors/error-catalog.yaml` against:
     - Regex `^[1-9]{4}(0[1-9]|[1-9][0-9])$`.
     - Monotonic sequence within each domain/module/layer/category slice.
     - Uniqueness of `numericCode` and `errorName`.
     - Agreement between numeric code digits and domain/module/layer/category mappings in `contracts/errors/domains.yaml`.
     - Required field completeness based on declared transports.
2. **OpenAPI Error Parity Validator (`tools/contracts/validate_openapi_parity.py`)**:
   - Ensures all three OpenAPI documents (`accounts`, `expense-core`, `notifications`) have uniform `ProblemDetails` components.
   - Verifies that all endpoints declare required non-2xx status responses.
3. **Breaking Change Detector (`tools/contracts/detect_breaking_error_changes.py`)**:
   - Compares the active catalog against Git master or baseline tag.
   - Flags errors if an existing `numericCode` or `errorName` is modified, deleted, or renumbered.
4. **Unit Tests for Tooling (`tests/tools/test_error_validators.py`)**:
   - Comprehensive test suite covering positive valid cases and negative failure cases (duplicate codes, invalid digits, missing fields).
5. **Makefile Targets**:
   - Wire validation scripts into `make contracts` and `make python-typecheck`.

## Acceptance Criteria

1. All Python tooling passes `uv run --frozen --no-build mypy` in strict mode with zero errors.
2. Test suite `tests/tools/test_error_validators.py` executes and passes all test cases.
3. Catalog validator correctly rejects test fixtures with invalid regex, duplicate codes, sequence `00`, or mismatched domains.
4. Breaking change detector reliably prevents removal or mutation of assigned codes.
5. `make contracts` executes the updated validation pipeline in under 5 seconds.

## Validation Commands

```powershell
uv run --frozen --no-build mypy tools/errors tools/contracts tests/tools
uv run --frozen --no-build python -m unittest tests/tools/test_error_validators.py
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- 100% passing test output from unittest and mypy.
- Execution logs demonstrating intentional failure on synthetic invalid catalog fixtures.

## Implementation Notes and Verification

- Added `validate_six_digit_catalog.py` for code-shape, namespace registration,
  sequence monotonicity, uniqueness, transport requirements, and domain/module
  agreement checks.
- Added `validate_openapi_parity.py` for canonical ProblemDetails parity,
  required response components/headers, and all 45 operation status references.
- Added `detect_breaking_error_changes.py` for immutable numeric-code and
  symbolic-name comparison against a Git baseline. A missing baseline catalog
  is reported as not applicable because this branch introduced the catalog;
  supplied baselines still fail closed on deletion or renumbering.
- Added five positive/negative unit tests and wired all three validators into
  the `contracts` Make target. The existing `python-typecheck` target already
  covers `tools` and `tests` under strict mypy.
- Strict mypy passed for `tools/errors tools/contracts tests/tools`; the unit
  suite passed; all three validators, the base contract validator, and
  `git diff --check` passed. Synthetic invalid catalog and missing-response
  cases were asserted by the unit tests. GNU Make is not installed in the
  Windows shell, so `mingw32-make contracts` was used for the actual target;
  it passed in approximately 3.4 seconds. Plain `make contracts` was not run.

## Rollout & Rollback Strategy

- Tooling and CI gate upgrade.
- Zero production runtime impact.
- Rollback: Revert script changes if CI false-positives block development.
