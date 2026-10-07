#!/usr/bin/env python3
"""Detect immutable error-code and error-name changes between catalog baselines."""

from __future__ import annotations

import argparse
from pathlib import Path
import subprocess
from typing import Any, Sequence

import yaml  # type: ignore[import-untyped]  # PyYAML ships without inline type stubs

ROOT = Path(__file__).resolve().parents[2]
CATALOG_RELATIVE = "contracts/errors/error-catalog.yaml"


def records_by_identity(records: list[dict[str, Any]]) -> tuple[dict[str, dict[str, Any]], dict[str, dict[str, Any]]]:
    """Index catalog records by immutable numeric code and symbolic name."""
    by_code = {str(record["numericCode"]): record for record in records if "numericCode" in record}
    by_name = {str(record["errorName"]): record for record in records if "errorName" in record}
    return by_code, by_name


def find_breaking_changes(previous: list[dict[str, Any]], current: list[dict[str, Any]]) -> list[str]:
    """Return violations for deleted, renamed, or renumbered published identities."""
    previous_codes, previous_names = records_by_identity(previous)
    current_codes, current_names = records_by_identity(current)
    changes: list[str] = []
    for code, record in sorted(previous_codes.items()):
        if code not in current_codes:
            changes.append(f"numericCode {code} was deleted")
        elif current_codes[code].get("errorName") != record.get("errorName"):
            changes.append(f"numericCode {code} changed errorName")
    for name, record in sorted(previous_names.items()):
        current_record = current_names.get(name)
        if current_record is None:
            changes.append(f"errorName {name} was deleted")
        elif current_record.get("numericCode") != record.get("numericCode"):
            changes.append(f"errorName {name} was renumbered")
    return sorted(set(changes))


def load_catalog_text(text: str) -> list[dict[str, Any]]:
    """Parse catalog records from YAML text."""
    raw: Any = yaml.safe_load(text)
    records = raw.get("errors") if isinstance(raw, dict) else raw
    if not isinstance(records, list) or not all(isinstance(record, dict) for record in records):
        raise ValueError("catalog must contain a list of mapping records")
    return [record for record in records if isinstance(record, dict)]


def load_baseline(baseline: str) -> list[dict[str, Any]] | None:
    """Load a baseline catalog from a git revision, or return None if absent."""
    result = subprocess.run(
        ["git", "show", f"{baseline}:{CATALOG_RELATIVE}"],
        cwd=ROOT,
        capture_output=True,
        check=False,
        text=True,
        encoding="utf-8",
    )
    if result.returncode != 0:
        return None
    return load_catalog_text(result.stdout)


def parse_args(arguments: Sequence[str] | None = None) -> argparse.Namespace:
    """Parse command-line options."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", default="master", help="Git revision containing the prior catalog")
    parser.add_argument("--current", type=Path, default=ROOT / CATALOG_RELATIVE)
    return parser.parse_args(arguments)


def main(arguments: Sequence[str] | None = None) -> int:
    """Run immutable catalog comparison against a git baseline."""
    args = parse_args(arguments)
    current = load_catalog_text(args.current.read_text(encoding="utf-8"))
    previous = load_baseline(args.baseline)
    if previous is None:
        print(f"no prior catalog at {args.baseline}; immutable-history check is not applicable")
        return 0
    changes = find_breaking_changes(previous, current)
    if changes:
        print("breaking error catalog changes detected:")
        print("\n".join(f"- {change}" for change in changes))
        return 1
    print(f"no breaking error identity changes against {args.baseline}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
