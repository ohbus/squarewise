# CORE-07: Expense categories

Add categories to expense creation and retrieval using a stable default
taxonomy, validated identifiers, and an extension point for group custom
categories. Preserve existing cursor pagination, totals, and safe CSV export.

## Implementation increment

The MVP taxonomy is represented by `ExpenseCategory` with stable lowercase keys:
food, lodging, transport, entertainment, shopping, bills, health, and other.
Missing categories remain backward compatible by defaulting to `other`; unknown
keys are rejected. Search accepts an optional category filter and CSV exports
include the stable category key. Group-owned custom categories remain a future
extension and must use an immutable group-scoped identifier rather than changing
these built-in keys.

Validation: `python3 -m json.tool contracts/rest/expense-core.openapi.json` and
`./gradlew :app:expense-core:test --no-daemon`.
