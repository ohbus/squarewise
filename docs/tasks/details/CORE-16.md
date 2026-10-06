# CORE-16: Implement debt simplification and settlement suggestions engine

## Objective

Calculate suggested settlement payments for a group to settle all outstanding balances with minimal cash transfers.

## Acceptance criteria

- `GET /expense-core/v1/groups/{groupId}/settlements/suggestions` computes net balances per participant per currency and produces a minimal list of payments (`fromParticipantId`, `toParticipantId`, `amountMinor`, `currency`).
- Algorithm uses standard greedy balance matching (largest debtor pays largest creditor in each currency).
- Zero-balance participants produce no suggestions; balanced groups produce an empty list.
- Schema updated in `contracts/rest/expense-core.openapi.json`.
- Comprehensive unit tests in `SettlementSuggestionTest.kt` and `SettlementControllerTest.kt`.

## Owned paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/settlements/`
- `contracts/rest/expense-core.openapi.json`

## Validation commands

- `./gradlew :app:expense-core:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Implementation details

- **Engine (`SettlementSuggestion.kt`)**:
  - `SuggestedSettlement`: Domain model representing suggested settlement payment (`fromParticipantId`, `toParticipantId`, `amountMinor`, `currency`).
  - `SettlementSuggestionEngine`: Given `groupId`, retrieves net group balances via `ExpenseStore.balances(groupId)`, groups by currency, and executes greedy balance simplification:
    - Net balances per participant partitioned into debtors (`< 0`) and creditors (`> 0`), zero balances ignored.
    - Deterministic priority queues order debtors by balance ascending (largest debt first, tie-broken by UUID) and creditors by balance descending (largest credit first, tie-broken by UUID).
    - Iteratively matches largest debtor with largest creditor for `min(-debtorBalance, creditorBalance)` transfer until all debts in that currency are cleared.
    - Currencies processed independently in sorted order preserving multi-currency isolation.
- **Service & Controller (`SettlementService.kt`, `SettlementController.kt`)**:
  - Added `suggestions(groupId)` to `SettlementService` delegating to `SettlementSuggestionEngine`.
  - Added `GET /expense-core/v1/groups/{groupId}/settlements/suggestions` (`getSuggestions`) returning `List<SuggestedSettlement>`.
- **Contract (`contracts/rest/expense-core.openapi.json`)**:
  - Registered path `/groups/{groupId}/settlements/suggestions` with `getSettlementSuggestions` GET operation returning `SuggestedSettlement` array.
  - Added `SuggestedSettlement` schema definition with required fields (`fromParticipantId`, `toParticipantId`, `amountMinor`, `currency`).
- **Tests**:
  - `SettlementSuggestionTest.kt`: Unit tests covering 2-person split, 3-person cycle (A owes B, B owes C simplified directly bypassing B), multi-currency isolation, zero balance handling, empty balances, greedy multi-debtor/multi-creditor matching, and integration with `ExpenseStore`.
  - `SettlementControllerTest.kt`: MockMvc tests for `GET /expense-core/v1/groups/{groupId}/settlements/suggestions` asserting 200 OK with suggestions payload and empty list for balanced groups.

## Verification evidence

- `./gradlew :app:expense-core:test --no-daemon`: 88 tests completed, 0 failures.
- `python3 tools/contracts/validate.py`: all contract schemas valid (valid JSON and valid task registry).
- `git diff --check`: passed cleanly.
