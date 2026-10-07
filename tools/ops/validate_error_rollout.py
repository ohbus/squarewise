#!/usr/bin/env python3
"""Validate the additive error-contract rollout manifest and ledger shape."""

from __future__ import annotations

from pathlib import Path
import re
import sys
from typing import Any

import yaml  # type: ignore[import-untyped]  # PyYAML ships without inline type stubs

ROOT = Path(__file__).resolve().parents[2]
MANIFEST_PATH = ROOT / "infra/deploy/canary/error-rollout-config.yaml"
LEDGER_PATH = ROOT / "docs/operations/rollout-verification-ledger.md"
EXPECTED_SERVICES = ("accounts", "expense-core", "notifications", "bff")
EXPECTED_PERCENTAGES = [5, 25, 100]


def load_mapping(path: Path) -> dict[str, Any]:
    """Load a YAML mapping and reject malformed top-level documents."""
    document: Any = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(document, dict):
        raise ValueError(f"{path} must contain a mapping")
    return document


def validate_manifest(document: dict[str, Any]) -> list[str]:
    """Return diagnostics for the rollout manifest's immutable safety bounds."""
    errors: list[str] = []
    if document.get("apiVersion") != "squarewise.dev/v1":
        errors.append("manifest apiVersion must be squarewise.dev/v1")
    if document.get("kind") != "ErrorContractRollout":
        errors.append("manifest kind must be ErrorContractRollout")

    spec = document.get("spec")
    if not isinstance(spec, dict):
        return [*errors, "manifest spec must be a mapping"]

    compatibility = spec.get("compatibility")
    if not isinstance(compatibility, dict):
        errors.append("compatibility must be a mapping")
    else:
        for key in ("legacyCodePreserved", "rollbackKeepsAdditiveFieldsOptional"):
            if compatibility.get(key) is not True:
                errors.append(f"compatibility.{key} must remain true")
        if compatibility.get("additiveFields") != ["numericCode", "errorName"]:
            errors.append("compatibility.additiveFields must contain numericCode and errorName")

    analysis = spec.get("analysis")
    if not isinstance(analysis, dict):
        errors.append("analysis must be a mapping")
    else:
        if analysis.get("interval") != "60s":
            errors.append("analysis.interval must be 60s")
        if analysis.get("minimumHealthySamples") != 300:
            errors.append("analysis.minimumHealthySamples must be 300")
        halt_on = analysis.get("haltOn")
        if not isinstance(halt_on, dict):
            errors.append("analysis.haltOn must be a mapping")
        else:
            expected_halt = {
                "fiveHundredRateIncrease": 0.0005,
                "p99LatencyIncreaseMs": 10,
                "serializationFailureRate": 0,
                "unmappedErrorEmissionRate": 0,
            }
            for key, expected in expected_halt.items():
                if halt_on.get(key) != expected:
                    errors.append(f"analysis.haltOn.{key} must be {expected}")

    stages = spec.get("stages")
    if not isinstance(stages, list):
        errors.append("stages must be a list")
    else:
        actual_services: list[str] = []
        for index, stage in enumerate(stages):
            if not isinstance(stage, dict):
                errors.append(f"stages[{index}] must be a mapping")
                continue
            service = stage.get("service")
            if not isinstance(service, str):
                errors.append(f"stages[{index}].service must be a string")
                continue
            actual_services.append(service)
            if stage.get("name") != service:
                errors.append(f"stages[{index}].name must match service {service}")
            if stage.get("percentages") != EXPECTED_PERCENTAGES:
                errors.append(f"stages[{index}] must use 5/25/100 percentages")
            smoke = stage.get("smoke")
            if not isinstance(smoke, str):
                errors.append(f"stages[{index}] smoke path must exist: {smoke}")
            else:
                smoke_path = (ROOT / smoke).resolve()
                if not smoke_path.is_relative_to(ROOT) or not smoke_path.exists():
                    errors.append(f"stages[{index}] smoke path must be an existing repository path: {smoke}")
        if tuple(actual_services) != EXPECTED_SERVICES:
            errors.append(f"stages must cover {', '.join(EXPECTED_SERVICES)} in order")

    rollback = spec.get("rollback")
    if not isinstance(rollback, dict):
        errors.append("rollback must be a mapping")
    else:
        if rollback.get("maximumSeconds") != 60:
            errors.append("rollback.maximumSeconds must be 60")
        if rollback.get("preserveDatabaseState") is not True:
            errors.append("rollback.preserveDatabaseState must remain true")
        if rollback.get("trigger") != "any-analysis-halt":
            errors.append("rollback.trigger must be any-analysis-halt")
    return errors


def validate_ledger(text: str) -> list[str]:
    """Return diagnostics for append-only ledger rows and status safety rules."""
    errors: list[str] = []
    if "| Timestamp (UTC) | Service | Commit SHA | Stage |" not in text:
        errors.append("ledger must contain the canonical verification table header")
    if "Status: `NOT EXECUTED`" not in text:
        errors.append("ledger must retain an explicit rollback rehearsal status")

    rows = [line for line in text.splitlines() if line.startswith("| 20")]
    seen_services: set[str] = set()
    for row in rows:
        columns = [column.strip() for column in row.strip("|").split("|")]
        if len(columns) != 11:
            errors.append(f"ledger row has {len(columns)} columns: {row}")
            continue
        service = columns[1]
        status = columns[9]
        seen_services.add(service)
        if status not in {"NOT EXECUTED", "PASSED", "FAILED"}:
            errors.append(f"ledger status is invalid for {service}: {status}")
        if status == "PASSED":
            if columns[2] in {"", "—", "-"} or columns[10] in {"", "—", "-"}:
                errors.append(f"passed ledger row requires SHA and sign-off: {service}")
            if not re.fullmatch(r"\d+", columns[8]):
                errors.append(f"passed ledger row requires rollback seconds: {service}")
    normalized_services = {service.lower().replace(" ", "-") for service in seen_services}
    if seen_services and set(EXPECTED_SERVICES) != normalized_services:
        errors.append("ledger rows must cover all four rollout services")
    return errors


def main() -> int:
    """Run manifest and ledger validation and print actionable diagnostics."""
    try:
        errors = validate_manifest(load_mapping(MANIFEST_PATH))
        errors.extend(validate_ledger(LEDGER_PATH.read_text(encoding="utf-8")))
    except (OSError, ValueError, yaml.YAMLError) as error:
        print(f"error rollout invalid: {error}")
        return 1
    if errors:
        print("error rollout invalid:")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print("valid error rollout manifest and ledger structure: four services, 5/25/100 stages, 60-second rollback bound")
    return 0


if __name__ == "__main__":
    sys.exit(main())
