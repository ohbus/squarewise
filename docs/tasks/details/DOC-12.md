# DOC-12: Project-wide Kotlin lint and coding guidelines

Establish a Kotlin quality baseline with a toolchain compatible with the
repository's Kotlin 2.4.20 and expose it through Gradle and Makefile commands.
Document repository coding rules without enabling the incompatible ktlint
integration.

## Compatible approach

Spotless 8.1.0 is centrally versioned and uses the verified ktfmt engine for a
non-rewriting approved Kotlin baseline. `make lint` runs contract validation,
`spotlessCheck`, and the Gradle `check` lifecycle. This deliberately avoids the
ktlint plugin failure (`Extensions storage is not registered`) recorded by
DOC-14; broader formatting migration must be registered as bounded work.
