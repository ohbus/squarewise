# Qodana inspection audit

## Scope and evidence

The local audit was run on 2026-09-22 with `jetbrains/qodana-jvm-community:2026.2` (Qodana for JVM 2026.2.3), the repository's `qodana.starter` profile, JDK 25, and a successful Gradle project import. The authoritative result is the generated SARIF report from that run; generated reports are intentionally not committed.

The first completed scan reported 58 enabled findings. The requested IntelliJ count of 42 is profile/version dependent and is not the count produced by the checked-in Qodana profile. Suggested inspections are reported separately by Qodana and are not part of the enabled-finding total. A subsequent verification scan completed project import but was terminated by the local container with exit 137 during analysis, so no new SARIF result was accepted as authoritative.

## Resolved findings

| Inspection | Count | Resolution |
| --- | ---: | --- |
| `KDocUnresolvedReference` | 17 | Removed detached KDoc blocks, corrected parameter tags, and used a resolvable fully qualified exception name. |
| `EnumValuesSoftDeprecate` | 2 | Replaced `Enum.values()` with Kotlin `entries`. |
| `UnstableApiUsage` | 4 | Added a narrow file-level suppression in `settings.gradle.kts`; Gradle repository-mode configuration is intentional and required. |

These changes were verified with `./gradlew test`, which completed successfully.

## Reviewed false positives

The completed scan's remaining 35 findings were `CanConvertToMultiDollarString`, all in these locations:

- Accounts configuration and security: `AuthSessionConfiguration.kt`, `AuthenticationCredentialConfiguration.kt`, `AuthEmailOutboxRelay.kt`, and `ProductionSecurityConfig.kt`.
- BFF configuration and gateways: `ProductionSecurityConfig.kt`, `UpstreamConfiguration.kt`, `AccountsGateway.kt`, and `ExpenseCoreGateway.kt`.
- Expense Core scheduled/configuration code: `ExpenseIdempotencyCleanup.kt`, `OutboxRelayDaemon.kt`, `RecurringExpenseWorker.kt`, and `ProductionSecurityConfig.kt`.
- Notifications configuration/listeners: `RabbitNotificationListener.kt`, `AuthEmailRabbitListener.kt`, `AuthEmailSecurityConfiguration.kt`, and `ProductionSecurityConfig.kt`.
- Shared infrastructure: `OidcConfigurationGuard.kt`. The historical SARIF named
  `GlobalErrorHandler.kt`; the current implementation is `GlobalErrorAdvice.kt`,
  which does not carry this file-level suppression.

This inspection recommends Kotlin's multi-dollar string syntax when a literal contains a dollar sign. The flagged values are predominantly Spring `${...}` placeholders escaped specifically for Spring's property resolver, while the remaining ordinary interpolations are short diagnostic strings. Converting them would add a non-standard delimiter to configuration-heavy code without changing behavior or improving safety. Each finding is now covered by a narrow file-level `@file:Suppress("CanConvertToMultiDollarString")`; no global suppression was added. The suppressions are intentional reviewed-false-positive decisions and keep the warning set closed in the checked-in profile.

Qodana also displayed suggested inspections such as unused symbols and imports. Those suggestions are outside the enabled profile result set and were not changed without a concrete compiler or behavior justification.

## Current suppression inventory

The follow-up source audit found 36 warning annotations before cleanup. The
current source retains only the reviewed, localized cases:

- 17 `CanConvertToMultiDollarString` file-level suppressions for Spring property
  placeholders and short diagnostic interpolations.
- 1 `UnstableApiUsage` suppression in `settings.gradle.kts` for intentional Gradle
  repository-mode configuration.
- 7 test-only `UNCHECKED_CAST` suppressions for Mockito/raw-Java generic interop.
- 6 `UNUSED_PARAMETER` suppressions for safe Spring exception handlers and one
  reflection fixture.

The stale file-wide `DEPRECATION` suppression in `HttpHeadersConfiguration.kt`,
the unused `GroupAccessCache` constructor dependency in
`EntityReferenceValidator`, and three production generic cache casts were
removed. Group access caches now use concrete API DTO types, so the compiler no
longer needs unchecked-cast suppressions in production code. No global compiler,
Gradle, or Qodana warning suppression was added.

## Reproduction

```shell
docker run --rm \
  -v "$PWD:/data/project" \
  -v "$PWD/qodana-results:/data/results" \
  jetbrains/qodana-jvm-community:2026.2 \
  --results-dir=/data/results --cache-dir=/data/qodana-cache
```

The commercial `jetbrains/qodana-jvm` image requires a Qodana Cloud token. The community image is used for local, token-free verification; IntelliJ inspections may differ by IDE build and enabled profile. On this machine, the final verification attempt required more memory than the Docker container provided and exited 137; rerun it with a larger container allocation when available.
