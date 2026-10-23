"""Tests for the stable required CI result."""

from __future__ import annotations

import unittest

from tools.ci.required_ci_gate import evaluate_results, main


class RequiredCiGateTest(unittest.TestCase):
    """Verify success, intentional skips, and blocking outcomes."""

    def test_success_and_skipped_jobs_pass(self) -> None:
        passed, failures = evaluate_results({"lint": "success", "verify": "skipped"})
        self.assertTrue(passed)
        self.assertEqual(failures, ())

    def test_failure_and_cancellation_block(self) -> None:
        passed, failures = evaluate_results({"lint": "failure", "e2e": "cancelled"})
        self.assertFalse(passed)
        self.assertEqual(
            failures,
            ("lint result was failure", "e2e result was cancelled"),
        )

    def test_command_returns_failure_for_non_passing_result(self) -> None:
        self.assertEqual(main(("--result", "lint=timed_out")), 1)


if __name__ == "__main__":
    unittest.main()
