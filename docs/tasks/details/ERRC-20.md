# ERRC-20: Migrate Expense Core definitions and failures

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 4 — Bounded-context migration
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Migrate all production error throw sites, validation checks, database constraint translations, and transactional outbox error boundaries in Expense Core (`app/expense-core`) to use strongly-typed definitions from `ExpenseErrors`. Eliminate all 87 legacy `ApplicationException(ErrorCode...)` call sites, fix semantic status inconsistencies, maintain ACID financial transaction guarantees, and support additive API v1 responses.

## Dependencies

- Preceding: [`ERRC-15`](ERRC-15.md), [`ERRC-18`](ERRC-18.md)
- Expense Core guide: [`docs/architecture/errors/expense-core.md`](../../architecture/errors/expense-core.md)

## Owned Paths

- `docs/tasks/details/ERRC-20.md`
- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/`
- `tools/qa/error_hygiene_allowlist.yaml` (prune migrated Expense Core entries)

## Architecture & Design Patterns

- **Domain-Driven Design (Financial Invariant Protection)**: Domain 2 errors strictly represent Expense Core modules: `21xxxx` Groups, `22xxxx` Membership, `23xxxx` Expenses, `24xxxx` Settlements, `25xxxx` Recurrence, `26xxxx` Sync, `27xxxx` Search, `28xxxx` Outbox.
- **Repository Exception Translation Pattern**: Database-level constraint violations (unique group name, participant foreign key, optimistic lock) are translated in Repository/Store implementations into semantic domain exceptions before crossing into application services.
- **Transactional Outbox & Unit of Work**: Financial ledger postings, sync mutations, audit events, and outbox records share a single ACID transaction. Failures during posting roll back cleanly and emit domain-governed error responses.
- **Strict SOLID File Separation**: Every new exception, translation mapper, and entity error adapter is placed in its own dedicated file.

## Common Libraries & Framework Integration

- **`libs/errors`**: `ExpenseErrors`, `SquarewiseException`, `GlobalErrorAdvice`.
- **`libs/db`**: JPA transaction rollback and database error code translation.
- **`libs/ids`**: `ApiEndpoints` and entity UUID validation.

## Technical Requirements & Deliverables

1. **Replace 87 Production `ApplicationException` Usages**:
   - Groups: `GROUP_NOT_FOUND` (`213201`), `GROUP_NAME_CONFLICT` (`213301`), `GROUP_ARCHIVED` (`214401`).
   - Membership: `MEMBERSHIP_NOT_FOUND` (`223201`), `INVITATION_EXPIRED` (`223401`), `CANNOT_REMOVE_CREATOR` (`225501`).
   - Expenses: `EXPENSE_NOT_FOUND` (`233201`), `ALLOCATION_SUM_MISMATCH` (`235501`), `NEGATIVE_SPLIT_AMOUNT` (`231101`).
   - Settlements: `SETTLEMENT_ALREADY_RECORDED` (`243301`), `INVALID_SETTLEMENT_PARTICIPANTS` (`245501`).
   - Recurrence: `SCHEDULE_NOT_FOUND` (`253201`), `INVALID_CRON_EXPRESSION` (`251101`).
   - Sync: `INVALID_SYNC_CURSOR` (`261101`), `SYNC_CURSOR_EXPIRED` (`261401`).
2. **Reconcile Semantic Discrepancies**:
   - Fix expense deletion reporting authentication error: map correctly to `EXPENSE_NOT_FOUND` (404) or `EXPENSE_MUTATION_FORBIDDEN` (403).
   - Reconcile archived group error mapping across endpoints to consistently return 409 Conflict.
3. **Prune Error Hygiene Allowlist**:
   - Remove all 87 Expense Core entries from `tools/qa/error_hygiene_allowlist.yaml`.
4. **Service Test Suite Updates**:
   - Update integration, controller, and repository tests to assert additive error fields.

## Acceptance Criteria

1. Zero references to legacy `ErrorCode` or generic `ApplicationException` remain in `app/expense-core/src/main/`.
2. All 87 Expense Core error call sites use `ExpenseErrors` constants.
3. Financial ledger transactions, rounding checks, and outbox publications roll back correctly upon domain failure.
4. Complete Expense Core test suite passes with 100% test success and zero regression.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :app:expense-core:test :app:expense-core:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/qa/check_error_hygiene.py
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JaCoCo test report showing full coverage across Expense Core domain slices.
- Verification log from `check_error_hygiene.py` demonstrating zero residual Expense Core debt.

## Rollout & Rollback Strategy

- Deployed to Expense Core service.
- Additive problem details ensure zero downtime and complete client compatibility.
- Rollback: Standard Git revert of service branch if regressions occur.

## Implementation Notes and Evidence

- Replaced all Expense Core production references to legacy `ApplicationException`
  and `ErrorCode` with catalog-backed `ExpenseDomainException` and static
  `ExpenseErrors` definitions across groups, expenses, settlements, recurrence,
  search, sync, and category validation paths.
- Preserved the existing exception compatibility type and message/cause behavior
  for v1 callers while transport mappers now read governed catalog identity and
  safe detail. ACID transaction annotations and financial mutation ordering were
  not changed.
- Added deterministic generator support for the temporary symbolic compatibility
  aliases; these aliases are explicitly migration scaffolding and are owned for
  removal by ERRC-30 after the compatibility window.
- The complete Expense Core suite passed 296 tests with 4 intentional skips on
  2026-10-07. Error hygiene passed with no Expense Core entries, the contract
  validator passed, and the declared JaCoCo report completed successfully.
