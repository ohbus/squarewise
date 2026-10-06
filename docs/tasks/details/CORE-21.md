# CORE-21: Complete authorized persistent search and CSV transport

Expose the implemented search/export domain through authenticated group-scoped
REST endpoints backed by durable data, bounded pagination/export, and CSV formula
escaping. Depends on CORE-06 and CORE-13. Owns Expense Core search/export source,
tests, and REST contracts.

## Deliverables & Decisions

1. **REST Contracts & ApiEndpoints**:
   - Centralized search and export endpoint path constants under `ApiEndpoints.ExpenseCore.V1` in `libs/ids` (`/groups/{groupId}/search`, `/groups/{groupId}/export`).
   - Defined REST paths, parameters (`query`, `currency`, `category`, `cursor`, `limit`, `maxRows`), and response schemas in `contracts/rest/expense-core.openapi.json`.
   - Verified via `python3 tools/contracts/validate.py`.

2. **Persistence Adapter (`SearchStore.kt`, `JpaSearchStore.kt`, `InMemorySearchStore.kt`)**:
   - Defined domain interface `SearchStore` with `findSearchExpenses(groupId: UUID): List<SearchExpense>`.
   - Implemented `JpaSearchStore` querying active (non-deleted) expenses from `ExpenseRepository` and mapping categories to `ExpenseCategory`.
   - Implemented thread-safe `InMemorySearchStore` for unit and standalone testing.

3. **Controller & CSV Transport (`SearchController.kt`, `ExpenseSearch.kt`)**:
   - `SearchController` exposed at `GET /groups/{groupId}/search` and `GET /groups/{groupId}/export`.
   - Enforces authenticated group membership authorization (`groupStore.list(principal.name)`), returning 404 for non-members or non-existent groups.
   - Paginates and filters search results with cursor encoding and per-currency totals via `ExpenseSearch.page`.
   - Generates CSV exports with formula injection escaping (prefixing `=,+,-,@` with single quotes) via `ExpenseSearch.csv` and streams as `text/csv; charset=UTF-8` with Content-Disposition attachment header.

4. **Unit & Integration Tests**:
   - `SearchControllerTest`: Standalone MockMvc tests verifying member search, query/currency/category filtering, cursor pagination with totals, CSV export with Content-Disposition, formula injection escaping, and unauthorized non-member rejection (404).
   - `JpaSearchStoreTest`: SpringBootTest integration test verifying JPA querying against database, mapping categories, and filtering out deleted expenses.

## Owned Paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/search/`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/search/`
- `contracts/rest/expense-core.openapi.json`
- `libs/ids/src/main/kotlin/com/subhrodip/squarewise/ids/ApiEndpoints.kt`
- `docs/tasks/details/CORE-21.md`

## Verification Evidence

- `python3 tools/contracts/validate.py`: All 6 JSON contracts, GraphQL declarations, and task registry valid.
- `./gradlew :app:expense-core:test --tests "com.subhrodip.squarewise.expensecore.search.*" --no-daemon`: BUILD SUCCESSFUL.
- `make check`: All tests, Spotless formatting, coverage, and build checks passed cleanly.
