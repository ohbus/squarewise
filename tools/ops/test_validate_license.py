"""Regression tests for repository license validation."""

from __future__ import annotations

import unittest
from typing import Any

from tools.ops.validate_license import (
    validate_license_text,
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

    def test_metadata_requires_exact_license_and_file(self) -> None:
        """Package metadata must identify the exact license and root text."""
        document: dict[str, Any] = {
            "project": {"license": "MIT", "license-files": ["LICENSE"]}
        }
        findings = validate_project_metadata(document)
        self.assertIn("pyproject.toml project.license must be AGPL-3.0-only", findings)

    def test_notice_preserves_first_party_copyright(self) -> None:
        """The canonical license is paired with an explicit project copyright notice."""
        text = "Copyright (C) 2026 Subhrodip Mohanta\nAGPL-3.0-only"
        self.assertEqual(validate_notice_text(text), [])

    def test_active_references_reject_stale_mit_policy(self) -> None:
        """Historical tracker entries are excluded; active policy text is not."""
        findings = validate_references({"README.md": "No GPL in MIT project"})
        self.assertEqual(len(findings), 1)


if __name__ == "__main__":
    unittest.main()
