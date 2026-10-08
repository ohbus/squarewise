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
_TIMESTAMP = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$"
)


def assert_problem_details(
    label: str,
    status: int,
    body: object,
    expected_status: int,
    expected_code: str | None = None,
) -> None:
    """Verify all canonical RFC 9457 and additive identity fields on a live REST error response."""
    if status != expected_status:
        raise AssertionError(f"{label}: expected HTTP {expected_status}, got {status}")
    if not isinstance(body, Mapping):
        raise AssertionError(f"{label}: expected a JSON object, got {type(body).__name__}")

    # Validate required Problem Details fields according to contracts/errors/problem.schema.json
    problem_type = body.get("type")
    title = body.get("title")
    body_status = body.get("status")
    code = body.get("code")
    numeric_code = body.get("numericCode")
    error_name = body.get("errorName")
    source = body.get("source")
    request_id = body.get("requestId")
    detail = body.get("detail")
    timestamp = body.get("timestamp")
    instance = body.get("instance")
    violations = body.get("violations")

    if not isinstance(problem_type, str) or not problem_type:
        raise AssertionError(f"{label}: missing or invalid type {problem_type!r}")
    if not isinstance(title, str) or not title:
        raise AssertionError(f"{label}: missing or invalid title {title!r}")
    if not isinstance(body_status, int) or body_status != expected_status:
        raise AssertionError(f"{label}: expected body status {expected_status}, got {body_status}")
    if not isinstance(code, str) or not code:
        raise AssertionError(f"{label}: missing symbolic code {code!r}")
    if expected_code is not None and code != expected_code:
        raise AssertionError(f"{label}: expected code {expected_code}, got {code}")
    if not isinstance(numeric_code, str) or not _NUMERIC_CODE.fullmatch(numeric_code):
        raise AssertionError(f"{label}: invalid numericCode {numeric_code!r}")
    if not isinstance(error_name, str) or not _ERROR_NAME.fullmatch(error_name):
        raise AssertionError(f"{label}: invalid errorName {error_name!r}")
    if not isinstance(source, str) or not source:
        raise AssertionError(f"{label}: missing or invalid source {source!r}")
    if not isinstance(request_id, str) or not _REQUEST_ID.fullmatch(request_id):
        raise AssertionError(f"{label}: invalid requestId {request_id!r}")
    if not isinstance(detail, str) or not detail:
        raise AssertionError(f"{label}: missing or invalid detail {detail!r}")
    if not isinstance(timestamp, str) or not _TIMESTAMP.fullmatch(timestamp):
        raise AssertionError(f"{label}: missing or invalid ISO-8601 timestamp {timestamp!r}")
    if instance is not None and not isinstance(instance, str):
        raise AssertionError(f"{label}: invalid instance URI {instance!r}")
    if violations is not None:
        if not isinstance(violations, list):
            raise AssertionError(f"{label}: violations must be a list, got {type(violations).__name__}")
        for idx, violation in enumerate(violations):
            if not isinstance(violation, Mapping):
                raise AssertionError(f"{label}: violation[{idx}] must be an object")
            if not isinstance(violation.get("field"), str):
                raise AssertionError(f"{label}: violation[{idx}].field must be a string")
            if not isinstance(violation.get("message"), str):
                raise AssertionError(f"{label}: violation[{idx}].message must be a string")

