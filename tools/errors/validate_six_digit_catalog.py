#!/usr/bin/env python3
"""Validate the frozen six-digit catalog and namespace allocations."""

from __future__ import annotations

from pathlib import Path
import sys
from typing import Any

import yaml  # type: ignore[import-untyped]  # PyYAML ships without inline type stubs

from tools.errors.validate_catalog import (
    validate_domains_registry,
    validate_six_digit_record,
)

ROOT = Path(__file__).resolve().parents[2]
CATALOG_PATH = ROOT / "contracts/errors/error-catalog.yaml"
DOMAINS_PATH = ROOT / "contracts/errors/domains.yaml"


def load_records(path: Path) -> list[dict[str, Any]]:
    """Load a YAML list of mapping records from ``path``."""
    raw: Any = yaml.safe_load(path.read_text(encoding="utf-8"))
    if isinstance(raw, dict) and isinstance(raw.get("errors"), list):
        raw = raw["errors"]
    if not isinstance(raw, list) or not all(isinstance(item, dict) for item in raw):
        raise ValueError(f"{path} must contain a list of mapping records")
    return [item for item in raw if isinstance(item, dict)]


def validate_catalog_records(
    records: list[dict[str, Any]], domains_registry: dict[str, Any]
) -> list[str]:
    """Return deterministic diagnostics for six-digit catalog invariants."""
    errors: list[str] = []
    seen_codes: set[str] = set()
    seen_names: set[str] = set()
    namespace_sequences: dict[tuple[int, int, int, int], list[int]] = {}
    domains: dict[Any, Any] = domains_registry.get("domains", {})

    for index, record in enumerate(records):
        code = str(record.get("numericCode", ""))
        name = str(record.get("errorName", ""))
        if "numericCode" not in record:
            errors.append(f"record {index}: legacy records are not allowed")
            continue
        errors.extend(
            f"record {index} ({code}): {message}"
            for message in validate_six_digit_record(record, domains_registry)
        )
        if code in seen_codes:
            errors.append(f"record {index}: duplicate numericCode {code}")
        seen_codes.add(code)
        if name in seen_names:
            errors.append(f"record {index}: duplicate errorName {name}")
        seen_names.add(name)

        if len(code) == 6 and code.isdigit():
            domain, module, layer, category = (int(part) for part in code[:4])
            namespace = (domain, module, layer, category)
            sequence = int(code[4:])
            namespace_sequences.setdefault(namespace, []).append(sequence)
            domain_entry = domains.get(domain)
            if not isinstance(domain_entry, dict):
                errors.append(f"record {index}: domain {domain} is not registered")
            elif module not in domain_entry.get("modules", {}):
                errors.append(f"record {index}: module {domain}/{module} is not registered")

    for namespace, sequences in sorted(namespace_sequences.items()):
        if sequences != sorted(sequences) or len(sequences) != len(set(sequences)):
            errors.append(f"namespace {namespace}: sequence allocations are not strictly monotonic")
    return errors


def main() -> int:
    """Run the catalog gate and print actionable diagnostics."""
    try:
        domains = validate_domains_registry(DOMAINS_PATH)
        errors = validate_catalog_records(load_records(CATALOG_PATH), domains)
    except (OSError, ValueError, yaml.YAMLError) as error:
        print(f"six-digit catalog invalid: {error}")
        return 1
    if errors:
        print("six-digit catalog invalid:")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print(f"valid six-digit catalog: {len(load_records(CATALOG_PATH))} records")
    return 0


if __name__ == "__main__":
    sys.exit(main())
