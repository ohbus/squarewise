# BFF-06: Add group update mutation and member query resolvers in GraphQL BFF

## Objective

Support group renaming and member discovery in the GraphQL BFF.

## Acceptance criteria

- `contracts/graphql/10-roots.graphqls` and `contracts/graphql/20-domain-types.graphqls` include:
  - `type Member { membershipId: ID!, subject: String! }`
  - Field `members: [Member!]!` on `type Group`
  - Mutation `updateGroup(groupId: ID!, name: String!): Group!` in `type Mutation`
- `ExpenseCoreGateway` provides:
  - `updateGroup(groupId: String, name: String, bearer: String?): Mono<BffGroup>`
  - `listMembers(groupId: String, bearer: String?): Mono<List<BffMember>>`
- `GroupGraphqlController` implements:
  - `updateGroup` mutation and triggers live invalidation
- Populates members in the single `group` query. Bounded member resolution for
  the `groups` list remains required before completion.
- Unit tests verify GraphQL resolvers and gateway methods.

## Owned paths

- `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/`
- `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/`
- `contracts/graphql/10-roots.graphqls`, `contracts/graphql/20-domain-types.graphqls`

## Validation commands

- `./gradlew :app:bff:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Implementation details

- **Data classes**: Added `BffMember(membershipId: String, subject: String)` and `members: List<BffMember>` field to `BffGroup`.
- **GraphQL schema**: Added `type Member`, `members` field on `Group`, and `updateGroup` mutation.
- **ExpenseCoreGateway**:
  - `updateGroup`: PATCH to `/expense-core/v1/groups/{groupId}` with name payload.
  - `listMembers`: GET to `/expense-core/v1/groups/{groupId}/members` returning `Flux<BffMember>`.
  - `getGroup`: Extended to zip members alongside balances and expenses.
- **GroupGraphqlController**: Added `@MutationMapping updateGroup` with `emitInvalidation` on success.
- **Tests**: Updated `GroupGraphqlControllerTest` to verify members in group query response.

## Verification evidence

- `./gradlew :app:bff:test --no-daemon`: All tests passed.
- `python3 tools/contracts/validate.py`: All contracts validated cleanly.
- `git diff --check`: Clean.

## Completion evidence

The formerly bounded list resolution is complete through BFF-07: `groups`
resolves members with bounded sequential fanout, while upstream member errors
are propagated as stable BFF errors. Focused gateway, resolver, invalidation,
fanout, and error propagation tests cover the mutation and both group-query
shapes.
