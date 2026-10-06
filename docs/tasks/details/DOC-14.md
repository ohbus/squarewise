# DOC-14: Kotlin lint compatibility research

Investigate a stable lint/format implementation that works with Kotlin 2.4.20
using actual dependency resolution. Do not lower the Kotlin version or enable a
failing quality gate silently.

## Investigation evidence

On 2026-09-17, Gradle resolved `com.diffplug.spotless:spotless-gradle-plugin:8.1.0`
and `ktfmt:0.54` successfully with Kotlin 2.4.20, Spring Boot 4.1.1, Gradle
9.7.1, and Java 25. The configuration compiled and registered
`spotlessKotlinCheck` and `spotlessKotlinApply`.

The check found formatting violations in 65 Kotlin files. A required gate would
therefore need a repository-wide formatting migration first. The temporary
plugin configuration was removed; Kotlin and dependency baselines were not
changed. Existing ktlint attempts remain blocked by the separate
`Extensions storage is not registered` failure documented under DOC-12.

Validation:

```text
./gradlew spotlessCheck --no-daemon
```

Dependency resolution and task execution succeeded; the command exited non-zero
only because existing sources do not match ktfmt formatting.
