"""Enforce static error-path hygiene with an explicit migration allowlist."""
from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Final

import yaml  # type: ignore[import-untyped]  # PyYAML ships without inline type stubs

ROOT: Final[Path] = Path(__file__).resolve().parents[2]
ALLOWLIST_PATH: Final[Path] = ROOT / "tools/qa/error_hygiene_allowlist.yaml"
KOTLIN_ROOTS: Final[tuple[Path, ...]] = (ROOT / "app", ROOT / "libs")
PATTERNS: Final[dict[str, re.Pattern[str]]] = {
    "generic_throw": re.compile(r"\bthrow\s+(?:RuntimeException|Exception|Throwable|IllegalArgumentException)\s*\("),
    "raw_numeric_code": re.compile(r"\bthrow\b[^\n]*[\"']\d{6}[\"']"),
    "reflection_discovery": re.compile(r"\b(?:Class\.forName|ClassLoader\.getResource|Reflections|ServiceLoader\.load)\b"),
    "catch_throwable": re.compile(r"\bcatch\s*\([^)]*\bThrowable\b"),
    "direct_problem_constructor": re.compile(r"\b(?:ApiProblem|ProblemDetails)\s*\("),
}


@dataclass(frozen=True)
class Violation:
    """One source-level policy violation identified by file, line, and kind."""

    file: str
    line: int
    violation_type: str


def source_files() -> list[Path]:
    """Return production Kotlin files under application and library roots."""
    return sorted(
        path
        for root in KOTLIN_ROOTS
        for path in root.rglob("*.kt")
        if "src/test/" not in path.as_posix()
    )


def scan() -> set[Violation]:
    """Scan source lines for forbidden error-path patterns."""
    violations: set[Violation] = set()
    for path in source_files():
        relative = path.relative_to(ROOT).as_posix()
        violations.update(scan_content(relative, path.read_text(encoding="utf-8")))
    return violations


def scan_content(relative: str, content: str) -> set[Violation]:
    """Scan supplied Kotlin content, enabling deterministic unit tests."""
    violations: set[Violation] = set()
    for line_number, line in enumerate(content.splitlines(), 1):
        for violation_type, pattern in PATTERNS.items():
            if pattern.search(line):
                violations.add(Violation(relative, line_number, violation_type))
    return violations


def load_allowlist() -> set[Violation]:
    """Load and structurally validate every explicit legacy waiver."""
    document = yaml.safe_load(ALLOWLIST_PATH.read_text(encoding="utf-8"))
    if not isinstance(document, dict) or not isinstance(document.get("entries"), list):
        raise ValueError("allowlist must contain an entries sequence")
    result: set[Violation] = set()
    for entry in document["entries"]:
        if not isinstance(entry, dict):
            raise ValueError("allowlist entries must be mappings")
        required = ("file", "line", "violation_type", "owner_task", "expires_by_task")
        if any(not entry.get(field) for field in required):
            raise ValueError(f"allowlist entry missing required metadata: {entry!r}")
        violation = Violation(str(entry["file"]), int(entry["line"]), str(entry["violation_type"]))
        if violation in result:
            raise ValueError(f"duplicate allowlist entry: {violation}")
        result.add(violation)
    return result


def main() -> int:
    """Fail closed on new violations, stale waivers, or malformed policy data."""
    try:
        discovered = scan()
        allowed = load_allowlist()
    except (OSError, ValueError, yaml.YAMLError) as error:
        print(f"error hygiene policy failure: {error}", file=sys.stderr)
        return 2
    unexpected = sorted(discovered - allowed, key=lambda item: (item.file, item.line, item.violation_type))
    stale = sorted(allowed - discovered, key=lambda item: (item.file, item.line, item.violation_type))
    if unexpected or stale:
        for violation in unexpected:
            print(f"unexpected {violation.violation_type}: {violation.file}:{violation.line}", file=sys.stderr)
        for violation in stale:
            print(f"stale allowlist entry: {violation.violation_type}: {violation.file}:{violation.line}", file=sys.stderr)
        return 1
    print(f"error hygiene passed: {len(discovered)} allowlisted legacy violations")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
