"""Tests for changed-scope CI planning."""

from __future__ import annotations

import unittest
from typing import cast

from tools.ci.changed_scope import compute_scope


class ChangedScopeTest(unittest.TestCase):
    """Verify conservative module and E2E selection behavior."""

    def test_master_selects_everything(self) -> None:
        scope = compute_scope((), full_run=True)
        self.assertTrue(scope["full_scope"])
        self.assertTrue(scope["has_verify"])
        self.assertTrue(scope["run_edge"])
        self.assertTrue(scope["run_product"])
        self.assertTrue(scope["run_chaos"])
        self.assertEqual(len(cast(list[dict[str, str]], scope["matrix"])), 9)

    def test_documentation_only_change_has_no_module_or_e2e_scope(self) -> None:
        scope = compute_scope(("docs/operations/ci.md",))
        self.assertFalse(scope["full_scope"])
        self.assertFalse(scope["has_verify"])
        self.assertFalse(scope["has_e2e"])

    def test_shared_security_change_selects_all_consuming_apps(self) -> None:
        scope = compute_scope(("libs/security/src/main/kotlin/Security.kt",))
        names = {row["name"] for row in scope["matrix"]}
        self.assertEqual(names, {"accounts", "expense-core", "notifications", "bff", "security"})
        self.assertTrue(scope["run_edge"])
        self.assertTrue(scope["run_product"])
        self.assertTrue(scope["run_chaos"])

    def test_e2e_stream_change_does_not_trigger_gradle_modules(self) -> None:
        scope = compute_scope(("tests/e2e/test_auth_email_delivery.py",))
        self.assertFalse(scope["has_verify"])
        self.assertFalse(scope["run_edge"])
        self.assertTrue(scope["run_product"])
        self.assertFalse(scope["run_chaos"])

    def test_branch_build_change_is_full_scope(self) -> None:
        scope = compute_scope(("gradle/libs.versions.toml",))
        self.assertTrue(scope["full_scope"])
        self.assertEqual(len(cast(list[dict[str, str]], scope["matrix"])), 9)


if __name__ == "__main__":
    unittest.main()
