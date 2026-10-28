"""Regression tests for repository license validation."""

from __future__ import annotations

import unittest
from typing import Any

from tools.ops.validate_license import (
    REFERENCE_PATHS,
    ROOT,
    validate_license_text,
    validate_license_bytes,
    validate_notice_text,
    validate_project_metadata,
    validate_references,
)


class LicenseValidationTest(unittest.TestCase):
    """Protect project-license and metadata invariants."""

    def test_agpl_license_text_is_accepted(self) -> None:
        """A canonical AGPL heading and section 13 satisfy the license guard."""
        text = "GNU AFFERO GENERAL PUBLIC LICENSE\nVersion 3\nRemote Network Interaction"
        self.assertEqual(validate_license_text(text), [])

    def test_mit_text_is_rejected(self) -> None:
        """The old MIT grant cannot remain in the authoritative license file."""
        findings = validate_license_text("MIT License\nPermission is hereby granted")
        self.assertIn("LICENSE still contains MIT license text", findings)

    def test_truncated_license_is_rejected(self) -> None:
        """Marker phrases alone must not authorize a modified license file."""
        data = b"GNU AFFERO GENERAL PUBLIC LICENSE\nVersion 3\nRemote Network Interaction"
        findings = validate_license_bytes(data)
        self.assertIn("LICENSE does not match the canonical AGPL-3.0-only text", findings)

    def test_metadata_requires_exact_license_and_file(self) -> None:
        """Package metadata must identify the exact license and root text."""
        document: dict[str, Any] = {
            "project": {"license": "MIT", "license-files": ["LICENSE"]}
        }
        findings = validate_project_metadata(document)
        self.assertIn("pyproject.toml project.license must be AGPL-3.0-only", findings)

    def test_metadata_requires_notice_file(self) -> None:
        """Built distributions must retain both the license and copyright notice."""
        document: dict[str, Any] = {
            "project": {"license": "AGPL-3.0-only", "license-files": ["LICENSE"]}
        }
        findings = validate_project_metadata(document)
        self.assertIn("pyproject.toml project.license-files must include LICENSE and NOTICE", findings)

    def test_notice_preserves_first_party_copyright(self) -> None:
        """The canonical license is paired with an explicit project copyright notice."""
        text = "Copyright (C) 2026 Subhrodip Mohanta\nAGPL-3.0-only"
        self.assertEqual(validate_notice_text(text), [])

    def test_active_references_reject_stale_mit_policy(self) -> None:
        """Historical tracker entries are excluded; active policy text is not."""
        findings = validate_references({"README.md": "AGPL-3.0-only; Squarewise is MIT-licensed"})
        self.assertEqual(len(findings), 1)

        findings = validate_references({"README.md": "AGPL-3.0-only; Squarewise is licensed under MIT."})
        self.assertEqual(len(findings), 1)

        findings = validate_references(
            {"README.md": "AGPL-3.0-only; Squarewise is licensed under the MIT License."}
        )
        self.assertEqual(len(findings), 1)

    def test_active_references_allow_third_party_mit_terms(self) -> None:
        """Third-party MIT terms do not count as stale Squarewise licensing."""
        text = "AGPL-3.0-only; permitted third-party packages may remain MIT-licensed."
        self.assertEqual(validate_references({"docs/operations/licensing.md": text}), [])

    def test_active_references_require_current_license_marker(self) -> None:
        """Every active reference must identify the current project license."""
        findings = validate_references({"CONTRIBUTING.md": "Please read the license."})
        self.assertEqual(findings, ["CONTRIBUTING.md must identify AGPL-3.0-only"])

    def test_operations_index_is_an_active_reference(self) -> None:
        """The operations index remains covered by the repository-level guard."""
        self.assertIn(ROOT / "docs" / "operations" / "README.md", REFERENCE_PATHS)


if __name__ == "__main__":
    unittest.main()
