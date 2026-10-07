"""Unit tests for error catalog validation tooling."""
from __future__ import annotations

from pathlib import Path
import unittest

from tools.errors.validate_catalog import (
    run_mock_catalog_validation,
    validate_catalog,
    validate_domains_registry,
    validate_legacy_record,
    validate_schema_file,
    validate_six_digit_record,
)

ROOT = Path(__file__).resolve().parents[2]


class ValidateCatalogTest(unittest.TestCase):
    """Test suite for error catalog validation and schema enforcement."""

    def test_domains_registry_is_frozen_and_valid(self) -> None:
        """Verify that domains.yaml exists and is frozen-authoritative."""
        domains_path = ROOT / "contracts/errors/domains.yaml"
        registry = validate_domains_registry(domains_path)
        self.assertEqual(registry.get("status"), "frozen-authoritative")
        self.assertIn(1, registry["domains"])
        self.assertIn(2, registry["domains"])
        self.assertIn(3, registry["domains"])
        self.assertIn(4, registry["domains"])
        self.assertIn(9, registry["domains"])
        self.assertEqual(set(registry["reserved"]["domains"]), {5, 6, 7, 8})

    def test_error_catalog_schema_json_is_valid(self) -> None:
        """Verify that error-catalog.schema.json has expected schema properties."""
        schema_path = ROOT / "contracts/errors/error-catalog.schema.json"
        schema = validate_schema_file(schema_path, "Squarewise Error Catalog")
        self.assertEqual(schema.get("$schema"), "https://json-schema.org/draft/2020-12/schema")
        self.assertIn("$defs", schema)
        self.assertIn("sixDigitErrorDefinition", schema["$defs"])
        self.assertIn("legacyErrorDefinition", schema["$defs"])

    def test_lifecycle_history_schema_json_is_valid(self) -> None:
        """Verify that lifecycle-history.schema.json has expected schema properties."""
        schema_path = ROOT / "contracts/errors/lifecycle-history.schema.json"
        schema = validate_schema_file(schema_path, "Squarewise Error Lifecycle History")
        self.assertEqual(schema.get("$schema"), "https://json-schema.org/draft/2020-12/schema")
        self.assertIn("$defs", schema)
        self.assertIn("lifecycleRecord", schema["$defs"])

    def test_mock_catalog_assertions_pass(self) -> None:
        """Verify built-in mock validation assertions."""
        run_mock_catalog_validation()

    def test_six_digit_validation_catches_invalid_codes(self) -> None:
        """Verify six-digit record validation catches format and invariant violations."""
        valid_record = {
            "numericCode": "113101",
            "errorName": "PROFILE_NOT_FOUND",
            "legacyCode": "NOT_FOUND",
            "domain": 1,
            "module": 1,
            "layer": 3,
            "category": 1,
            "sequence": 1,
            "title": "Profile not found",
            "safeDetail": "User profile not found.",
            "messageKey": "error.profile.not_found",
            "owner": "Accounts",
            "source": "squarewise-accounts",
            "component": "profile",
            "operation": "get-profile",
            "transports": ["REST"],
            "httpStatus": 404,
            "retryPolicy": "NEVER",
            "severity": "WARNING",
            "introducedIn": "1.0.0",
        }
        self.assertEqual(validate_six_digit_record(valid_record), [])

        invalid_public_code = dict(valid_record, legacyCode="ERR-05")
        self.assertTrue(any("public symbolic code pattern" in e for e in validate_six_digit_record(invalid_public_code)))

        # Sequence 00 is forbidden
        bad_seq = dict(valid_record, sequence="00", numericCode="113100")
        self.assertTrue(len(validate_six_digit_record(bad_seq)) > 0)

        # Digit 0 is forbidden
        bad_digit = dict(valid_record, domain=0, numericCode="013101")
        self.assertTrue(len(validate_six_digit_record(bad_digit)) > 0)

        # Missing httpStatus for REST transport is rejected
        no_status = dict(valid_record)
        del no_status["httpStatus"]
        self.assertTrue(any("httpStatus is required" in e for e in validate_six_digit_record(no_status)))

        # Missing runbook for CRITICAL is rejected
        crit_no_runbook = dict(valid_record, severity="CRITICAL")
        self.assertTrue(any("Runbook is required" in e for e in validate_six_digit_record(crit_no_runbook)))

    def test_legacy_validation_and_current_catalog(self) -> None:
        """Verify legacy record validation and current error-catalog.yaml."""
        valid_legacy = {
            "code": "ERR-01",
            "service": "core",
            "component": "expense-core",
            "operation": "generic",
            "httpStatus": 500,
            "severity": "critical",
            "retryable": False,
            "safeDetail": "Internal server error",
        }
        self.assertEqual(validate_legacy_record(valid_legacy), [])

        catalog_path = ROOT / "contracts/errors/error-catalog.yaml"
        count, errors = validate_catalog(catalog_path)
        self.assertEqual(errors, [])
        self.assertEqual(count, 99)

    def test_lifecycle_history_retires_err_12(self) -> None:
        """Verify lifecycle history records ERR-12 retirement without replacement."""
        import yaml  # type: ignore[import-untyped]
        lifecycle_path = ROOT / "contracts/errors/lifecycle-history.yaml"
        self.assertTrue(lifecycle_path.exists())
        records = yaml.safe_load(lifecycle_path.read_text(encoding="utf-8"))
        self.assertTrue(isinstance(records, list))
        err_12 = next((r for r in records if r["code"] == "ERR-12"), None)
        self.assertIsNotNone(err_12)
        self.assertEqual(err_12["status"], "RETIRED")
        self.assertIsNone(err_12["replacedBy"])


if __name__ == "__main__":
    unittest.main()
