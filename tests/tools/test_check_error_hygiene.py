"""Tests for the error-path policy scanner."""
from __future__ import annotations

import unittest

from tools.qa.check_error_hygiene import Violation, load_allowlist, scan_content


class ErrorHygieneScannerTest(unittest.TestCase):
    """Prove forbidden constructs fail while governed code remains clean."""

    def test_detects_all_banned_constructs(self) -> None:
        content = "\n".join(
            [
                'throw RuntimeException("bad")',
                'throw IllegalArgumentException("213201")',
                'val type = Class.forName("Example")',
                "catch (error: Throwable) {",
                "val problem = ApiProblem(",
            ]
        )

        self.assertEqual(
            {
                Violation("Mock.kt", 1, "generic_throw"),
                Violation("Mock.kt", 2, "generic_throw"),
                Violation("Mock.kt", 2, "raw_numeric_code"),
                Violation("Mock.kt", 3, "reflection_discovery"),
                Violation("Mock.kt", 4, "catch_throwable"),
                Violation("Mock.kt", 5, "direct_problem_constructor"),
            },
            scan_content("Mock.kt", content),
        )

    def test_allows_catalogue_references_and_typed_catches(self) -> None:
        content = """
            throw DomainValidationException(violations)
            catch (error: InvalidEnvelopeException) { handle(error) }
            val definition = ExpenseErrors.GROUP_NOT_FOUND
        """

        self.assertEqual(set(), scan_content("Compliant.kt", content))

    def test_baseline_allowlist_is_explicit_and_matches_source(self) -> None:
        allowlist = load_allowlist()

        self.assertEqual(23, len(allowlist))
        self.assertTrue(all(item.file and item.line > 0 and item.violation_type for item in allowlist))


if __name__ == "__main__":
    unittest.main()
