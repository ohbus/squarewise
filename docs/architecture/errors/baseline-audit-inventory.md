# Production Error Baseline & Characterization Inventory

Status: Frozen baseline under `ERRC-02` (Phase 0).

## 1. Overview & Audit Scope

This document captures the characterization snapshot of all error throw sites, catches, boundary handlers,
HTTP status mappings, response shapes, and security/messaging pathways across the Squarewise codebase.
No production logic is altered in this task.

### Call Site Summary by Component

| Service / Library | `ApplicationException` | `throw` | `require` | `catch` | `@ExceptionHandler` |
|---|:---:|:---:|:---:|:---:|:---:|
| **Accounts** (`app/accounts`) | 32 | 43 | 50 | 10 | 1 |
| **Expense Core** (`app/expense-core`) | 87 | 89 | 72 | 16 | 0 |
| **Notifications** (`app/notifications`) | 7 | 35 | 18 | 21 | 0 |
| **GraphQL BFF** (`app/bff`) | 4 | 10 | 15 | 5 | 0 |
| **Database Library** (`libs/db`) | 0 | 3 | 18 | 2 | 0 |
| **Error Library** (`libs/errors`) | 1 | 0 | 0 | 0 | 9 |
| **Security Library** (`libs/security`) | 0 | 4 | 24 | 3 | 0 |
| **Observability Library** (`libs/observability`) | 0 | 0 | 1 | 0 | 0 |
| **IDs Library** (`libs/ids`) | 0 | 0 | 0 | 0 | 0 |
| **TOTAL** | **131** | **184** | **198** | **57** | **10** |

---

## 2. Complete Inventory of All 131 `ApplicationException` Call Sites

| # | File Path & Line | Legacy Code | Code Snippet / Detail | Planned 6-Digit Code | Planned Error Name |
|---|---|:---:|---|:---:|---|
| 1 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/AuthController.kt:129` | `ERR_11` | `throw ApplicationException(ErrorCode.ERR_11, "Refresh rate limit exceeded")` | `127801` | `AUTH_RATE_LIMITED` |
| 2 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/AuthController.kt:132` | `ERR_11` | `throw ApplicationException(ErrorCode.ERR_11, "Rate-limit service unavailable"...` | `127801` | `AUTH_RATE_LIMITED` |
| 3 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/AuthController.kt:154` | `ERR_03` | `throw ApplicationException(ErrorCode.ERR_03, "Authentication required")` | `127102` | `AUTH_CREDENTIAL_INVALID` |
| 4 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/login/LoginStartService.kt:44` | `ERR_11` | `throw ApplicationException(ErrorCode.ERR_11, "Rate-limit service unavailable"...` | `127801` | `AUTH_RATE_LIMITED` |
| 5 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/login/LoginStartService.kt:48` | `ERR_11` | `throw ApplicationException(ErrorCode.ERR_11, "Login rate limit exceeded")` | `127801` | `AUTH_RATE_LIMITED` |
| 6 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/login/LoginVerificationService.kt:67` | `ERR_11` | `throw ApplicationException(ErrorCode.ERR_11, "Login verification rate limit e...` | `127801` | `AUTH_RATE_LIMITED` |
| 7 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/login/LoginVerificationService.kt:70` | `ERR_11` | `throw ApplicationException(ErrorCode.ERR_11, "Rate-limit service unavailable"...` | `127801` | `AUTH_RATE_LIMITED` |
| 8 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/login/LoginVerificationService.kt:75` | `ERR_03` | `throw ApplicationException(ErrorCode.ERR_03, "Authentication required")` | `127102` | `AUTH_CREDENTIAL_INVALID` |
| 9 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/session/TokenSessionService.kt:118` | `ERR_03` | `throw ApplicationException(ErrorCode.ERR_03, "Authentication required")` | `127102` | `AUTH_CREDENTIAL_INVALID` |
| 10 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/session/TokenSessionService.kt:123` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "Authentication required")` | `127102` | `AUTH_CREDENTIAL_INVALID` |
| 11 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/session/TokenSessionService.kt:130` | `ERR_03` | `throw ApplicationException(ErrorCode.ERR_03, "Authentication required")` | `127102` | `AUTH_CREDENTIAL_INVALID` |
| 12 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/session/TokenSessionService.kt:139` | `ERR_03` | `throw ApplicationException(ErrorCode.ERR_03, "Authentication required")` | `127102` | `AUTH_CREDENTIAL_INVALID` |
| 13 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/session/TokenSessionService.kt:148` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "Authentication required")` | `127102` | `AUTH_CREDENTIAL_INVALID` |
| 14 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/session/TokenSessionService.kt:150` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "Authentication required")` | `127102` | `AUTH_CREDENTIAL_INVALID` |
| 15 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/session/TokenSessionService.kt:154` | `ERR_03` | `throw ApplicationException(ErrorCode.ERR_03, "Authentication required")` | `127102` | `AUTH_CREDENTIAL_INVALID` |
| 16 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/session/TokenSessionService.kt:159` | `ERR_03` | `throw ApplicationException(ErrorCode.ERR_03, "Authentication required")` | `127102` | `AUTH_CREDENTIAL_INVALID` |
| 17 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/session/TokenSessionService.kt:183` | `ERR_03` | `throw ApplicationException(ErrorCode.ERR_03, "Authentication required")` | `127102` | `AUTH_CREDENTIAL_INVALID` |
| 18 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfileController.kt:46` | `ERR_03` | `profiles.get(principal.name) ?: throw ApplicationException(ErrorCode.ERR_03, ...` | `117101` | `PROFILE_ACCESS_UNAUTHORIZED` |
| 19 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfileController.kt:119` | `ERR_03` | `val subject = principal?.name ?: throw ApplicationException(ErrorCode.ERR_03,...` | `117101` | `PROFILE_ACCESS_UNAUTHORIZED` |
| 20 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfileController.kt:126` | `ERR_03` | `val subject = principal?.name ?: throw ApplicationException(ErrorCode.ERR_03,...` | `117101` | `PROFILE_ACCESS_UNAUTHORIZED` |
| 21 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfileController.kt:156` | `ERR_03` | `val callerSubject = principal?.name ?: throw ApplicationException(ErrorCode.E...` | `117101` | `PROFILE_ACCESS_UNAUTHORIZED` |
| 22 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfileController.kt:157` | `ERR_03` | `val callerProfile = profiles.get(callerSubject) ?: throw ApplicationException...` | `117101` | `PROFILE_ACCESS_UNAUTHORIZED` |
| 23 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfileController.kt:159` | `ERR_04` | `throw ApplicationException(ErrorCode.ERR_04, "Access denied to foreign profile")` | `113199` | `PROFILE_UNCLASSIFIED` |
| 24 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfileController.kt:164` | `ERR_05` | `profiles.findById(accountId) ?: throw ApplicationException(ErrorCode.ERR_05, ...` | `113201` | `PROFILE_NOT_FOUND` |
| 25 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfileController.kt:193` | `ERR_03` | `val callerSubject = principal?.name ?: throw ApplicationException(ErrorCode.E...` | `117101` | `PROFILE_ACCESS_UNAUTHORIZED` |
| 26 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfileController.kt:194` | `ERR_03` | `val callerProfile = profiles.get(callerSubject) ?: throw ApplicationException...` | `117101` | `PROFILE_ACCESS_UNAUTHORIZED` |
| 27 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfileController.kt:197` | `ERR_04` | `throw ApplicationException(ErrorCode.ERR_04, "Batch profile lookup requires i...` | `113199` | `PROFILE_UNCLASSIFIED` |
| 28 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfilePatchRequest.kt:18` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "At least one profile field is r...` | `113101` | `INVALID_PROFILE_DATA` |
| 29 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/persistence/JpaProfileStore.kt:78` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "Profile not found")` | `117101` | `PROFILE_ACCESS_UNAUTHORIZED` |
| 30 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/persistence/JpaProfileStore.kt:92` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "Profile not found")` | `117101` | `PROFILE_ACCESS_UNAUTHORIZED` |
| 31 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/service/ProfileRules.kt:13` | `ERR_03` | `throw ApplicationException(ErrorCode.ERR_03, "Authenticated subject is invalid")` | `117101` | `PROFILE_ACCESS_UNAUTHORIZED` |
| 32 | `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/service/ProfileRules.kt:22` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "timezone must be a valid IANA z...` | `113101` | `INVALID_PROFILE_DATA` |
| 33 | `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/graphql/GroupGraphqlController.kt:49` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "groupId must not be blank")` | `411101` | `GRAPHQL_RESOLVER_ERROR` |
| 34 | `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/graphql/GroupGraphqlController.kt:53` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "authenticated subject is req...` | `411101` | `GRAPHQL_RESOLVER_ERROR` |
| 35 | `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/graphql/GroupGraphqlController.kt:117` | `ERR_02` | `?: return Mono.error(ApplicationException(ErrorCode.ERR_02, "groupId is requi...` | `411101` | `GRAPHQL_RESOLVER_ERROR` |
| 36 | `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/realtime/LiveUpdateFanout.kt:79` | `ERR_11` | `throw ApplicationException(ErrorCode.ERR_11, "subscription limit exceeded")` | `411199` | `BFF_GENERIC` |
| 37 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/categories/ExpenseCategory.kt:16` | `ERR_02` | `?: throw ApplicationException(ErrorCode.ERR_02, "Unknown expense category")` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 38 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/AllocationPreviewController.kt:25` | `ERR_02` | `?: throw ApplicationException(ErrorCode.ERR_02, "totalMinor must be a non-neg...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 39 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/AllocationPreviewController.kt:26` | `ERR_02` | `if (total < 0) throw ApplicationException(ErrorCode.ERR_02, "totalMinor must ...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 40 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/AllocationPreviewController.kt:30` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, error.message, error)` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 41 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:67` | `ERR_02` | `?: throw ApplicationException(ErrorCode.ERR_02, "payer amount.minor must be a...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 42 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:69` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "payer amount.minor must be posi...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 43 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:72` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "payer currency must match expen...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 44 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:77` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, e.message, e)` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 45 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:82` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "Sum of payer amounts ($payerSum...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 46 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:88` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, e.message, e)` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 47 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:148` | `ERR_02` | `?: throw ApplicationException(ErrorCode.ERR_02, "payer amount.minor must be a...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 48 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:150` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "payer amount.minor must be posi...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 49 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:153` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "payer currency must match expen...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 50 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:158` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, e.message, e)` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 51 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:163` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "Sum of payer amounts ($payerSum...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 52 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:169` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, e.message, e)` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 53 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:238` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "limit must be between 1 and 100")` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 54 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:267` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "Authenticated subject is req...` | `237501` | `EXPENSE_FORBIDDEN` |
| 55 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:269` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 56 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:272` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Group $groupId is archived")` | `233301` | `EXPENSE_CONFLICT` |
| 57 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:278` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "Idempotency-Key is too long")` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 58 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:289` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "category is too long")` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 59 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/api/ExpenseController.kt:292` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "participant count exceeds the m...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 60 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/domain/AllocationCalculator.kt:65` | `ERR_02` | `else -> throw ApplicationException(ErrorCode.ERR_02, "Unsupported allocation ...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 61 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:67` | `ERR_05` | `?: throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 62 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:71` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Group is archived")` | `233301` | `EXPENSE_CONFLICT` |
| 63 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:78` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Idempotency key was already use...` | `233301` | `EXPENSE_CONFLICT` |
| 64 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:81` | `ERR_06` | `ApplicationException(ErrorCode.ERR_06, "Idempotency record has no committed e...` | `233301` | `EXPENSE_CONFLICT` |
| 65 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:95` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Expense already exists with dif...` | `233301` | `EXPENSE_CONFLICT` |
| 66 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:215` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 67 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:226` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "Participant IDs must be unique ...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 68 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:233` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Every financial participant mus...` | `233201` | `EXPENSE_NOT_FOUND` |
| 69 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:251` | `ERR_05` | `?: throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 70 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:255` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Group is archived")` | `233301` | `EXPENSE_CONFLICT` |
| 71 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:259` | `ERR_05` | `?: throw ApplicationException(ErrorCode.ERR_05, "Expense $expenseId not found...` | `233201` | `EXPENSE_NOT_FOUND` |
| 72 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:262` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Expense $expenseId has been del...` | `233201` | `EXPENSE_NOT_FOUND` |
| 73 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:266` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Stale version: expected ${entit...` | `233301` | `EXPENSE_CONFLICT` |
| 74 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:406` | `ERR_05` | `?: throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 75 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:409` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Group is archived")` | `233301` | `EXPENSE_CONFLICT` |
| 76 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:413` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "Expense $expenseId not found...` | `237501` | `EXPENSE_FORBIDDEN` |
| 77 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:416` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Expense $expenseId has already ...` | `233201` | `EXPENSE_NOT_FOUND` |
| 78 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt:420` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Stale version: expected ${entit...` | `233301` | `EXPENSE_CONFLICT` |
| 79 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/service/ExpenseValidator.kt:19` | `ERR_02` | `?: throw ApplicationException(ErrorCode.ERR_02, "$fieldName must be a valid i...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 80 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/service/ExpenseValidator.kt:21` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "$fieldName must be greater than...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 81 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/service/ExpenseValidator.kt:31` | `ERR_02` | `?: throw ApplicationException(ErrorCode.ERR_02, "$fieldPrefix amount.minor mu...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 82 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/service/ExpenseValidator.kt:33` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "$fieldPrefix amount.minor must ...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 83 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/service/ExpenseValidator.kt:36` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "$fieldPrefix currency must matc...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 84 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/api/GroupController.kt:35` | `ERR_05` | `?: throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `213201` | `GROUP_NOT_FOUND` |
| 85 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/api/GroupController.kt:44` | `ERR_06` | `if (fault == ApiEndpoints.Headers.ACCEPTANCE_FAULT_ROLLBACK) throw Applicatio...` | `213301` | `GROUP_NAME_CONFLICT` |
| 86 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/api/GroupController.kt:76` | `ERR_08` | `if (fault == ApiEndpoints.Bff.ACCEPTANCE_FAULT_FANOUT) throw ApplicationExcep...` | `213199` | `GROUP_UNCLASSIFIED` |
| 87 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/persistence/store/JpaGroupStore.kt:113` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Group is already archived")` | `213301` | `GROUP_NAME_CONFLICT` |
| 88 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/persistence/store/JpaGroupStore.kt:176` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Member is already removed")` | `213301` | `GROUP_NAME_CONFLICT` |
| 89 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/persistence/store/JpaGroupStore.kt:216` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Placeholder not found or alread...` | `213301` | `GROUP_NAME_CONFLICT` |
| 90 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/persistence/store/JpaGroupStore.kt:330` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Group is archived")` | `213301` | `GROUP_NAME_CONFLICT` |
| 91 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/persistence/store/JpaGroupStore.kt:373` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, message)` | `213301` | `GROUP_NAME_CONFLICT` |
| 92 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/persistence/store/JpaGroupStore.kt:376` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Group not found")` | `213201` | `GROUP_NOT_FOUND` |
| 93 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/api/RecurringExpenseController.kt:93` | `ERR_05` | `?: throw ApplicationException(ErrorCode.ERR_05, "Schedule $scheduleId not fou...` | `233201` | `EXPENSE_NOT_FOUND` |
| 94 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/api/RecurringExpenseController.kt:95` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Schedule $scheduleId not found ...` | `233201` | `EXPENSE_NOT_FOUND` |
| 95 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/api/RecurringExpenseController.kt:146` | `ERR_05` | `?: throw ApplicationException(ErrorCode.ERR_05, "Schedule $scheduleId not fou...` | `233201` | `EXPENSE_NOT_FOUND` |
| 96 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/api/RecurringExpenseController.kt:148` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Schedule $scheduleId not found ...` | `233201` | `EXPENSE_NOT_FOUND` |
| 97 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/api/RecurringExpenseController.kt:161` | `ERR_05` | `?: throw ApplicationException(ErrorCode.ERR_05, "Schedule $scheduleId not fou...` | `233201` | `EXPENSE_NOT_FOUND` |
| 98 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/api/RecurringExpenseController.kt:163` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Schedule $scheduleId not found ...` | `233201` | `EXPENSE_NOT_FOUND` |
| 99 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/api/RecurringExpenseController.kt:170` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 100 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/api/RecurringExpenseController.kt:173` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "Authenticated subject is req...` | `237501` | `EXPENSE_FORBIDDEN` |
| 101 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/api/RecurringExpenseController.kt:175` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 102 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/api/RecurringExpenseController.kt:181` | `ERR_02` | `?: throw ApplicationException(ErrorCode.ERR_02, "amount.minor must be a valid...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 103 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/api/RecurringExpenseController.kt:183` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "amount.minor must be positive")` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 104 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/service/RecurringExpenseService.kt:80` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 105 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/service/RecurringExpenseService.kt:114` | `ERR_05` | `ApplicationException(ErrorCode.ERR_05, "Schedule $scheduleId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 106 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/service/RecurringExpenseService.kt:117` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Schedule $scheduleId not in gro...` | `233201` | `EXPENSE_NOT_FOUND` |
| 107 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/service/RecurringExpenseService.kt:167` | `ERR_05` | `ApplicationException(ErrorCode.ERR_05, "Schedule $scheduleId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 108 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/service/RecurringExpenseService.kt:176` | `ERR_05` | `ApplicationException(ErrorCode.ERR_05, "Schedule $scheduleId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 109 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/search/api/SearchController.kt:88` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, ex.message, ex)` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 110 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/search/api/SearchController.kt:137` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, ex.message, ex)` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 111 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/search/api/SearchController.kt:150` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 112 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/search/model/ExpenseSearch.kt:126` | `ERR_02` | `}.getOrElse { throw ApplicationException(ErrorCode.ERR_02, "Invalid search cu...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 113 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/search/persistence/SearchStore.kt:28` | `ERR_02` | `}.getOrElse { throw ApplicationException(ErrorCode.ERR_02, "Invalid search cu...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 114 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/api/SettlementController.kt:53` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "Authenticated subject is req...` | `237501` | `EXPENSE_FORBIDDEN` |
| 115 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/api/SettlementController.kt:55` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 116 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/persistence/JpaSettlementStore.kt:48` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Idempotency key was already use...` | `233301` | `EXPENSE_CONFLICT` |
| 117 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/persistence/JpaSettlementStore.kt:89` | `ERR_05` | `?: throw ApplicationException(ErrorCode.ERR_05, "Settlement not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 118 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/persistence/JpaSettlementStore.kt:111` | `ERR_06` | `throw ApplicationException(ErrorCode.ERR_06, "Group is archived")` | `233301` | `EXPENSE_CONFLICT` |
| 119 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/persistence/JpaSettlementStore.kt:113` | `ERR_05` | `} ?: throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 120 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/sync/api/SyncController.kt:31` | `ERR_03` | `if (principal.name.isBlank()) throw ApplicationException(ErrorCode.ERR_03, "A...` | `237501` | `EXPENSE_FORBIDDEN` |
| 121 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/sync/api/SyncController.kt:33` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Group $groupId not found")` | `233201` | `EXPENSE_NOT_FOUND` |
| 122 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/sync/api/SyncController.kt:35` | `ERR_02` | `if (limit !in 1..100) throw ApplicationException(ErrorCode.ERR_02, "limit mus...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 123 | `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/sync/api/SyncController.kt:39` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, exception.message ?: "Invalid sy...` | `231101` | `EXPENSE_ALLOCATION_INVALID` |
| 124 | `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/email/smtp/JavaMailSender.kt:28` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "At least one recipient must be ...` | `345701` | `EMAIL_DISPATCH_FAILED` |
| 125 | `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/inbox/api/InboxController.kt:30` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "authenticated subject is req...` | `317101` | `INBOX_UNAUTHORIZED` |
| 126 | `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/inbox/api/InboxController.kt:32` | `ERR_02` | `throw ApplicationException(ErrorCode.ERR_02, "limit must be between 1 and 100")` | `311101` | `NOTIFICATION_CURSOR_INVALID` |
| 127 | `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/inbox/api/InboxController.kt:42` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "authenticated subject is req...` | `317101` | `INBOX_UNAUTHORIZED` |
| 128 | `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/inbox/api/InboxController.kt:44` | `ERR_05` | `throw ApplicationException(ErrorCode.ERR_05, "Notification not found")` | `313201` | `NOTIFICATION_NOT_FOUND` |
| 129 | `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/inbox/service/NotificationInboxService.kt:33` | `ERR_02` | `private fun decodeCursor(cursor: String): Pair<Instant, UUID> = runCatching {...` | `311101` | `NOTIFICATION_CURSOR_INVALID` |
| 130 | `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/preferences/api/PreferenceController.kt:32` | `ERR_03` | `?: throw ApplicationException(ErrorCode.ERR_03, "authenticated subject is req...` | `331101` | `PREFERENCE_INVALID` |
| 131 | `libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/domain/ApplicationException.kt:7` | `UNKNOWN` | `class ApplicationException(` | `919901` | `ERROR_FRAMEWORK_INTERNAL` |

---

## 3. Boundary & Handler Audit

### 3.1 Spring MVC Controller Advice Handlers

All REST services inherit exception mapping from `com.subhrodip.squarewise.errors.http.GlobalErrorHandler` in `libs/errors`:

| Handled Exception Type | HTTP Status | Mapped Legacy Code | Response Title | Public Detail Behavior |
|---|:---:|:---:|---|---|
| `HttpMediaTypeNotAcceptableException` | 406 | None | Bodyless | Body omitted intentionally |
| `MethodArgumentNotValidException` | 400 | `ERR_02` | Request validation failed | Populates `violations` array |
| `HttpMessageNotReadableException` | 400 | `ERR_02` | Malformed request payload | Echoes root cause message (safe-sanitization target) |
| `ServletRequestBindingException` | 400 | `ERR_02` | Missing or invalid parameter | Echoes binding message |
| `MethodArgumentTypeMismatchException` | 400 | `ERR_02` | Type mismatch | Echoes parameter name and expected type |
| `OptimisticLockingFailureException` | 409 | `ERR_06` | Conflict | Echoes concurrency message |
| `IllegalArgumentException` | 400 | `ERR_02` | Request validation failed | Echoes exception message |
| `ApplicationException` | Dynamic | `ex.errorCode` | `ex.message ?: safeDetail` | Echoes exception message |
| `Exception` (Catch-All) | 500 | `ERR_01` | Internal server error | "An unexpected error occurred" (Safe fallback) |

In addition, Accounts `AuthenticationController` contains 1 dedicated handler for `LoginRateLimitExceededException` mapping to HTTP 429 (`ERR_11`).

### 3.2 Spring Security Filter Chain Boundaries

- **Servlet Stack** (`app/accounts`, `app/expense-core`, `app/notifications`):
  - Unauthenticated requests intercepted by Spring Security before reaching DispatcherServlet.
  - Handled via `BearerTokenAuthenticationEntryPoint`.
  - Emits HTTP 401 with `WWW-Authenticate: Bearer` header.
- **Reactive WebFlux Stack** (`app/bff`):
  - Unauthenticated requests intercepted by `ServerAuthenticationEntryPoint`.
  - Emits HTTP 401.

### 3.3 Messaging & Background Consumer Boundaries

- **RabbitMQ Consumers**:
  - `AuthEmailConsumer` (`app/notifications`): Listens on `auth.email.dispatch` queue. Catches `Exception`, retries with backoff up to attempt threshold, then routes to `dlx.squarewise`.
  - `GroupNotificationConsumer` (`app/notifications`): Listens on `expense.events` queue. Processes user inbox entries.
  - **Audit Note**: 4 broad `catch (Throwable)` sites exist in messaging listeners that must be migrated in Phase 3/4 to prevent swallowing fatal JVM errors (`OutOfMemoryError`, `ThreadDeath`).
- **Transactional Outbox Relay** (`app/expense-core`):
  - Polls `expense_outbox` table in local transaction.
  - Failures to publish to RabbitMQ leave record as `PENDING`, retried on next scheduler tick.

---

## 4. Known Semantic & Status Inconsistency Register

The following known inconsistencies exist in the baseline implementation and are cataloged for intentional resolution during the phased refactoring:

1. **Expense Deletion Status Discrepancy**:
   - Attempting to delete an expense when unauthorized currently reports an authentication error (`ERR_03`) in certain paths rather than 403 Forbidden or 404 Not Found.
2. **Profile Absence Multi-Status Mapping**:
   - Looking up a nonexistent profile returns 401 Unauthorized in some filter paths and 404 Not Found in service paths. Must be unified to anti-enumeration 404.
3. **Archived Group Status Conflict**:
   - Mutations against archived groups return 400 Bad Request on some endpoints and 409 Conflict on others. Standardized to 409 Conflict.
4. **Quota Limit vs Quota Store Outage**:
   - Rate limit quota exhaustion and Redis cache outage both historically shared `ERR_11` (429). Refactored so quota exhaustion returns 429 and store outage returns 503.

---

## 5. Golden-Master Response Shape Fixtures

Exact JSON problem bodies and header baselines are checked into `tests/fixtures/errors/baseline/`:

- `err_01_internal_error.json` (500 Internal Server Error)
- `err_02_validation_failed.json` (400 Bad Request)
- `err_02_field_violations.json` (400 Bad Request with field violations)
- `err_03_unauthenticated.json` (401 Unauthorized + WWW-Authenticate header)
- `err_04_forbidden.json` (403 Forbidden)
- `err_05_not_found.json` (404 Not Found)
- `err_06_conflict.json` (409 Conflict)
- `err_09_conflict.json` (409 Conflict)
- `err_10_validation_failed.json` (400 Bad Request)
- `err_11_rate_limited.json` (429 Too Many Requests + Retry-After: 60 header)
- `response_headers_baseline.json` (Standard and conditional HTTP headers contract)

This inventory is frozen and serves as the characterization baseline for Phase 1 through Phase 5.
