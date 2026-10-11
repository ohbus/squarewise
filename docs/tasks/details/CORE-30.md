# CORE-30: Financial arithmetic hardening and allocation duplicate validation

## Objective

Remediate allocation item deduplication and arithmetic integer overflow vulnerabilities (`AUD-03`, `AUD-05`) identified in the financial audit:
- In `AllocationCalculator.calculate`, add an explicit guard validating that `items.map { it.participantId }` contains strictly unique IDs across all allocation modes (`EXACT`, `PERCENT_BASIS_POINTS`, `WEIGHTED_SHARES`, and `EQUAL`), rejecting duplicates before map association.
- In `ExpenseValidator.validatePayers`, replace primitive `sum += pAmount` with checked arithmetic `sum = FinancialArithmetic.add(sum, pAmount)` to detect and prevent two's-complement 64-bit signed overflow.
- In `ExpenseValidator.validatePayers`, enforce participant uniqueness across the `payers` list to reject duplicate payer IDs early before database constraint violations.
- Provide comprehensive unit tests verifying duplicate allocation item rejection across all modes and integer overflow bounds testing.

## Design Patterns & Architectural Guidance

- **Guard Clause / Fail-Fast Pattern**: Enforce input validity (uniqueness, positivity, and bounds) as early as possible in domain and calculation methods before passing data to down-stream associative data structures or database repositories.
- **Safe Math / Checked Arithmetic Pattern**: Signed 64-bit financial operations must use `Math.addExact` and `Math.multiplyExact` encapsulated in `FinancialArithmetic`. Never allow primitive integer wraparound on financial sums.
- **RFC 9457 Problem Details Translation**: Domain validation failures for duplicate participants or overflow must translate cleanly into HTTP 422 Unprocessable Content / 400 Bad Request with field violations, rather than internal server errors (500).

## Dependencies

- `CORE-27`

## Owned paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/domain/AllocationCalculator.kt`
- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/service/ExpenseValidator.kt`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/expenses/AllocationCalculatorTest.kt`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/expenses/ExpenseValidatorTest.kt`
- `docs/tasks/details/CORE-30.md`

## Acceptance criteria

- Submitting duplicate `participantId` entries in `EXACT`, `PERCENT_BASIS_POINTS`, or `WEIGHTED_SHARES` throws `ExpenseDomainException(EXPENSE_REQUEST_INVALID)` with message stating participant IDs must be unique.
- Submitting payer amounts whose sum exceeds `Long.MAX_VALUE` throws `ExpenseInputException` / `EXPENSE_REQUEST_INVALID` without numeric wraparound.
- Submitting duplicate `participantId` entries in `payers` throws `EXPENSE_REQUEST_INVALID`.
- All existing tests in `AllocationCalculatorTest` pass without regression.

## Validation commands

- `./gradlew :app:expense-core:test --tests "com.subhrodip.squarewise.expensecore.expenses.*" --no-daemon`
- `make contracts`
- `git diff --check`

## Implementation Notes & Evidence

- **AUD-03 Remediation**: In `AllocationCalculator.calculate`, added strict uniqueness guard on `items.map { it.participantId }` before map association and mode dispatch. Rejects duplicate participant IDs across `EQUAL`, `EXACT`, `PERCENT_BASIS_POINTS`, and `WEIGHTED_SHARES` with `IllegalArgumentException("participant IDs must be unique")`, which `ExpenseController` wraps into `ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID)`.
- **AUD-05 Remediation**: In `ExpenseValidator.validatePayers`, enforced participant uniqueness across `payers` with `ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID)` and replaced raw addition with `FinancialArithmetic.add(sum, pAmount)` to catch `Long` two's complement overflow.
- **Evidence**:
  - `./gradlew :app:expense-core:test --tests "com.subhrodip.squarewise.expensecore.expenses.*" --no-daemon` passed cleanly.
  - `make contracts` verified valid JSON, valid GraphQL, 257 valid tasks, 45 operations, 99 six-digit error catalog records.
  - `git diff --check` reported zero trailing whitespace or format issues.
