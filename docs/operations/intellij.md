# IntelliJ IDEA development

Shared run configurations live in `.run/` and are committed so a fresh clone
has the same developer entry points. Open the repository as a Gradle project,
start Docker Desktop, and run `Squarewise Local Infrastructure` before starting
an application. The infrastructure configuration starts PostgreSQL, RabbitMQ,
and Mailpit from `infra/local/docker-compose.yml`.

Application configurations use Java 25, the `local` Spring profile, and the
repository root as the working directory. Local credentials are development-only
values from the Compose file; production credentials must never be copied into
these XML files.

The application entries use standard IntelliJ `Application` configurations with
the imported Gradle module and the Kotlin-generated `*ApplicationKt` launcher.
This invokes each real `main(String[])` directly and avoids Spring Boot plugin
start-point ambiguity. Their database, RabbitMQ, SMTP, port, and upstream URL
values mirror the dependency-only Compose topology and host native-run ports.

## Develocity live build timeline

The project applies the Develocity Gradle plugin `4.5.0` from
`settings.gradle.kts`. This enables IntelliJ's Develocity live build timeline
when the Develocity IntelliJ plugin is installed. Local builds do not publish
Build Scans; CI builds publish them to `scans.gradle.com` and use the `CI` tag.

In IntelliJ, open **Settings/Preferences | Tools | Develocity** if the IDE
offers to configure the live timeline. The Gradle project must use the wrapper,
JDK 25, and the Gradle build/run delegation described in the repository
quickstart. The timeline is diagnostic only and is not required for Gradle
sync or application execution.

| Configuration | Gradle project / main class | Purpose |
|---|---|---|
| Squarewise Accounts | `AccountsApplicationKt` / `squarewise.app.accounts.main` | Accounts REST service |
| Squarewise Expense Core | `ExpenseCoreApplicationKt` / `squarewise.app.expense-core.main` | Financial REST service |
| Squarewise Notifications | `NotificationsApplicationKt` / `squarewise.app.notifications.main` | Notification REST service |
| Squarewise BFF | `BffApplicationKt` / `squarewise.app.bff.main` | GraphQL and WebSocket edge |
| Squarewise JVM Tests | Gradle `test` | Complete JVM test suite |
| Squarewise Local Infrastructure | `infra/local/docker-compose.yml` | PostgreSQL, RabbitMQ, Mailpit |

When adding a service, first register its task and update the architecture and
operations documents. Then add its `.run` configuration, main class, port and
environment variables, and validate the repository contracts before committing.

## GraphQL Schema Detection

To allow IntelliJ IDEA and the GraphQL plugin to recognize the GraphQL schema for
Spring GraphQL annotations (`@QueryMapping`, `@MutationMapping`, `@SubscriptionMapping`),
the repository provides root-level `graphql.config.yml` referencing all
`contracts/graphql/**/*.graphqls` files. The schema is organized into scalar,
root-operation, domain-type, and input files so code navigation stays focused.
Additionally, `:app:bff` includes the `contracts/` directory in
`sourceSets.main.resources`, allowing Spring GraphQL and the IDE to resolve the
complete schema from the same source without manual configuration.
