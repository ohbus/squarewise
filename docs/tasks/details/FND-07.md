# FND-07: UUIDv7 generation

Java 25 does not provide the Java 26 UUIDv7 factory API. Verify and centrally
version a compatible RFC 9562 UUIDv7 library, expose one small foundation
abstraction, and migrate only new identifier generation sites. Caller-provided
IDs and deterministic fixtures remain untouched.

## Implementation

- Dependency: `com.github.f4b6a3:uuid-creator:6.1.1`, centrally managed in
  `gradle/libs.versions.toml`. Gradle resolved it successfully with Kotlin
  2.4.20 and Java 25.
- Foundation API: `libs/ids` exposes `UuidGenerator.next()`, keeping the
  third-party API out of application code.
- Migrated internally generated request, subscription, settlement, group, and
  invitation identifiers, plus generated request IDs. Caller-supplied IDs and
  tests remain unchanged.
- The library test verifies UUID version 7 and RFC variant 2.

## Validation

`./gradlew :libs:ids:dependencies --configuration compileClasspath --no-daemon`
resolved `com.github.f4b6a3:uuid-creator:6.1.1`; focused application and library
tests passed with `./gradlew :libs:ids:test :app:accounts:test
:app:expense-core:test :app:bff:test :libs:errors:test --no-daemon`.
