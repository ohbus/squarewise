# CORE-35: Streamline SettlementSuggestionEngine injection and eliminate legacy unauthenticated service overloads

## Objective

Refactor `SettlementService` and `SettlementController` to follow DRY, KISS, and SOLID dependency injection:
1. **Direct singleton injection**: Inject `SettlementSuggestionEngine` non-null directly into `SettlementService` and remove optional null fallback branching in `SettlementService.suggestions(groupId)`.
2. **Controller delegation cleanup**: Remove duplicate injection of `SettlementSuggestionEngine` from `SettlementController` and delegate `getSuggestions` solely through `SettlementService.suggestions(groupId)`.
3. **Consolidate service entry points**: Streamline `SettlementService.record(...)` by removing legacy unauthenticated overloads that bypass idempotency key tracking, standardizing on the canonical authenticated method.
4. **KDoc**: Add structured doc comments specifying intent, parameters, and invariants on public methods.

## Dependencies

- `CORE-34`

## Owned paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/service/SettlementService.kt`
- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/api/SettlementController.kt`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/settlements/SettlementServiceTest.kt`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/settlements/SettlementControllerTest.kt`
- `docs/tasks/details/CORE-35.md`

## Acceptance criteria

- `SettlementService` takes non-null `SettlementSuggestionEngine` in constructor and delegates `suggestions(groupId)` directly without nullable checks.
- `SettlementController` injects `SettlementService` only (removing `SettlementSuggestionEngine` constructor parameter) and delegates `getSuggestions` directly to `service.suggestions(groupId)`.
- `SettlementService.record` has a clean canonical method signature taking required parameters (`groupId`, `from`, `to`, `amountMinor`, `currency`, `actorSubject`, `idempotencyKey`).
- All settlement unit tests and web layer tests pass without regressions.
- `git diff --check` passes cleanly.

## Validation commands

- `./gradlew :app:expense-core:test --tests "com.subhrodip.squarewise.expensecore.settlements.SettlementServiceTest" --no-daemon`
- `./gradlew :app:expense-core:test --tests "com.subhrodip.squarewise.expensecore.settlements.SettlementControllerTest" --no-daemon`
- `./gradlew :app:expense-core:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Status

done

## Implementation notes

- Injected `SettlementSuggestionEngine` directly into `SettlementService` and removed duplicate controller injection.
- Updated service-level suggestion coverage to verify delegation through a non-empty result.
- Removed the unauthenticated `SettlementService.record` overloads and reconciled callers and tests to the canonical authenticated method.

## Verification evidence

- `SettlementServiceTest` and `SettlementControllerTest` cover authenticated recording and suggestion delegation.
- The canonical authenticated `record` API is now the only supported service entry point.
