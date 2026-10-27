# DOC-29 - Migrate first-party repository content to AGPL-3.0-only

## Objective

Replace the repository's MIT project license with the exact-version GNU Affero
General Public License v3.0 (`AGPL-3.0-only`) for first-party Squarewise code,
documentation, tests, tooling, and configuration. Preserve third-party notices,
generated/lockfile provenance, historical MIT releases, and the existing
contribution model.

## Dependencies

- `DOC-27` - repository community-health files and current MIT license baseline
- `SEC-01F` - supply-chain and SBOM evidence conventions

## Owned paths

- `LICENSE`
- `NOTICE`
- `README.md`
- `CONTRIBUTING.md`
- `pyproject.toml`
- `Makefile`
- `.github/workflows/_reusable-ci.yml`
- `docs/README.md`
- `docs/operations/README.md`
- `docs/operations/licensing.md`
- `docs/operations/ci.md`
- `docs/security/supply-chain-evidence.md`
- `docs/implementation/production-readiness-roadmap.md`
- `docs/operations/production-readiness-plan.md`
- `docs/reviews/production-readiness-audit.md`
- `tools/ops/validate_license.py`
- `tools/ops/test_validate_license.py`
- `docs/tasks/details/DOC-29.md`
- `docs/tasks/registry.yaml`
- `docs/tasks/board.md`
- `docs/tasks/progress.md`

## Decisions and boundaries

1. The exact SPDX identifier is `AGPL-3.0-only`; future AGPL versions are not
   granted automatically.
2. The branch is `legal/agpl-3.0`, created from the unchanged `master` branch.
3. The canonical AGPL text is copied verbatim into `LICENSE`; repository
   history is not rewritten and previously distributed MIT releases remain
   available under their original grant.
4. First-party files are covered by the project license. Third-party material
   keeps its own terms, including the Apache-2.0 Gradle wrapper and Contributor
   Covenant text. Dependencies and lockfiles are inventory inputs, not files
   that the project can relicense.
5. No CLA or DCO is introduced. Contributors remain responsible for having the
   right to submit their work under the project license.
6. AGPL network-source obligations are documented for operators of modified
   network deployments; this task does not add a runtime source-download route.

## Acceptance criteria

1. `LICENSE` is the canonical GNU AGPL v3.0 text and contains no MIT terms.
2. README, documentation indexes, contributor guidance, package metadata, and
   active production-readiness language consistently identify `AGPL-3.0-only`.
3. Third-party and historical license references are explicitly documented and
   are not accidentally rewritten.
4. `license-validate` checks the authoritative license marker, package metadata,
   active references, and the documented exception boundary on every CI lint run.
5. The task registry, board, task detail, and progress ledger contain the
   objective, ownership, validation commands, evidence, limitations, and commit
   hashes.

## Validation commands

```text
uv run --frozen --no-build python tools/contracts/validate.py
uv run --frozen --no-build python -m unittest tools.ops.test_validate_license
make license-validate
make python-typecheck
make workflow-validate
make sbom-validate
uv build
git diff --check
```

## Implementation notes and limitations

The license validator is intentionally narrow: it prevents stale project-level
metadata and references, but it does not decide whether every external
dependency is legally compatible. CycloneDX SBOM output and dependency review
remain the authoritative inventory for that separate review.

The repository currently has one substantive copyright identity in history plus
Dependabot commits. Ownership of imported snippets and employer-owned work must
still be confirmed by the copyright holder before merge.

## Evidence

### Increment 1 - governance and policy

Commit `c6c33268` registered DOC-29, added the licensing policy, documented the
third-party boundary and AGPL network obligations, and linked the policy from
the contributor and documentation entry points.

### Increment 2 - license and CI gate

Commit `36e76acb` replaced the MIT text with the canonical SPDX
`AGPL-3.0-only` text, updated README/package/readiness metadata, added the typed
license validator and four regression tests, and wired the validator into the
reusable CI lint job and `make validate`.

The separate `NOTICE` file preserves the first-party copyright holder while the
canonical license remains verbatim; it is included in the Python package's
license-file metadata.

Verified locally:

- `make license-validate` passed.
- `uv run --frozen --no-build python -m unittest tools.ops.test_validate_license`
  passed 4 tests.
- `make contracts` passed with 265 valid tasks, 45 REST operations, 9 GraphQL
  root operations, 50 Bruno requests, and 99 catalog records.
- `make python-typecheck` passed for 75 files.
- `make workflow-validate`, `make sbom-validate`, `make security-hygiene`,
  `uv build`, and `git diff --check` passed.
- Built wheel metadata contains `License-Expression: AGPL-3.0-only` and
  `License-File: LICENSE`; the checked-in license matches the SPDX text.

Hosted GitHub license detection, repository About metadata, and final copyright
ownership confirmation remain post-merge checks and cannot be verified from the
local checkout.
