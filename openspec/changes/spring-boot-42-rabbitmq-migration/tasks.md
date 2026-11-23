# Tasks

## 1. Dependency selection

- [x] 1.1 Replace the generic AMQP catalog alias with the explicit RabbitMQ AMQP 0.9 starter and update all four application build scripts; verify no `libs.boot.amqp` or `spring-boot-starter-amqp` application dependency remains.
- [x] 1.2 Regenerate the Accounts, BFF, Expense Core, and Notifications lockfiles; verify each graph contains `spring-boot-starter-rabbitmq`, `spring-boot-rabbitmq`, `spring-rabbit`, and `com.rabbitmq:amqp-client`, with no `spring-boot-starter-amqp` or Qpid ProtonJ2 dependency introduced by these applications.

## 2. OpenSpec documentation

- [x] 2.1 Keep the proposal, RabbitMQ AMQP 0.9 capability spec, and design synchronized with the implemented dependency choice; verify `openspec validate spring-boot-42-rabbitmq-migration --strict` passes.
- [x] 2.2 Record release-note and deprecation audit evidence in the OpenSpec design and issue body; verify the audit covers Spring Boot, Spring AMQP, Flyway, Gradle, CycloneDX, SonarScanner, Kotlin, and affected transitive APIs.

## 3. Build and behavior verification

- [x] 3.1 Compile all applications and tests with JDK 25 and Gradle 9.9.2 using `./gradlew testClasses --warning-mode=all`; verify the previous unresolved RabbitMQ symbols are gone.
- [x] 3.2 Run JVM tests, coverage, packaging, and shared-library tests; verify existing messaging tests and application test classes complete successfully.
- [x] 3.3 Run RabbitMQ-backed local acceptance checks when Docker services are available; verify existing publisher, consumer, acknowledgement, and correlation behavior remains valid.

## 4. Repository gates and delivery evidence

- [x] 4.1 Run `make contracts`, `make python-typecheck`, `make workflow-validate`, `make compose-config`, `make sbom-validate`, `make security-hygiene`, and `git diff --check`; record exact results in the OpenSpec change evidence.
- [ ] 4.2 Commit the focused implementation and OpenSpec artifacts on the new local branch with a meaningful conventional commit; verify the branch is clean and no push occurs.

## Workflow follow-up

- Create the detailed follow-up issue with `gh issue create`, referencing PR #67 and this OpenSpec change.
- Close PR #67 with `gh pr close` after the issue exists, linking the issue in the close comment.
- Do not push the local branch or commit.
