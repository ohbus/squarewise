# BFF-04: Settlement suggestions GraphQL resolver

## Objective

Expose settlement suggestions through the GraphQL BFF to allow clients to query suggested payments for any group.

## Acceptance criteria

- `contracts/graphql/10-roots.graphqls` and `contracts/graphql/20-domain-types.graphqls` include:
  - `Query.settlementSuggestions(groupId: ID!): [SuggestedSettlement!]!`
  - `type SuggestedSettlement { fromParticipantId: ID!, toParticipantId: ID!, amount: Money! }`
- `ExpenseCoreGateway` fetches suggestions from Expense Core `GET /expense-core/v1/groups/{groupId}/settlements/suggestions`.
- `GroupGraphqlController` exposes `settlementSuggestions` query resolver.
- Unit tests verify GraphQL resolver behavior and gateway mapping.

## Owned paths

- `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/`
- `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/`
- `contracts/graphql/10-roots.graphqls`, `contracts/graphql/20-domain-types.graphqls`

## Implementation details

- **GraphQL Contract**:
  - In `contracts/graphql/10-roots.graphqls` and `contracts/graphql/20-domain-types.graphqls`:
    - Added `settlementSuggestions(groupId: ID!): [SuggestedSettlement!]!` to `type Query`.
    - Added `type SuggestedSettlement { fromParticipantId: ID!, toParticipantId: ID!, amount: Money! }`.
- **REST Gateway**:
  - In `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/RestGateway.kt`:
    - Added `data class BffSuggestedSettlement(val fromParticipantId: String, val toParticipantId: String, val amountMinor: Long, val currency: String)` with computed getter `val amount: BffMoney get() = BffMoney(currency, amountMinor.toString())`.
    - Added `fun getSettlementSuggestions(groupId: String, bearer: String?): Mono<List<BffSuggestedSettlement>>` to `ExpenseCoreGateway`, calling `GET /expense-core/v1/groups/{groupId}/settlements/suggestions` and parsing into `BffSuggestedSettlement` list.
- **GraphQL Resolver**:
  - In `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/GroupGraphqlController.kt`:
    - Added `@QueryMapping fun settlementSuggestions(@Argument groupId: String, principal: Principal?): Mono<List<BffSuggestedSettlement>> = gateway.getSettlementSuggestions(groupId, principal?.name)`.
- **Testing**:
  - In `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/GroupGraphqlControllerTest.kt`:
    - Added `resolves settlementSuggestions query` testing the GraphQL query resolver against mocked gateway response.
  - In `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/RestGatewayTest.kt`:
    - Added verification of `BffSuggestedSettlement` fields and amount formatting into `BffMoney`.

## Verification evidence

- Contract validation:
  ```
  python3 tools/contracts/validate.py
  # valid JSON: contracts/errors/problem.schema.json
  # valid JSON: contracts/events/envelope.schema.json
  # valid JSON: contracts/examples/expense-rounding.json
  # valid JSON: contracts/rest/accounts.openapi.json
  # valid JSON: contracts/rest/expense-core.openapi.json
  # valid JSON: contracts/rest/notifications.openapi.json
  # valid GraphQL declaration set: 4 files in contracts/graphql/
  # valid task registry: 67 tasks
  ```
- BFF unit test suite:
  ```
  ./gradlew :app:bff:test --no-daemon
  # 7 actionable tasks: 5 executed, 2 up-to-date
  # BUILD SUCCESSFUL
  ```
- Git diff whitespace check:
  ```
  git diff --check
  # Clean, no whitespace issues
  ```
