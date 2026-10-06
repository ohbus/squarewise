# BFF-03: GraphQL BFF query and mutation resolvers

Implement GraphQL resolvers in `app/bff` connecting GraphQL queries and mutations to the Accounts and Expense Core REST endpoints via reactive WebClient gateways.

## Acceptance criteria

- `Query.me`: Calls Accounts service `GET /accounts/v1/me` forwarding the authenticated user's bearer token, resolving `Profile`.
- `Query.group(id: ID!)`: Calls Expense Core to fetch group details, balances (`GET /expense-core/v1/groups/{id}/balances`), and expenses (`GET /expense-core/v1/groups/{id}/expenses`), resolving `Group`.
- `Mutation.createExpense(groupId: ID!, input: CreateExpenseInput!, idempotencyKey: String!)`: Forwards create expense payload to Expense Core `POST /expense-core/v1/groups/{groupId}/expenses` with `Idempotency-Key` header, resolving `Expense`.
- `Mutation.recordRepayment(input: RepaymentInput!)`: Forwards repayment/settlement to Expense Core `POST /expense-core/v1/groups/{groupId}/settlements`, resolving `Settlement`.
- Downstream errors preserve problem codes and propagate appropriately without failing unhandled.

## Implementation details

- **Gateways**:
  - `AccountsGateway`: Added `getMe(bearer: String?): Mono<BffProfile>` targeting `/accounts/v1/me`.
  - `ExpenseCoreGateway`:
    - `getGroup(groupId: String, bearer: String?): Mono<BffGroup>`: Zip calls to `GET /groups/{groupId}`, `GET /groups/{groupId}/balances`, and `GET /groups/{groupId}/expenses`.
    - `createExpense(groupId: String, input: CreateExpenseInput, idempotencyKey: String, bearer: String?): Mono<BffExpense>`.
    - `recordRepayment(groupId: String, input: RepaymentInput, bearer: String?): Mono<BffSettlement>`.
- **GraphQL Controllers**:
  - `ProfileGraphqlController`: `@QueryMapping fun me(principal: Principal?): Mono<BffProfile>`.
  - `GroupGraphqlController`:
    - `@QueryMapping fun groups(principal: Principal?): Mono<List<BffGroup>>`.
    - `@QueryMapping fun group(@Argument id: String, principal: Principal?): Mono<BffGroup>`.
    - `@MutationMapping fun createGroup(@Argument input: CreateGroupInput, principal: Principal?): Mono<BffGroup>`.
    - `@MutationMapping fun createExpense(@Argument groupId: String, @Argument input: CreateExpenseInput, @Argument idempotencyKey: String, principal: Principal?): Mono<BffExpense>`.
    - `@MutationMapping fun recordRepayment(@Argument input: RepaymentInput, principal: Principal?): Mono<BffSettlement>`.
- **Expense Core Endpoint**:
  - Added `GET /expense-core/v1/groups/{groupId}` endpoint in `GroupController.kt` and updated OpenAPI schema.
- **Contract & Schema Updates**:
  - Added optional `groupId: ID` to `input RepaymentInput` in `contracts/graphql/30-inputs.graphqls` to allow client specification of group context for repayments.
  - Added `GET /groups/{groupId}` to `contracts/rest/expense-core.openapi.json`.
- **Unit & Integration Tests**:
  - Added `ProfileGraphqlControllerTest` verifying `me` query resolver.
  - Expanded `GroupGraphqlControllerTest` verifying `groups`, `group(id)`, `createGroup`, `createExpense`, and `recordRepayment`.
  - Expanded `RestGatewayTest` verifying DTO properties and exception preservation.
  - Added `gets group by id for member and returns 404 for non-existent or non-member` in `GroupControllerTest`.

## Verification evidence

- Contract validation:
  ```
  python3 tools/contracts/validate.py
  # valid JSON: contracts/rest/accounts.openapi.json
  # valid JSON: contracts/rest/expense-core.openapi.json
  # valid GraphQL declaration set: 4 files in contracts/graphql/
  # valid task registry: 59 tasks
  ```
- Module tests:
  ```
  ./gradlew :app:bff:test --no-daemon
  # BUILD SUCCESSFUL
  ./gradlew :app:expense-core:test --no-daemon
  # BUILD SUCCESSFUL (69 tests)
  ```
- Full test suite re-execution:
  ```
  ./gradlew test --rerun --no-daemon
  # 30 actionable tasks: 5 executed, 25 up-to-date, BUILD SUCCESSFUL
  ```
