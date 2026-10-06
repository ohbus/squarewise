# DOC-10: IntelliJ developer run configurations

## Objective

Provide shared IntelliJ IDEA run configurations with the same project paths,
JVM settings, Spring profiles, and local environment assumptions used by the
documented Docker Compose development workflow.

## Deliverables

- `.run/` XML configurations for Accounts, Expense Core, Notifications, BFF,
  the full JVM test suite, and local infrastructure Compose.
- [`docs/operations/intellij.md`](../../operations/intellij.md) describing import,
  prerequisites, environment variables, and how to add a new service.

## Acceptance criteria

- Configurations are safe to share: no credentials or machine-specific paths.
- Every application configuration points at the correct Gradle project and main
  class, uses Java 25, and documents required local environment variables.
- Changes to service names, ports, profiles, or required environment variables
  update the run configuration and this task's evidence in the same increment.

## Verification

- `python3 tools/contracts/validate.py`
- `git diff --check`
- IntelliJ import inspection or equivalent XML validation.

## Implementation notes

- IntelliJ 2026.2 registers Spring Boot runs as
  `SpringBootApplicationConfigurationType`; using the shorter
  `SpringBootConfiguration` ID leaves the shared entries unresolved.
- Native application values mirror the local infrastructure Compose file:
  service-specific databases, the fixed local-only PostgreSQL/RabbitMQ
  credentials, Mailpit on `localhost:1025`, and host application ports.
- The application entries use standard IntelliJ `Application` configurations
  with the imported Gradle module and Kotlin-generated `*ApplicationKt` main
  class. This directly invokes the verified `main(String[])` entry point without
  Spring Boot plugin start-point inference.
- Each bootstrap remains the sole class in its service root package so default
  Spring component scanning covers the complete application. The IntelliJ
  configuration and Spring Boot `mainClass` setting target that root launcher.
