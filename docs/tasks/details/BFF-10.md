# BFF-10: Resolve GraphQL scalar deprecation warnings

Replace or narrowly suppress deprecated Spring GraphQL `Coercing` overrides in
the custom Money/DateTime scalars. Preserve schema behavior, invalid literal
rejection, serialization, and parsing semantics. Add valid/invalid scalar tests,
document the compatibility decision, and leave compilation warning-free.

Depends on BFF-09. Owns the scalar configuration, BFF scalar tests, and related
quality documentation.
