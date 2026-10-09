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
