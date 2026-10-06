# DOC-18: Reconcile current API operation and error contracts

## Objective

Update GraphQL operation mapping and REST operation metadata for implemented
behavior, authentication, validation, and common problem responses. Define
ACC-05 ordering/duplicate semantics and NOT-09 repeated-read semantics.

## Deliverables & Decisions

1. **GraphQL Operation Mapping (`contracts/graphql/operation-mapping.md`)**:
   - Reconciled all implemented queries (`me`, `groups`, `group`, `settlementSuggestions`), mutations (`createGroup`, `updateGroup`, `createExpense`, `recordRepayment`), and subscriptions (`groupChanged`).
   - Documented `extensions.code` error code preservation and `extensions.requestId` correlation.
   - Documented query depth, page size, aliases, and subscription fanout bounds.

2. **Accounts OpenAPI Contract (`contracts/rest/accounts.openapi.json`)**:
   - Documented `/profiles/batch` (ACC-05) duplicate and non-existent ID semantics: duplicate requested IDs are deduplicated and non-existent IDs are omitted without failing the request.

3. **Notifications OpenAPI Contract (`contracts/rest/notifications.openapi.json`)**:
   - Documented `/inbox/{notificationId}/read` (NOT-09) repeated-read semantics: repeated calls on already-read notifications are idempotent and succeed with 204 No Content.

4. **API Implementation Status (`docs/api/implementation-status.md`)**:
   - Updated documentation to reflect all current production slices across Accounts, Expense Core, Notifications, and BFF.

## Verification Evidence

- `python3 tools/contracts/validate.py`: All 6 JSON contracts, GraphQL declarations, and 117 task registry entries valid.
- `git diff --check`: Passed with zero whitespace, format, or trailing newline errors.
