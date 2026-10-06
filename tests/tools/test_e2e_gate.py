"""Unit tests for the selected E2E aggregate gate."""

from __future__ import annotations

import unittest

from tools.ci.e2e_gate import evaluate_gate


class E2EGateTest(unittest.TestCase):
    """Verify selected and unselected workflow result semantics."""

    def test_unselected_skipped_stream_is_neutral(self) -> None:
        """A skipped stream that was not selected does not fail or pass the gate."""

        passed, failures = evaluate_gate(
            preflight="success",
            artifacts="success",
            selected={"edge": True, "product": False, "chaos": False},
            results={"edge": "success", "product": "skipped", "chaos": "skipped"},
        )

        self.assertTrue(passed)
        self.assertEqual(failures, ())

    def test_selected_failure_fails_gate(self) -> None:
        """A selected stream failure is a gate failure."""

        passed, failures = evaluate_gate(
            preflight="success",
            artifacts="success",
            selected={"edge": False, "product": True, "chaos": False},
            results={"edge": "skipped", "product": "failure", "chaos": "skipped"},
        )

        self.assertFalse(passed)
        self.assertEqual(failures, ("selected product E2E result was failure",))

    def test_shared_failures_fail_even_without_selected_streams(self) -> None:
        """Preflight and artifact failures remain fatal for the aggregate."""

        passed, failures = evaluate_gate(
            preflight="failure",
            artifacts="cancelled",
            selected={"edge": False, "product": False, "chaos": False},
            results={"edge": "skipped", "product": "skipped", "chaos": "skipped"},
        )

        self.assertFalse(passed)
        self.assertEqual(
            failures,
            (
                "shared preflight result was failure",
                "E2E artifact preparation result was cancelled",
            ),
        )
