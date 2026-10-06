"""Compute the CI verification scope from changed repository paths."""

from __future__ import annotations

import argparse
import json
import sys
from collections.abc import Iterable, Sequence
from dataclasses import dataclass


@dataclass(frozen=True)
class Module:
    """Describe one Gradle module that can be verified independently."""

    kind: str
    name: str
    path: str
    report: str


MODULES: tuple[Module, ...] = (
    Module("app", "accounts", ":app:accounts", "app/accounts/build/reports/jacoco/test/jacocoTestReport.xml"),
    Module("app", "expense-core", ":app:expense-core", "app/expense-core/build/reports/jacoco/test/jacocoTestReport.xml"),
    Module("app", "notifications", ":app:notifications", "app/notifications/build/reports/jacoco/test/jacocoTestReport.xml"),
    Module("app", "bff", ":app:bff", "app/bff/build/reports/jacoco/test/jacocoTestReport.xml"),
    Module("library", "db", ":libs:db", "libs/db/build/reports/jacoco/test/jacocoTestReport.xml"),
    Module("library", "errors", ":libs:errors", "libs/errors/build/reports/jacoco/test/jacocoTestReport.xml"),
    Module("library", "observability", ":libs:observability", "libs/observability/build/reports/jacoco/test/jacocoTestReport.xml"),
    Module("library", "security", ":libs:security", "libs/security/build/reports/jacoco/test/jacocoTestReport.xml"),
    Module("library", "test-support", ":libs:test-support", ""),
)

ALL_MODULE_NAMES = frozenset(module.name for module in MODULES)
APP_NAMES = frozenset({"accounts", "expense-core", "notifications", "bff"})

# Reverse dependency closure for the current Gradle project graph. A changed
# library requires its direct consumers to be tested as well as the library.
DEPENDENTS: dict[str, frozenset[str]] = {
    "observability": frozenset({"db"}),
    "db": frozenset(APP_NAMES),
    "ids": frozenset({"accounts", "expense-core", "notifications", "bff", "errors"}),
    "errors": frozenset(APP_NAMES),
    "security": frozenset(APP_NAMES),
}

GLOBAL_PATH_PREFIXES = (
    ".github/",
    "contracts/",
    "gradle/",
    "infra/",
    "tools/",
    "tests/acceptance/",
)
GLOBAL_PATHS = frozenset(
    {
        "Makefile",
        "build.gradle.kts",
        "gradle.properties",
        "settings.gradle.kts",
        "gradlew",
        "gradlew.bat",
        "pyproject.toml",
        "uv.lock",
    }
)


def _module_for_path(path: str) -> str | None:
    """Return the Gradle module named by an application/library path."""

    normalized = path.replace("\\", "/")
    parts = normalized.split("/")
    if len(parts) >= 2 and parts[0] in {"app", "libs"}:
        return parts[1]
    return None


def _e2e_streams(paths: Sequence[str], modules: set[str], global_change: bool) -> set[str]:
    """Return E2E streams whose public paths can be affected by the change."""

    if global_change or modules:
        return {"edge", "product", "chaos"}

    streams: set[str] = set()
    for raw_path in paths:
        path = raw_path.replace("\\", "/")
        if not path.startswith("tests/e2e/"):
            continue
        if any(token in path for token in ("auth_cache", "oidc", "rest_edge", "contract")):
            streams.add("edge")
        elif any(token in path for token in ("auth_email", "product", "offline", "bruno")):
            streams.add("product")
        elif any(token in path for token in ("concurrency", "chaos", "websocket")):
            streams.add("chaos")
        else:
            return {"edge", "product", "chaos"}
    return streams


def _is_global(path: str) -> bool:
    """Return whether a path changes repository-wide CI assumptions."""

    normalized = path.replace("\\", "/")
    return normalized in GLOBAL_PATHS or normalized.startswith(GLOBAL_PATH_PREFIXES)


def _affected_modules(changed_paths: Sequence[str]) -> tuple[set[str], bool]:
    """Resolve changed paths to modules and their reverse dependency closure."""

    direct = {module for path in changed_paths if (module := _module_for_path(path))}
    if any(module not in ALL_MODULE_NAMES and module != "ids" for module in direct):
        return set(ALL_MODULE_NAMES), True

    affected = set(direct) & set(ALL_MODULE_NAMES)
    pending = list(affected | (direct & {"ids"}))
    while pending:
        module = pending.pop()
        for dependent in DEPENDENTS.get(module, frozenset()):
            if dependent not in affected:
                affected.add(dependent)
                pending.append(dependent)
    return affected, False


def compute_scope(changed_paths: Iterable[str], full_run: bool = False) -> dict[str, object]:
    """Compute module, Sonar, and E2E selections for one CI invocation.

    A master or explicitly full run selects every module and E2E stream. A
    changed-scope run selects the affected module dependency closure. Changes
    confined to documentation do not select Gradle verification or E2E, while
    repository-wide build, contract, infrastructure, tooling, and workflow
    changes conservatively select the complete scope.
    """

    paths = tuple(path.strip() for path in changed_paths if path.strip())
    global_change = any(_is_global(path) for path in paths)
    modules, unknown_module = _affected_modules(paths)
    full_scope = full_run or global_change or unknown_module
    if full_scope:
        modules = set(ALL_MODULE_NAMES)
    streams = {"edge", "product", "chaos"} if full_scope else _e2e_streams(paths, modules, global_change)
    module_rows = [module.__dict__ for module in MODULES if module.name in modules]
    return {
        "matrix": module_rows,
        "has_verify": bool(module_rows),
        "full_scope": full_scope,
        "run_sonar": full_scope or bool(modules),
        "has_e2e": bool(streams),
        "run_edge": "edge" in streams,
        "run_product": "product" in streams,
        "run_chaos": "chaos" in streams,
        "changed_paths": list(paths),
    }


def main() -> int:
    """Write GitHub Actions outputs for paths read from standard input."""

    parser = argparse.ArgumentParser()
    parser.add_argument("--full-run", action="store_true")
    args = parser.parse_args()
    scope = compute_scope(sys.stdin, full_run=args.full_run)
    print(json.dumps(scope, sort_keys=True))
    for key, value in scope.items():
        if key == "matrix":
            output = json.dumps(value, separators=(",", ":"))
        elif isinstance(value, bool):
            output = str(value).lower()
        elif isinstance(value, list):
            output = json.dumps(value, separators=(",", ":"))
        else:
            output = str(value)
        print(f"{key}={output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
