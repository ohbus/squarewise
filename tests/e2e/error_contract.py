"""Assertions for additive REST Problem Details compatibility."""

from __future__ import annotations

import re
from collections.abc import Mapping


_NUMERIC_CODE = re.compile(r"^[1-9][0-9]{5}$")
_ERROR_NAME = re.compile(r"^[A-Z0-9_]{3,64}$")
_REQUEST_ID = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    re.IGNORECASE,
)


def assert_problem_details(
    label: str,
    status: int,
    body: object,
    expected_status: int,
    expected_code: str | None = None,
) -> None:
    """Verify additive identity fields on one live REST error response."""
    if status != expected_status:
        raise AssertionError(f"{label}: expected HTTP {expected_status}, got {status}")
    if not isinstance(body, Mapping):
        raise AssertionError(f"{label}: expected a JSON object, got {type(body).__name__}")
    code = body.get("code")
    numeric_code = body.get("numericCode")
    error_name = body.get("errorName")
    request_id = body.get("requestId")
    if not isinstance(code, str) or not code:
        raise AssertionError(f"{label}: missing symbolic code")
    if expected_code is not None and code != expected_code:
        raise AssertionError(f"{label}: expected code {expected_code}, got {code}")
    if not isinstance(numeric_code, str) or not _NUMERIC_CODE.fullmatch(numeric_code):
        raise AssertionError(f"{label}: invalid numericCode {numeric_code!r}")
    if not isinstance(error_name, str) or not _ERROR_NAME.fullmatch(error_name):
        raise AssertionError(f"{label}: invalid errorName {error_name!r}")
    if not isinstance(request_id, str) or not _REQUEST_ID.fullmatch(request_id):
        raise AssertionError(f"{label}: invalid requestId {request_id!r}")
