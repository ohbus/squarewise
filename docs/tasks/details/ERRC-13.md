# ERRC-13: Add static policy and reflection/generic-throw gates

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 2 — Static error core, no runtime discovery
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Implement automated static analysis rules and CI verification gates to prohibit deliberate generic exception throws (`throw RuntimeException()`), raw numeric error code string literals, reflection or classpath scanning on error paths, and unsafe `catch (Throwable)` blocks across all Squarewise source code. Maintain a strictly managed, expiring allowlist for legacy debt undergoing migration.

## Dependencies

- Preceding: [`ERRC-12: Implement governed exception and typed-diagnostics contracts`](ERRC-12.md)

## Owned Paths

- `docs/tasks/details/ERRC-13.md`
- `tools/qa/check_error_hygiene.py`
- `tools/qa/error_hygiene_allowlist.yaml`
- `tests/tools/test_check_error_hygiene.py`
- `Makefile`

## Architecture & Design Patterns

- **Policy as Code & Architectural Fitness Function**: Enforce architectural constraints programmatically in CI via static abstract syntax tree (AST) and regex analysis.
- **Fail-Closed & Zero Tolerance**: Build breaks immediately if any newly introduced file contains deliberate generic throws or reflection lookups.
- **Expiring Allowlist Pattern**: Legacy debt is quarantined in `error_hygiene_allowlist.yaml` with explicit task owners, expiration gates, and removal milestones, preventing baseline ratcheting.
- **Strict Static Typing**: Python tooling strictly adheres to the repository typing rule (`uv run mypy --strict`).

## Common Libraries & Framework Integration

- **`tools/qa`**: Integrated into `make check`, `make lint`, and GitHub Actions CI pipelines.
- **Spotless & Gradle**: Complements existing Kotlin compiler and linter checks.

## Technical Requirements & Deliverables

1. **Error Hygiene Scanner (`tools/qa/check_error_hygiene.py`)**:
   - Scans all Kotlin files under `app/` and `libs/` for:
     - Deliberate generic throws: `throw RuntimeException(`, `throw Exception(`, `throw Throwable(`, `throw IllegalArgumentException(`.
     - Raw string numeric error codes: e.g. `"213201"` used in throw sites instead of catalog constant references.
     - Reflection and classpath discovery: `Class.forName`, `ClassLoader.getResource`, `Reflections`, `ServiceLoader.load` in error handlers.
     - Unsafe generic catch blocks: `catch (e: Throwable)` outside reviewed messaging/reactor root wrappers.
     - Direct ProblemDetails instantiation outside adapter packages.
2. **Quarantine Allowlist (`tools/qa/error_hygiene_allowlist.yaml`)**:
   - Tracks the 181 audited legacy throw sites and 4 messaging catch sites awaiting migration in Phase 4.
   - Each entry contains: `file`, `line`, `violation_type`, `owner_task` (e.g. `ERRC-19`), `expires_by_task`.
3. **Unit Tests for Scanner (`tests/tools/test_check_error_hygiene.py`)**:
   - Comprehensive test suite proving scanner catches all banned patterns on mock code files and approves compliant code.
4. **Makefile Target**:
   - Add `make error-hygiene` and wire into `make check`.

## Acceptance Criteria

1. `tools/qa/check_error_hygiene.py` passes `mypy --strict` with zero typing errors.
2. Unit tests verify scanner detects prohibited patterns and honors valid allowlist entries.
3. Running `make error-hygiene` on current repository state passes cleanly using the baseline allowlist.
4. Adding an un-allowlisted `throw RuntimeException("test")` immediately fails the scan with non-zero exit code.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
uv run --frozen --no-build mypy tools/qa tests/tools/test_check_error_hygiene.py
uv run --frozen --no-build python -m unittest tests/tools/test_check_error_hygiene.py
uv run python tools/qa/check_error_hygiene.py
git diff --check
```

## Evidence Expectations

- Clean mypy and unittest execution outputs.
- Verification output from `check_error_hygiene.py` auditing the repository against the baseline allowlist.

## Rollout & Rollback Strategy

- Tooling and CI gate addition.
- Prevents introduction of new violations during migration.
- Rollback: Adjust allowlist or rules if false positives occur on valid constructs.

## Implementation Notes and Evidence

- Added the typed `tools/qa/check_error_hygiene.py` scanner for generic throws,
  raw six-digit literals at throw sites, reflection/classpath discovery,
  `catch (Throwable)`, and direct Problem Details construction.
- Added an explicit 23-entry migration allowlist with owner and expiry task
  metadata. The scanner fails on both newly discovered violations and stale
  allowlist entries, preventing debt from being silently ratcheted forward.
- Added three unit tests covering every forbidden pattern, compliant catalog
  references, and baseline allowlist structure. Wired `error-hygiene` into the
  Makefile and the aggregate `check` target.
- Validation passed on 2026-10-07:
  `uv run --frozen --no-build mypy tools/qa tests/tools/test_check_error_hygiene.py`,
  `uv run --frozen --no-build python -m unittest tests/tools/test_check_error_hygiene.py`,
  `uv run python tools/qa/check_error_hygiene.py`,
  `mingw32-make error-hygiene`, and `git diff --check`.
