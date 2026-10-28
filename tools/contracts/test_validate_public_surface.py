"""Regression tests for public-surface parsing boundaries."""

from __future__ import annotations

import subprocess
import sys
import unittest

from tools.contracts.validate_public_surface import GRAPHQL_RESOLVER


class PublicSurfacePatternTest(unittest.TestCase):
    """Protect GraphQL resolver discovery from ambiguous whitespace matching."""

    def test_resolver_pattern_accepts_multiline_annotations(self) -> None:
        """Resolver annotations may be separated from functions by blank lines."""
        source = "@QueryMapping\n\n    fun groups"
        self.assertEqual(GRAPHQL_RESOLVER.findall(source), [("Query", "groups")])

    def test_resolver_pattern_rejects_non_resolver_text(self) -> None:
        """A long newline-only gap without a function must not match."""
        source = "@QueryMapping\n" + ("\n" * 1024) + "not_a_function"
        self.assertEqual(GRAPHQL_RESOLVER.findall(source), [])

    def test_resolver_pattern_rejects_long_space_run_within_timeout(self) -> None:
        """A nonmatch with long horizontal whitespace must remain bounded."""
        source = "@QueryMapping\n" + (" " * 32_000) + "not_a_function"
        script = (
            "import sys; "
            "from tools.contracts.validate_public_surface import GRAPHQL_RESOLVER; "
            "assert GRAPHQL_RESOLVER.findall(sys.argv[1]) == []"
        )
        subprocess.run(
            [sys.executable, "-c", script, source],
            check=True,
            timeout=2.0,
        )


if __name__ == "__main__":
    unittest.main()
