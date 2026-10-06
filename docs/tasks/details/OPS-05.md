# OPS-05: CI hardening and local parity

Review the reusable workflows for least privilege, current action versions,
parallel dependency edges, and trigger isolation. Align local Make targets and
operations documentation with the hosted stages. No product behavior changes.

## Implementation notes

- Added `make ci` as the local equivalent of hosted preflight, verification,
  coverage, checks, and application packaging. Gradle receives `--parallel` so
  independent projects can execute concurrently in one local checkout.
- Added `make ci-e2e` for the hosted main workflow's verification plus E2E
  smoke stage. Image builds remain explicit local targets and never push.
- Documented the hosted matrix fan-out, dependency edges, Microsoft OpenJDK
  setup, GHCR policy, and local/hosted execution differences in
  `docs/operations/ci.md`.

## Verification evidence

- `make -n ci` and `make -n ci-e2e` show the expected preflight and parallel
  Gradle commands.
- Ruby YAML parsing succeeds for all four workflow files.
- `python3 tools/contracts/validate.py` succeeds and validates 37 task links.
- `git diff --check` succeeds.
