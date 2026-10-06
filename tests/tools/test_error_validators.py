"""Tests for ERRC-09 catalog, OpenAPI, and immutability gates."""

from __future__ import annotations

from copy import deepcopy
import json
from pathlib import Path
import unittest

from tools.contracts.detect_breaking_error_changes import find_breaking_changes
from tools.contracts.validate_openapi_parity import load_json, problem_schema, validate_documents
from tools.errors.validate_six_digit_catalog import load_records, validate_catalog_records
from tools.errors.validate_catalog import validate_domains_registry

ROOT = Path(__file__).resolve().parents[2]


class ErrorValidatorTest(unittest.TestCase):
    """Verify positive and negative ERRC-09 gate behavior."""

    def test_current_catalog_and_openapi_contracts_pass(self) -> None:
        """The current frozen catalog and REST contracts satisfy all gates."""
        domains = validate_domains_registry(ROOT / "contracts/errors/domains.yaml")
        self.assertEqual(validate_catalog_records(load_records(ROOT / "contracts/errors/error-catalog.yaml"), domains), [])
        files = [ROOT / f"contracts/rest/{name}.openapi.json" for name in ("accounts", "expense-core", "notifications")]
        self.assertEqual(validate_documents([load_json(path) for path in files], problem_schema(files[0])), [])

    def test_catalog_rejects_invalid_code_and_duplicate_name(self) -> None:
        """Invalid digits and duplicate symbolic identities fail closed."""
        domains = validate_domains_registry(ROOT / "contracts/errors/domains.yaml")
        records = load_records(ROOT / "contracts/errors/error-catalog.yaml")
        invalid = deepcopy(records[0])
        invalid["numericCode"] = "013201"
        invalid["errorName"] = records[1]["errorName"]
        duplicate = deepcopy(records[1])
        errors = validate_catalog_records([invalid, duplicate], domains)
        self.assertTrue(any("pattern" in error for error in errors))
        self.assertTrue(any("duplicate errorName" in error for error in errors))

    def test_openapi_validator_rejects_missing_status(self) -> None:
        """A missing predictable response is reported by operation identity."""
        path = ROOT / "contracts/rest/accounts.openapi.json"
        document = load_json(path)
        del document["paths"]["/me"]["get"]["responses"]["429"]
        errors = validate_documents([document], problem_schema(path))
        self.assertTrue(any("getMe missing 429" in error for error in errors))

    def test_breaking_detector_rejects_delete_and_renumber(self) -> None:
        """Published identities cannot be deleted or assigned a new number."""
        previous = [{"numericCode": "213201", "errorName": "GROUP_NOT_FOUND"}, {"numericCode": "213301", "errorName": "GROUP_CONFLICT"}]
        current = [{"numericCode": "213201", "errorName": "GROUP_NOT_FOUND"}, {"numericCode": "213302", "errorName": "GROUP_CONFLICT"}]
        changes = find_breaking_changes(previous, current)
        self.assertIn("numericCode 213301 was deleted", changes)
        self.assertIn("errorName GROUP_CONFLICT was renumbered", changes)

    def test_problem_schema_is_json(self) -> None:
        """The canonical schema remains parseable as JSON after parity work."""
        json.loads((ROOT / "contracts/errors/problem.schema.json").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
