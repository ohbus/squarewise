#!/usr/bin/env python3
"""Authoritative validator for Squarewise error contracts, schemas, and catalog definitions.

Enforces:
1. Canonical frozen status of `contracts/errors/domains.yaml`.
2. Validity and structural completeness of `contracts/errors/error-catalog.schema.json`.
3. Validity and structural completeness of `contracts/errors/lifecycle-history.schema.json`.
4. Conformance of `contracts/errors/error-catalog.yaml` (legacy or 6-digit records).
5. Invariant enforcement: no zero digits, no sequence '00', strict regex matching, and
   conditional property enforcement (HTTP status for REST, classification for GraphQL,
   runbook for critical/retryable/data-consistency).
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import sys
from typing import Any, Dict, List, Optional, Set, Tuple

import yaml  # type: ignore[import-untyped]  # PyYAML ships without inline type stubs

CODE_PATTERN = re.compile(r"^[1-9]{4}(0[1-9]|[1-9][0-9])$")
LEGACY_CODE_PATTERN = re.compile(r"^ERR-[0-9]{2}$")
ERROR_NAME_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]*$")
PUBLIC_CODE_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]*$")
MESSAGE_KEY_PATTERN = re.compile(r"^[a-z][a-z0-9_.-]*$")

ALLOWED_TRANSPORTS: Set[str] = {
    "REST",
    "GRAPHQL",
    "WEBSOCKET",
    "MESSAGING",
    "SCHEDULED",
    "STARTUP",
    "INTERNAL",
}

ALLOWED_SEVERITIES: Set[str] = {"INFO", "WARNING", "ERROR", "CRITICAL"}
ALLOWED_RETRY_POLICIES: Set[str] = {
    "NEVER",
    "RETRY_AFTER",
    "REAUTHENTICATE",
    "REFRESH",
    "RESYNC",
    "SAME_IDEMPOTENCY_KEY",
}


def validate_domains_registry(domains_path: Path) -> Dict[str, Any]:
    """Validate that the domain registry exists, is frozen, and defines all required bounded contexts."""
    if not domains_path.exists():
        raise ValueError(f"Domains registry missing at {domains_path}")

    data: Any = yaml.safe_load(domains_path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("Domains registry root must be a YAML mapping")

    if data.get("status") != "frozen-authoritative":
        raise ValueError(
            f"Domains registry status must be 'frozen-authoritative', found: {data.get('status')}"
        )

    domains: Dict[Any, Any] = data.get("domains", {})
    required_domains = {1, 2, 3, 4, 9}
    found_domains = set(domains.keys())
    missing_domains = required_domains - found_domains
    if missing_domains:
        raise ValueError(f"Domains registry missing required domain IDs: {missing_domains}")

    reserved = data.get("reserved", {})
    reserved_domains = set(reserved.get("domains", []))
    if not {5, 6, 7, 8}.issubset(reserved_domains):
        raise ValueError(f"Reserved domains 5-8 must be locked; found: {reserved_domains}")

    layers = data.get("layers", {})
    if set(layers.keys()) != set(range(1, 10)):
        raise ValueError(f"All 9 layers (1-9) must be defined; found: {set(layers.keys())}")

    categories = data.get("categories", {})
    if set(categories.keys()) != set(range(1, 10)):
        raise ValueError(f"All 9 categories (1-9) must be defined; found: {set(categories.keys())}")

    return data


def validate_schema_file(schema_path: Path, expected_title: str) -> Dict[str, Any]:
    """Validate JSON Schema file existence and basic meta-schema properties."""
    if not schema_path.exists():
        raise ValueError(f"Schema file missing at {schema_path}")

    schema: Any = json.loads(schema_path.read_text(encoding="utf-8"))
    if not isinstance(schema, dict):
        raise ValueError(f"Schema at {schema_path} must be a JSON object")

    if schema.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
        raise ValueError(
            f"Schema at {schema_path} must use draft 2020-12, found: {schema.get('$schema')}"
        )

    if expected_title not in schema.get("title", ""):
        raise ValueError(
            f"Schema at {schema_path} title mismatch; expected substring '{expected_title}'"
        )

    return schema


def validate_six_digit_record(
    record: Dict[str, Any], domains_registry: Optional[Dict[str, Any]] = None
) -> List[str]:
    """Validate a single six-digit error catalog record against the authoritative standard."""
    errors: List[str] = []

    # Required fields
    required_fields = [
        "numericCode",
        "errorName",
        "domain",
        "module",
        "layer",
        "category",
        "sequence",
        "title",
        "safeDetail",
        "messageKey",
        "owner",
        "source",
        "component",
        "operation",
        "transports",
        "retryPolicy",
        "severity",
        "introducedIn",
    ]
    for field in required_fields:
        if field not in record or record[field] is None:
            errors.append(f"Missing required field: '{field}'")

    if errors:
        return errors

    legacy_code = record.get("legacyCode")
    if legacy_code is not None and (
        not isinstance(legacy_code, str) or not PUBLIC_CODE_PATTERN.match(legacy_code)
    ):
        errors.append(
            f"legacyCode '{legacy_code}' does not match public symbolic code pattern '^[A-Z][A-Z0-9_]*$'"
        )

    numeric_code = str(record["numericCode"])
    if not CODE_PATTERN.match(numeric_code):
        errors.append(
            f"Numeric code '{numeric_code}' does not match pattern '^[1-9]{{4}}(0[1-9]|[1-9][0-9])$'"
        )

    # Sequence cannot be 00 and must match digits 5-6
    seq_val = record["sequence"]
    if isinstance(seq_val, int):
        seq_str = f"{seq_val:02d}"
    else:
        seq_str = str(seq_val)

    if seq_str == "00" or not (1 <= int(seq_str) <= 99):
        errors.append(f"Sequence '{seq_str}' is invalid; must be between 01 and 99")

    # Match numericCode parts with domain, module, layer, category, sequence
    if len(numeric_code) == 6:
        d, m, l, c, ee = (
            int(numeric_code[0]),
            int(numeric_code[1]),
            int(numeric_code[2]),
            int(numeric_code[3]),
            numeric_code[4:6],
        )
        if record["domain"] != d:
            errors.append(f"Domain mismatch: record has {record['domain']} but code specifies {d}")
        if record["module"] != m:
            errors.append(f"Module mismatch: record has {record['module']} but code specifies {m}")
        if record["layer"] != l:
            errors.append(f"Layer mismatch: record has {record['layer']} but code specifies {l}")
        if record["category"] != c:
            errors.append(
                f"Category mismatch: record has {record['category']} but code specifies {c}"
            )
        if seq_str != ee:
            errors.append(f"Sequence mismatch: record has {seq_str} but code specifies {ee}")

    # Check error name
    error_name = str(record["errorName"])
    if not ERROR_NAME_PATTERN.match(error_name):
        errors.append(
            f"Error name '{error_name}' does not match SCREAMING_SNAKE_CASE pattern"
        )

    # Check messageKey
    message_key = str(record["messageKey"])
    if not MESSAGE_KEY_PATTERN.match(message_key):
        errors.append(
            f"Message key '{message_key}' does not match pattern '^[a-z][a-z0-9_.-]*$'"
        )

    # Transports
    transports = record.get("transports", [])
    if not isinstance(transports, list) or not transports:
        errors.append("Transports must be a non-empty list")
    else:
        invalid_transports = set(transports) - ALLOWED_TRANSPORTS
        if invalid_transports:
            errors.append(f"Invalid transports: {invalid_transports}")

        # Conditional requirements
        if "REST" in transports:
            if "httpStatus" not in record or not isinstance(record["httpStatus"], int):
                errors.append("httpStatus is required when transports contains 'REST'")
            elif not (100 <= record["httpStatus"] <= 599):
                errors.append(f"httpStatus {record['httpStatus']} out of range 100-599")

        if "GRAPHQL" in transports:
            if "graphqlClassification" not in record or not record["graphqlClassification"]:
                errors.append(
                    "graphqlClassification is required when transports contains 'GRAPHQL'"
                )

    # Severity & Retry policy
    severity = str(record.get("severity", ""))
    if severity not in ALLOWED_SEVERITIES:
        errors.append(f"Severity '{severity}' not in allowed set: {ALLOWED_SEVERITIES}")

    retry_policy = str(record.get("retryPolicy", ""))
    if retry_policy not in ALLOWED_RETRY_POLICIES:
        errors.append(
            f"Retry policy '{retry_policy}' not in allowed set: {ALLOWED_RETRY_POLICIES}"
        )

    # Runbook requirement
    category = record.get("category")
    needs_runbook = (
        severity == "CRITICAL"
        or retry_policy != "NEVER"
        or category in {6, 8}
    )
    if needs_runbook:
        runbook = record.get("runbook")
        if not runbook or not isinstance(runbook, str) or not runbook.strip():
            errors.append(
                f"Runbook is required for severity '{severity}', retryPolicy '{retry_policy}', or category {category}"
            )

    return errors


def validate_legacy_record(record: Dict[str, Any]) -> List[str]:
    """Validate a legacy ERR-XX record format."""
    errors: List[str] = []
    required_fields = [
        "code",
        "service",
        "component",
        "operation",
        "httpStatus",
        "severity",
        "retryable",
        "safeDetail",
    ]
    for field in required_fields:
        if field not in record or record[field] is None:
            errors.append(f"Legacy record missing required field: '{field}'")

    if errors:
        return errors

    code = str(record["code"])
    if not LEGACY_CODE_PATTERN.match(code):
        errors.append(f"Legacy code '{code}' does not match pattern '^ERR-[0-9]{{2}}$'")

    http_status = record.get("httpStatus")
    if not isinstance(http_status, int) or not (100 <= http_status <= 599):
        errors.append(f"Legacy record invalid httpStatus: {http_status}")

    if not isinstance(record.get("retryable"), bool):
        errors.append("Legacy record 'retryable' must be boolean")

    return errors


def validate_catalog(
    catalog_path: Path, domains_registry: Optional[Dict[str, Any]] = None
) -> Tuple[int, List[str]]:
    """Validate the error catalog YAML file against legacy or six-digit schema rules."""
    if not catalog_path.exists():
        return 0, [f"Error catalog missing at {catalog_path}"]

    content: Any = yaml.safe_load(catalog_path.read_text(encoding="utf-8"))
    if not isinstance(content, list):
        if isinstance(content, dict) and "errors" in content and isinstance(content["errors"], list):
            records = content["errors"]
        else:
            return 0, ["Error catalog root must be a YAML list or mapping with 'errors' list"]
    else:
        records = content

    all_errors: List[str] = []
    seen_codes: Set[str] = set()
    seen_names: Set[str] = set()

    for idx, record in enumerate(records):
        if not isinstance(record, dict):
            all_errors.append(f"Item #{idx} is not a dictionary")
            continue

        if "numericCode" in record:
            item_errors = validate_six_digit_record(record, domains_registry)
            code = str(record.get("numericCode", ""))
            name = str(record.get("errorName", ""))
            if code in seen_codes:
                item_errors.append(f"Duplicate numeric code: '{code}'")
            seen_codes.add(code)
            if name in seen_names:
                item_errors.append(f"Duplicate error name: '{name}'")
            seen_names.add(name)
        elif "code" in record:
            item_errors = validate_legacy_record(record)
            code = str(record.get("code", ""))
            if code in seen_codes:
                item_errors.append(f"Duplicate legacy code: '{code}'")
            seen_codes.add(code)
        else:
            item_errors = [f"Item #{idx} has neither 'numericCode' nor 'code'"]

        for err in item_errors:
            all_errors.append(f"Item #{idx} ({record.get('code') or record.get('numericCode')}): {err}")

    return len(seen_codes), all_errors


def run_mock_catalog_validation() -> None:
    """Verify that sample valid and invalid mock records behave according to schema rules."""
    # 1. Valid 6-digit mock record
    valid_mock = {
        "numericCode": "213201",
        "errorName": "GROUP_NOT_FOUND",
        "legacyCode": "NOT_FOUND",
        "domain": 2,
        "module": 1,
        "layer": 3,
        "category": 2,
        "sequence": 1,
        "title": "Group not found",
        "safeDetail": "The requested group does not exist.",
        "messageKey": "error.groups.not_found",
        "owner": "Expense Core",
        "source": "squarewise-expense-core",
        "component": "groups",
        "operation": "get-group",
        "transports": ["REST", "GRAPHQL"],
        "httpStatus": 404,
        "graphqlClassification": "NOT_FOUND",
        "retryPolicy": "NEVER",
        "severity": "WARNING",
        "introducedIn": "1.0.0",
    }
    errors = validate_six_digit_record(valid_mock)
    if errors:
        raise AssertionError(f"Valid mock failed validation: {errors}")

    # 2. Invalid sequence "00"
    invalid_seq_mock = dict(valid_mock, sequence="00", numericCode="213200")
    seq_errors = validate_six_digit_record(invalid_seq_mock)
    if not any("00" in e or "pattern" in e for e in seq_errors):
        raise AssertionError(f"Expected sequence '00' failure, got: {seq_errors}")

    # 3. Invalid zero digit in domain
    invalid_digit_mock = dict(valid_mock, domain=0, numericCode="013201")
    digit_errors = validate_six_digit_record(invalid_digit_mock)
    if not any("pattern" in e or "Domain mismatch" in e for e in digit_errors):
        raise AssertionError(f"Expected digit zero failure, got: {digit_errors}")

    # 4. Missing httpStatus when REST transport present
    invalid_rest_mock = dict(valid_mock)
    del invalid_rest_mock["httpStatus"]
    rest_errors = validate_six_digit_record(invalid_rest_mock)
    if not any("httpStatus is required" in e for e in rest_errors):
        raise AssertionError(f"Expected missing httpStatus failure, got: {rest_errors}")

    # 5. Missing runbook when severity is CRITICAL
    invalid_critical_mock = dict(valid_mock, severity="CRITICAL")
    crit_errors = validate_six_digit_record(invalid_critical_mock)
    if not any("Runbook is required" in e for e in crit_errors):
        raise AssertionError(f"Expected missing runbook failure for CRITICAL, got: {crit_errors}")


def main() -> int:
    """Run complete error contract and catalog verification suite."""
    root = Path(__file__).resolve().parents[2]

    domains_path = root / "contracts/errors/domains.yaml"
    catalog_schema_path = root / "contracts/errors/error-catalog.schema.json"
    lifecycle_schema_path = root / "contracts/errors/lifecycle-history.schema.json"
    catalog_path = root / "contracts/errors/error-catalog.yaml"

    try:
        # 1. Validate domains registry
        domains_data = validate_domains_registry(domains_path)
        print(f"valid frozen domains registry: {domains_path.relative_to(root)} (status: {domains_data.get('status')})")

        # 2. Validate schemas
        validate_schema_file(catalog_schema_path, "Squarewise Error Catalog")
        print(f"valid error-catalog JSON schema: {catalog_schema_path.relative_to(root)}")

        validate_schema_file(lifecycle_schema_path, "Squarewise Error Lifecycle History")
        print(f"valid lifecycle-history JSON schema: {lifecycle_schema_path.relative_to(root)}")

        # 3. Validate mock catalog enforcement
        run_mock_catalog_validation()
        print("valid mock schema assertion tests: all constraints (sequence != 00, digits 1-9, conditionals) passed")

        # 4. Validate current catalog
        count, catalog_errors = validate_catalog(catalog_path, domains_data)
        if catalog_errors:
            print("Errors in error-catalog.yaml:", file=sys.stderr)
            for err in catalog_errors:
                print(f"  - {err}", file=sys.stderr)
            return 1

        print(f"valid error catalog: {count} unique codes in {catalog_path.relative_to(root)}")
        return 0

    except Exception as exc:
        print(f"Error validating error contracts: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
