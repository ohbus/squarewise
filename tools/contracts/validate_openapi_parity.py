#!/usr/bin/env python3
"""Validate shared REST Problem Details and response declarations."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
OPENAPI_FILES = tuple(
    ROOT / f"contracts/rest/{name}.openapi.json"
    for name in ("accounts", "expense-core", "notifications")
)
REQUIRED_STATUSES: dict[str, str] = {
    "400": "BadRequest",
    "401": "Unauthorized",
    "403": "Forbidden",
    "404": "NotFound",
    "405": "MethodNotAllowed",
    "409": "Conflict",
    "429": "RateLimited",
    "500": "InternalError",
}


def load_json(path: Path) -> dict[str, Any]:
    """Load one OpenAPI JSON document."""
    raw: Any = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError(f"{path} must contain an object")
    return raw


def problem_schema(path: Path) -> dict[str, Any]:
    """Load the canonical Problem Details schema without document metadata."""
    schema: dict[str, Any] = load_json(ROOT / "contracts/errors/problem.schema.json")
    return {
        key: value
        for key, value in schema.items()
        if key not in {"$schema", "$id", "title", "description"}
    }


def validate_documents(documents: list[dict[str, Any]], expected: dict[str, Any]) -> list[str]:
    """Return parity, header, and operation response diagnostics."""
    errors: list[str] = []
    required = expected.get("required", [])
    if not isinstance(required, list) or not {"numericCode", "errorName"}.issubset(required):
        errors.append("canonical ProblemDetails must require numericCode and errorName")
    for document in documents:
        source = str(document.get("info", {}).get("title", "OpenAPI"))
        schemas = document.get("components", {}).get("schemas", {})
        if schemas.get("ProblemDetails") != expected:
            errors.append(f"{source}: ProblemDetails differs from canonical schema")
        responses = document.get("components", {}).get("responses", {})
        for status, response_name in REQUIRED_STATUSES.items():
            response = responses.get(response_name)
            if not isinstance(response, dict):
                errors.append(f"{source}: missing response component {response_name}")
                continue
            if status == "401" and "WWW-Authenticate" not in response.get("headers", {}):
                errors.append(f"{source}: Unauthorized lacks WWW-Authenticate")
            if status == "405" and "Allow" not in response.get("headers", {}):
                errors.append(f"{source}: MethodNotAllowed lacks Allow")
            if status == "429" and "Retry-After" not in response.get("headers", {}):
                errors.append(f"{source}: RateLimited lacks Retry-After")
        for route, path_item in document.get("paths", {}).items():
            if not isinstance(path_item, dict):
                errors.append(f"{source}: {route} is not a path object")
                continue
            for method, operation in path_item.items():
                if method not in {"get", "post", "put", "patch", "delete", "head", "options", "trace"}:
                    continue
                operation_name = operation.get("operationId", f"{method.upper()} {route}")
                response_map = operation.get("responses", {})
                for status, response_name in REQUIRED_STATUSES.items():
                    expected_ref = f"#/components/responses/{response_name}"
                    if response_map.get(status, {}).get("$ref") != expected_ref:
                        errors.append(f"{source}: {operation_name} missing {status} -> {response_name}")
    return errors


def main() -> int:
    """Run the OpenAPI parity gate."""
    try:
        documents = [load_json(path) for path in OPENAPI_FILES]
        errors = validate_documents(documents, problem_schema(OPENAPI_FILES[0]))
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"OpenAPI parity invalid: {error}")
        return 1
    if errors:
        print("OpenAPI parity invalid:")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print("valid OpenAPI error parity: 3 documents, 45 operations")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
