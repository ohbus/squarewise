#!/usr/bin/env python3
"""Validate REST path structure and GraphQL schema/resolver parity."""

from __future__ import annotations

import json
import re
from pathlib import Path

HTTP_METHODS = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}
PATH_ITEM_KEYS = HTTP_METHODS | {"summary", "description", "servers", "parameters", "$ref"}
GRAPHQL_ROOTS = {"Query": "QueryMapping", "Mutation": "MutationMapping", "Subscription": "SubscriptionMapping"}
GRAPHQL_FIELD = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*(?:\([^)]*\))?\s*:")
GRAPHQL_RESOLVER = re.compile(
    r"@(Query|Mutation|Subscription)Mapping(?:\([^)]*\))?"
    r"(?:[ \t]*\r?\n[ \t]*)*[ \t]*fun[ \t]+([A-Za-z_][A-Za-z0-9_]*)"
)
BRUNO_REQUEST = re.compile(r"(?m)^(get|post|put|patch|delete)\s*\{\s*\n\s*url:\s*([^\n]+)")
MATRIX_OPERATION = re.compile(r"^\|[^|]+\|[^|]+\|[^|]+\|\s*`([^`]+)`\s*\|", re.MULTILINE)


def normalized_route(value: str) -> str:
    """Normalize Bruno variables and OpenAPI path parameters for comparison."""
    value = re.sub(r"\{\{[^}]+\}\}", "{param}", value.strip())
    return re.sub(r"\{[^}]+\}", "{param}", value)


def validate_bruno_rest(errors: list[str], operations: dict[tuple[str, str], str]) -> tuple[int, int]:
    """Ensure every contracted REST operation has an ordered Bruno request."""
    requests: set[tuple[str, str]] = set()
    asserted = 0
    for path in Path("tools/bruno").rglob("*.bru"):
        contents = path.read_text(encoding="utf-8")
        for method, url in BRUNO_REQUEST.findall(contents):
            requests.add((method.upper(), normalized_route(url)))
            asserted += "tests {" in contents
    missing = []
    for (method, route), operation_id in sorted(operations.items()):
        if not any(method == found_method and found_route.endswith(normalized_route(route)) for found_method, found_route in requests):
            missing.append(f"{operation_id} ({method} {route})")
    for operation in missing:
        errors.append(f"REST operation has no Bruno request: {operation}")
    return len(requests), asserted


def validate_operation_matrix(errors: list[str], operations: dict[tuple[str, str], str]) -> None:
    """Ensure the human-readable matrix has one row for every OpenAPI operation."""
    matrix_path = Path("docs/quality/public-interface-operation-matrix.md")
    if not matrix_path.exists():
        errors.append(f"missing operation evidence matrix: {matrix_path}")
        return
    listed = set(MATRIX_OPERATION.findall(matrix_path.read_text(encoding="utf-8")))
    expected = set(operations.values())
    for operation_id in sorted(expected - listed):
        errors.append(f"operation evidence matrix is missing: {operation_id}")
    for operation_id in sorted(listed - expected):
        errors.append(f"operation evidence matrix has unknown operation: {operation_id}")


def graphql_fields(schema: str) -> dict[str, set[str]]:
    """Extract root operation fields from the repository GraphQL schema."""
    fields: dict[str, set[str]] = {}
    for root in GRAPHQL_ROOTS:
        match = re.search(rf"type\s+{root}\s*\{{([^}}]*)\}}", schema, re.DOTALL)
        if match:
            fields[root] = set(GRAPHQL_FIELD.findall(match.group(1)))
    return fields


def validate_graphql(errors: list[str]) -> int:
    """Ensure every declared root field has exactly one annotated BFF resolver."""
    schema_files = sorted(Path("contracts/graphql").glob("*.graphqls"))
    sources = "\n".join(
        path.read_text(encoding="utf-8") for path in Path("app/bff/src/main/kotlin").rglob("*.kt")
    )
    schema = "\n".join(path.read_text(encoding="utf-8") for path in schema_files)
    declared = graphql_fields(schema)
    implemented: dict[str, list[str]] = {root: [] for root in GRAPHQL_ROOTS}
    for root, function in GRAPHQL_RESOLVER.findall(sources):
        implemented[root].append(function)
    for root, fields in declared.items():
        actual = set(implemented[root])
        for field in sorted(fields - actual):
            errors.append(f"GraphQL {root}.{field} is declared but has no @{GRAPHQL_ROOTS[root]} resolver")
        for field in sorted(actual - fields):
            errors.append(f"GraphQL {root}.{field} has a resolver but is not declared in the schema")
    return sum(len(fields) for fields in declared.values())


def main() -> int:
    errors: list[str] = []
    operation_count = 0
    operations: dict[tuple[str, str], str] = {}
    for path in sorted(Path("contracts/rest").glob("*.json")):
        document = json.loads(path.read_text(encoding="utf-8"))
        paths = document.get("paths", {})
        if not isinstance(paths, dict):
            errors.append(f"{path}: paths must be an object")
            continue
        for route, item in paths.items():
            if not route.startswith("/"):
                errors.append(f"{path}: non-path key under paths: {route}")
                continue
            if not isinstance(item, dict):
                errors.append(f"{path}: path item is not an object: {route}")
                continue
            for key in item:
                if key.startswith("/"):
                    errors.append(f"{path}: nested path {key!r} under {route!r}")
                elif key not in PATH_ITEM_KEYS and not key.startswith("x-"):
                    errors.append(f"{path}: invalid path-item key {key!r} under {route!r}")
            operation_count += sum(key in HTTP_METHODS for key in item)
            for method, operation in item.items():
                if method in HTTP_METHODS and isinstance(operation, dict):
                    operation_id = operation.get("operationId")
                    if operation_id:
                        operations[(method.upper(), route)] = operation_id

    if errors:
        print("public surface invalid:")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    graphql_count = validate_graphql(errors)
    validate_operation_matrix(errors, operations)
    bruno_count, bruno_asserted = validate_bruno_rest(errors, operations)
    if errors:
        print("public surface invalid:")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print(f"valid REST public surface: {operation_count} operations")
    print(f"valid GraphQL public surface: {graphql_count} root operations")
    print(f"valid Bruno REST request inventory: {bruno_count} requests ({bruno_asserted} assertion-backed)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
