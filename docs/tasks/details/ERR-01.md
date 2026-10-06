# ERR-01: Governed error taxonomy and catalog

Define the authoritative catalog and rules for specific service/feature/scenario
codes. Add schema validation for uniqueness, prefixes, status, ownership,
retryability, severity, and safe client detail. Reconcile the existing broad
codes as compatibility aliases or explicitly deprecate them. No application
implementation starts until the catalog and review rules are accepted.

Validation: `python3 tools/contracts/validate.py`,
`python3 tools/errors/validate_catalog.py`, and `git diff --check`.
