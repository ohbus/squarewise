"""Unit tests for the provider-token orchestration in the OIDC negative harness."""

from __future__ import annotations

import sys
import unittest
from unittest.mock import call, patch

from tests.e2e import test_oidc_negative


class OidcNegativeHarnessTest(unittest.TestCase):
    """Keep deferred isolated-fixture setup compatible with the provider probe."""

    def test_provider_variant_allows_deferred_invalid_subject_fixture(self) -> None:
        """The normal-stack provider checks run before the isolated subject fixture exists."""
        with (
            patch.object(test_oidc_negative, "TOKEN", "signed-token"),
            patch.object(test_oidc_negative, "WRONG_ISSUER_TOKEN", "wrong-issuer"),
            patch.object(test_oidc_negative, "EXPIRED_TOKEN", "expired"),
            patch.object(test_oidc_negative, "INVALID_SUBJECT_TOKEN", ""),
            patch.object(test_oidc_negative, "run_variant", return_value=[]) as run_variant,
            patch.object(sys, "argv", ["test_oidc_negative.py", "--variant", "provider"]),
        ):
            self.assertEqual(test_oidc_negative.main(), 0)

        run_variant.assert_has_calls(
            [call("wrong-issuer", "wrong-issuer"), call("expired", "expired")]
        )
        self.assertEqual(run_variant.call_count, 2)


if __name__ == "__main__":
    unittest.main()
