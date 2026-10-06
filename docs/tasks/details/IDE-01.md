# IDE-01: Resolve IntelliJ GraphQL schema detection and eliminate unnecessary constructor field injection

## Objective

1. Resolve IntelliJ IDEA warning/inspection `Cannot find GraphQL schema` on GraphQL annotations such as `@QueryMapping`, `@MutationMapping`, and `@SubscriptionMapping`.
2. Clean up unnecessary `@Autowired` annotations on single-constructor Spring components/controllers across the codebase in accordance with Spring best practices and project coding guidelines.

## Root Cause & Architecture Decisions

### 1. IntelliJ "Cannot find GraphQL schema"
- In `app/bff/build.gradle.kts`, the BFF includes the `contracts/` directory as a resource root, copying all `contracts/graphql/*.graphqls` files into `graphql/` during the build.
- The source directory `app/bff/src/main/resources` only contains `application.yml` and does not contain `graphql/schema.graphqls`.
- IntelliJ IDEA inspects project source roots (`src/main/resources/graphql/**`) and project configuration to resolve schema definitions for `@QueryMapping` and `@MutationMapping`. Because `schema.graphqls` only lives in `contracts/graphql/`, the IDE's GraphQL plugin cannot locate it statically in source roots.
- Solution:
  - Configure `graphql.config.yml` at the project root to point to `contracts/graphql/**/*.graphqls` so IntelliJ and its GraphQL plugin recognize the complete schema.

### 2. Constructor Field / `@Autowired` Clean-up
- In Spring Framework 4.3+, if a class has a single constructor, `@Autowired` is completely redundant and unnecessary.
- In `GroupGraphqlController.kt`:
  ```kotlin
  class GroupGraphqlController @Autowired constructor(
      private val gateway: ExpenseCoreGateway,
      @Autowired(required = false) private val fanout: LiveUpdateFanout? = null
  )
  ```
  The `@Autowired` on the constructor is redundant.
  The optional dependency `fanout: LiveUpdateFanout? = null` can be provided cleanly via default parameter or constructor without `@Autowired`.
- In `DeletionRequestService.kt` and `ExportRequestService.kt`:
  ```kotlin
  class DeletionRequestService @Autowired constructor(...)
  class ExportRequestService @Autowired constructor(...)
  ```
  `@Autowired` on primary constructors is unnecessary and should be removed.

## Dependencies

- `BFF-07`

## Owned paths

- `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/`
- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/`
- `app/bff/build.gradle.kts`
- `app/bff/src/main/resources/`
- `.graphqlconfig` or `graphql.config.yml`
- `docs/tasks/details/IDE-01.md`

## Acceptance criteria

- IntelliJ and GraphQL tools recognize all `contracts/graphql/**/*.graphqls` files via standard GraphQL config or the BFF resources directory.
- Redundant `@Autowired` on primary constructors removed from `GroupGraphqlController`, `DeletionRequestService`, `ExportRequestService`, etc.
- All application and BFF tests pass cleanly.
- `make workflow-validate`, `python3 tools/contracts/validate.py`, and `git diff --check` pass.

## Validation commands

- `./gradlew test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`
