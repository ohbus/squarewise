# OPS-11: Enable GitHub Actions BuildKit GHA image caching

## Objective

Fix the main image-publish job so Docker Buildx uses a cache-capable
`docker-container` driver before exporting/importing the GitHub Actions cache.

## Dependencies

- `OPS-04`
- `OPS-06`

## Owned paths

- `.github/workflows/_reusable-ci.yml`
- `docs/operations/ci.md`
- `docs/tasks/details/OPS-11.md`

## Acceptance criteria

- The image job creates and selects an explicit `docker-container` Buildx builder.
- GHA cache scopes are isolated per matrix service.
- Workflow YAML parses and the changed workflow remains structurally valid.
- Documentation explains the driver requirement and cache behavior.

## Validation commands

- `make workflow-validate`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Evidence

The failing hosted command used the default Docker driver and reported:
`Cache export is not supported for the docker driver.`
