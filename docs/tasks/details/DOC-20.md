# DOC-20: Establish a non-breaking Kotlin formatting baseline

Use the DOC-14-compatible Spotless/ktfmt toolchain to remediate or baseline the
known formatting violations in bounded increments, then replace or close the
blocked DOC-12 approach with a reliable CI check. Depends on DOC-12 and DOC-14.
Owns formatting configuration, coding guidelines, and mechanical formatting
changes registered before each implementation increment.

## Current increment

Spotless 8.1.0 is centrally declared and checks whitespace/newline invariants
for the approved `ExpenseCategory.kt` baseline without rewriting its accepted
layout. The ktfmt engine remains available for later bounded migrations. The
The accepted non-breaking baseline is complete: `./gradlew spotlessCheck
--no-daemon` is a reliable CI check and does not rewrite approved formatting.
Repository-wide migration of legacy violations remains separate work and must
be registered as bounded increments before changing additional files.
