# Squarewise licensing and compliance

Squarewise first-party software and repository-authored implementation material
are licensed under the [GNU Affero General Public License v3.0 only](../../LICENSE)
(`AGPL-3.0-only`). The root `LICENSE` file is the complete, unmodified license
text and is the authoritative project-level grant.

## Scope

The project license covers original Squarewise application code, shared
libraries, tests, operational tooling, configuration, and repository-authored
documentation unless a file states another license.

The following material is not relicensed by this change:

- `gradlew` and `gradlew.bat`, which retain their Apache License 2.0 notices;
- Contributor Covenant text in `CODE_OF_CONDUCT.md`, which retains its upstream
  attribution and terms;
- dependency sources, generated lockfile records, and other external material,
  which retain their upstream licenses and provenance.

No commit history is rewritten. Releases and snapshots published before the
AGPL migration remain available under the license stated when they were
distributed.

## Contributions

Contributors must have the right to submit each contribution and must submit it
under the project license. Squarewise does not currently require a separate CLA
or DCO. Do not copy third-party code into the repository without preserving its
copyright and license notice and recording the compatibility decision.

## Dependencies and releases

The CycloneDX SBOM and dependency-review workflow inventory external components.
Their licenses must remain visible in downstream distributions; the project
license does not convert an external dependency into AGPL code. Any exception
requires an owner, rationale, and review before release.

Operators who modify Squarewise and expose it to users over a network must
provide the corresponding source for that modified version as required by
AGPL-3.0 section 13. The deployment must expose a prominent, no-cost source
location and retain the exact source revision used to build the running image.

This document records repository policy; it is not legal advice.
