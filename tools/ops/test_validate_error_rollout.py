"""Regression tests for the additive error-contract rollout validator."""

from __future__ import annotations

import copy
import unittest
from typing import Any

from tools.ops.validate_error_rollout import (
    MANIFEST_PATH,
    LEDGER_PATH,
    ROOT,
    load_mapping,
    validate_ledger,
    validate_manifest,
)


class ErrorRolloutValidationTest(unittest.TestCase):
    """Protect the rollout manifest and ledger safety invariants."""

    def test_checked_in_manifest_and_ledger_are_valid(self) -> None:
        """The checked-in pre-staging artifacts satisfy structural validation."""
        self.assertEqual(validate_manifest(load_mapping(MANIFEST_PATH)), [])
        self.assertEqual(validate_ledger(LEDGER_PATH.read_text(encoding="utf-8")), [])

    def test_manifest_rejects_changed_rollback_bound(self) -> None:
        """A rollback budget change must require an explicit review."""
        document: dict[str, Any] = copy.deepcopy(load_mapping(MANIFEST_PATH))
        spec = document["spec"]
        self.assertIsInstance(spec, dict)
        spec["rollback"]["maximumSeconds"] = 61
        errors = validate_manifest(document)
        self.assertIn("rollback.maximumSeconds must be 60", errors)

    def test_manifest_rejects_missing_service_stage(self) -> None:
        """Every deployable must retain a complete ordered canary sequence."""
        document: dict[str, Any] = copy.deepcopy(load_mapping(MANIFEST_PATH))
        spec = document["spec"]
        self.assertIsInstance(spec, dict)
        spec["stages"] = spec["stages"][:-1]
        errors = validate_manifest(document)
        self.assertTrue(any(error.startswith("stages must cover") for error in errors))

    def test_manifest_rejects_stage_name_drift(self) -> None:
        """Stage labels must remain aligned with the service rollout identity."""
        document: dict[str, Any] = copy.deepcopy(load_mapping(MANIFEST_PATH))
        spec = document["spec"]
        self.assertIsInstance(spec, dict)
        spec["stages"][0]["name"] = "wrong-service"
        errors = validate_manifest(document)
        self.assertIn("stages[0].name must match service accounts", errors)

    def test_manifest_rejects_smoke_path_outside_repository(self) -> None:
        """Smoke commands must not make validation read outside the repository."""
        document: dict[str, Any] = copy.deepcopy(load_mapping(MANIFEST_PATH))
        spec = document["spec"]
        self.assertIsInstance(spec, dict)
        spec["stages"][0]["smoke"] = str(ROOT.parent / "outside-smoke-path")
        errors = validate_manifest(document)
        self.assertIn(
            "stages[0] smoke path must be an existing repository path: "
            f"{ROOT.parent / 'outside-smoke-path'}",
            errors,
        )

    def test_ledger_rejects_unattested_passed_row(self) -> None:
        """A passed row must carry immutable SHA, metrics, and operator sign-off."""
        ledger = """| Timestamp (UTC) | Service | Commit SHA | Stage | 5xx delta | p99 delta (ms) | Serialization failures | Unmapped codes | Rollback seconds | Status | Sign-off |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n| 2026-10-07 | Accounts | — | 5% | — | — | — | — | — | PASSED | — |\n\nStatus: `NOT EXECUTED`\n"""
        errors = validate_ledger(ledger)
        self.assertIn("passed ledger row requires SHA and sign-off: Accounts", errors)


if __name__ == "__main__":
    unittest.main()
