# OPS-25: Migrate Python tooling to pyproject.toml + uv sync + uv run

## Status
`completed`

## Objective
Replace the ad-hoc `python3 …`, `PYTHONPATH=.` prefix, and `uvx --from …`
invocations scattered across the `Makefile` and CI workflow with a single,
authoritative `pyproject.toml` at the repository root that:

1. Declares `tests` and `tools` as editable namespace packages (PEP 660 / PEP 517).
2. Pins every Python tool dependency (mypy, yamllint, websockets, …)
   in a locked `uv.lock` file.
3. Lets `uv sync` produce a reproducible `.venv` in one step.
4. Replaces every `python3` call in the `Makefile` and CI with `uv run`, which
   automatically activates the project virtual environment.
5. Eliminates all `PYTHONPATH=.` prefixes: package resolution comes from the
   editable install instead.
6. Makes the immutable commit-pinned `astral-sh/setup-uv@d0cc045d04ccac9d8b7881df0226f9e82c39688e` (`v6`)
   the *only* Python environment setup step in CI;
   no separate `python3` prerequisite for clone-and-run.

## Background and motivation

### Current pain points

| Symptom | Root cause |
|---|---|
| `ModuleNotFoundError: No module named 'tests'` | `tests/` is not a proper installable package; callers must remember `PYTHONPATH=.` |
| `PYTHONPATH=.` scattered in 7+ Makefile targets + 4 CI step `env:` blocks | No single source of truth for the import root |
| `uvx --from mypy==1.17.1 mypy` | Tool version pinned in two places (Makefile and CI `run:` blocks); no lock file |
| `uvx --from yamllint==1.37.1 yamllint` | Same issue; ephemeral `uvx` tool fetches are not reproducible across runners |
| `python3` used for scripts, `uvx` used for tools: no unified entrypoint | Split-brain: local developers need both system Python and uv; CI has the same split |
| `tests/fixtures/invalid_subject_oidc/server.py` had to inline constants because it ran inside Docker without `PYTHONPATH=.` | Symptom of the structural problem fixed per-file rather than systemically |

### Why pyproject.toml + uv is the right fix

- **`uv sync`** resolves and installs all declared dependencies (including editable
  packages) into `.venv` in a single, deterministic step.
- **`uv run <script>`** activates `.venv` automatically: no `source .venv/bin/activate`
  or `PYTHONPATH` management needed.
- **`uv.lock`** records exact resolved versions of every transitive dependency,
  making CI reproducible without pinning in multiple files.
- **Editable install** (`[tool.uv.sources]` or `packages = [{include = "tests"}, …]`)
  means `import tests.http_constants` works in any `uv run` invocation, in the
  virtual env, and in `uv run pytest`.
- **`astral-sh/setup-uv@d0cc045d04ccac9d8b7881df0226f9e82c39688e`** is pinned in CI; `uv sync` replaces
  `setup-java cache: gradle` for Python-side setup.

## Owned paths

```
pyproject.toml              (new)
uv.lock                     (new, generated)
Makefile                    (update python3 → uv run, remove PYTHONPATH=.)
.github/workflows/_reusable-ci.yml   (update all python3 / uvx calls)
tests/__init__.py           (new, marks tests/ as a package)
tests/e2e/__init__.py       (new)
tools/__init__.py           (new, marks tools/ as a package)
tools/contracts/__init__.py (new)
tools/ops/__init__.py       (new)
tools/errors/__init__.py    (new)
docs/operations/ci.md       (update Python tooling description)
docs/operations/quickstart.md (add uv sync prerequisite)
docs/implementation/technology-decisions.md (record uv as Python environment manager)
```

## Dependencies
- `QA-09` (done): CI pipeline structure is stable; safe to refactor Python invocations.
- No Kotlin/Gradle changes required.

## Design decisions

### 1. Project metadata

The `pyproject.toml` uses the **Hatchling** build backend (the same backend that
`uv init` recommends for pure-Python projects with no compiled extensions).

```toml
[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[project]
name = "squarewise-tools"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = []          # runtime deps: none (all are dev)

[dependency-groups]
dev = [
    "mypy==1.17.1",
    "yamllint==1.37.1",
    "websockets>=14.0,<16",
]

[tool.hatch.build.targets.wheel]
# Expose both packages from the repo root as editable installs
packages = ["tests", "tools"]

[tool.mypy]
# Moved from mypy.ini into pyproject.toml
python_version = "3.12"
...
```

> **Why Hatchling?** It is the lightest PEP 517 backend that supports the
> `packages` list for editable installs without requiring a `src/` layout change.
> The `tests/` and `tools/` directories stay exactly where they are.

### 2. `__init__.py` files

`tests/`, `tests/e2e/`, `tools/`, `tools/contracts/`, `tools/ops/`, and
`tools/errors/` each receive an empty `__init__.py`. This is required so
hatchling's wheel builder includes them as proper packages, and so `uv run`
resolves `from tests.http_constants import …` without `PYTHONPATH`.

`tests/acceptance/` already has `__init__.py`: no change needed there.

### 3. Lock file

`uv lock` is run once to produce `uv.lock`. This file is committed and updated
only when dependencies change. CI runs `uv sync --frozen --no-build` to enforce
the lock without executing package/project build hooks during environment setup.

### 4. mypy.ini → pyproject.toml

Migrate the existing `mypy.ini` content into `[tool.mypy]` in `pyproject.toml`.
The `mypy.ini` file is deleted. This eliminates one configuration file and makes
`uv run --frozen --no-build mypy tests tools` the canonical CI invocation.

### 5. Makefile changes

Every `python3` call becomes `uv run python3` (or just `uv run <script>` where
the script has a shebang). Every `PYTHONPATH=.` prefix is removed. Every
`uvx --from mypy==... mypy` becomes `uv run mypy` (resolved from the lock).
Every `uvx --from yamllint==... yamllint` becomes `uv run yamllint`.

Example diff:
```diff
-python-typecheck: ## Run mypy strict type checking over all Python source
-	@uvx --from mypy==1.17.1 mypy --config-file mypy.ini tests tools
+python-typecheck: ## Run mypy strict type checking over all Python source
+	@uv run mypy tests tools
```

```diff
-acceptance-live: ## Run the acceptance test harness requiring live running services
-	@PYTHONPATH=. python3 tests/acceptance/runner.py --require-services
+acceptance-live: ## Run the acceptance test harness requiring live running services
+	@uv run python3 tests/acceptance/runner.py --require-services
```

A new top-level `sync` (or `venv`) target is added:
```make
sync: ## Install/update the Python virtual environment from uv.lock
	@uv sync
```

### 6. CI changes

All E2E and lint job steps replace `python3` with `uv run python3` and remove
`PYTHONPATH: .` env blocks. `uvx` calls for mypy and yamllint are replaced with
`uv run --frozen --no-build mypy` and `uv run --frozen --no-build yamllint`. A
new `uv sync --frozen --no-build` step is added after the pinned setup action in
every job that runs Python.

The `verify` matrix jobs that don't run Python scripts themselves do not need
`uv sync`: only the `lint`, `e2e-*`, and `preflight` jobs need it.

`preflight` currently runs raw `python3` without `setup-uv`. After this task it
adds the pinned setup action + `uv sync --frozen --no-build` and uses
`uv run --frozen --no-build python3`.

### 7. `server.py` in the Docker fixture

The `invalid_subject_oidc/server.py` is intentionally excluded from this change.
It runs inside a Docker container with only `server.py` and `requirements.txt`
copied; `uv` is not available there. The inline constants fix (`45ad4ff`) is the
correct solution for that file. No regression.

## Acceptance criteria

- [x] `uv sync` completes successfully from a clean clone (no system packages needed).
- [x] `uv run --frozen --no-build mypy tests tools` exits 0 with the same rule set as current `mypy.ini`.
- [x] `uv run python3 -m unittest discover -s tests/acceptance` exits 0, 10/10.
- [x] `uv run python3 tests/e2e/test_oidc_negative.py --variant forged-signature` exits 0.
- [x] `make acceptance-live` exits 0 against live stack without `PYTHONPATH=.` anywhere.
- [x] `make e2e-rest-edge`, `make e2e-live`, `make e2e-offline`, `make e2e-concurrency`,
      `make e2e-chaos` all exit 0.
- [x] `make workflow-validate` exits 0.
- [x] `make python-typecheck` exits 0 using `uv run mypy`.
- [x] No `PYTHONPATH=.` appears anywhere in `Makefile` or `_reusable-ci.yml`.
- [x] `uv.lock` is committed and `uv sync --frozen --no-build` passes in CI.
- [x] `mypy.ini` is deleted; mypy config lives solely in `pyproject.toml`.
- [x] `docs/operations/quickstart.md` updated: `uv sync` listed as a prerequisite step.
- [x] `docs/implementation/technology-decisions.md` records `uv` as Python manager.
- [x] Registry marks OPS-25 `done` with commit hash.

## Validation commands

```bash
uv sync
uv run --frozen --no-build mypy tests tools
PYTHONPATH=. python3 -m unittest discover -s tests/acceptance   # should still work as fallback
uv run python3 -m unittest discover -s tests/acceptance
make python-typecheck
make acceptance-live
make e2e-rest-edge
make workflow-validate
git diff --check
python3 tools/contracts/validate.py   # contracts validator uses only stdlib
```

## Implementation order

1. Add `__init__.py` to `tests/`, `tests/e2e/`, `tools/`, `tools/contracts/`,
   `tools/ops/`, `tools/errors/`.
2. Write `pyproject.toml` with Hatchling, editable packages, and dev dependencies.
3. Run `uv sync` → generates `uv.lock`. Commit both.
4. Migrate `mypy.ini` content to `[tool.mypy]` in `pyproject.toml`. Delete `mypy.ini`.
5. Update `Makefile`: replace `python3` → `uv run python3`, remove `PYTHONPATH=.`,
   replace `uvx` → `uv run`, add `sync` target.
6. Update `_reusable-ci.yml`: pin `setup-uv` to an immutable commit and add
   `uv sync --frozen --no-build` steps to affected jobs; use
   `uv run --frozen --no-build`; remove
   `PYTHONPATH: .` env keys; replace `python3` → `uv run python3`; replace `uvx` → `uv run`.
7. Update `docs/operations/ci.md`, `docs/operations/quickstart.md`,
   `docs/implementation/technology-decisions.md`.
8. Run full local validation; record evidence.

## Known limitations and non-goals

- The `tests/fixtures/invalid_subject_oidc/server.py` Docker container is excluded
  from the `uv` ecosystem: it has its own `requirements.txt` and runs standalone.
- The `make doctor` target checks for `python3` and `uv`; after this task it should
  check only for `uv` (Python is managed by uv).
- No changes to Kotlin/Gradle, JVM tooling, or Spring Boot services.
- No `src/` layout migration; `tests/` and `tools/` stay at the repo root.
