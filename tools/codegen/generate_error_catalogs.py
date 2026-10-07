"""Generate immutable Kotlin error catalogs from the authoritative YAML catalog."""
from __future__ import annotations

from pathlib import Path
from typing import Any, Final

import yaml  # type: ignore[import-untyped]  # PyYAML ships without inline type stubs

ROOT: Final[Path] = Path(__file__).resolve().parents[2]
SOURCE: Final[Path] = ROOT / "contracts/errors/error-catalog.yaml"
DESTINATION: Final[Path] = ROOT / "libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/catalog"

OWNER_TO_OBJECT: Final[dict[str, str]] = {
    "Accounts": "AccountsErrors",
    "Expense Core": "ExpenseErrors",
    "Notifications": "NotificationErrors",
    "BFF": "BffErrors",
    "Platform": "PlatformErrors",
}
SEVERITY_MAP: Final[dict[str, str]] = {"WARNING": "WARN"}
RETRY_MAP: Final[dict[str, str]] = {
    "REFRESH": "REFRESH_TOKEN",
    "SAME_IDEMPOTENCY_KEY": "IDEMPOTENT_RETRY",
}


def kotlin_string(value: str) -> str:
    """Return a Kotlin string literal for bounded catalog text."""
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def enum_name(value: str, mapping: dict[str, str] | None = None) -> str:
    """Map a YAML enum spelling to its Kotlin enum spelling."""
    return (mapping or {}).get(value, value)


def disclosure(record: dict[str, Any]) -> str:
    """Derive the conservative pre-migration disclosure policy."""
    if record.get("graphqlClassification") == "NOT_FOUND":
        return "RESOURCE_HIDDEN_WHEN_UNAUTHORIZED"
    return "PUBLIC"


def definition(record: dict[str, Any]) -> str:
    """Render one immutable SimpleErrorDefinition constructor."""
    graphql = record.get("graphqlClassification")
    status = record.get("httpStatus")
    lines = [
        "SimpleErrorDefinition(",
        f'    numericCode = ErrorCode({kotlin_string(str(record["numericCode"]))}),',
        f'    errorName = {kotlin_string(str(record["errorName"]))},',
        f'    legacyCode = {kotlin_string(str(record["legacyCode"])) if record.get("legacyCode") else "null"},',
        f'    title = {kotlin_string(str(record["title"]))},',
        f'    safeDetail = {kotlin_string(str(record["safeDetail"]))},',
        f'    messageKey = {kotlin_string(str(record["messageKey"]))},',
        f'    httpStatus = {status if status is not None else "null"},',
        f'    graphqlClassification = {kotlin_string(str(graphql)) if graphql else "null"},',
        f'    retryPolicy = RetryPolicy.{enum_name(str(record["retryPolicy"]), RETRY_MAP)},',
        f'    severity = ErrorSeverity.{enum_name(str(record["severity"]), SEVERITY_MAP)},',
        f'    disclosure = DisclosurePolicy.{disclosure(record)}',
        ")",
    ]
    return "\n".join(lines)


def render_catalog(owner: str, records: list[dict[str, Any]]) -> str:
    """Render one domain catalog with stable source ordering."""
    object_name = OWNER_TO_OBJECT[owner]
    lines = [
        "package com.subhrodip.squarewise.errors.catalog",
        "",
        "import com.subhrodip.squarewise.errors.code.DisclosurePolicy",
        "import com.subhrodip.squarewise.errors.code.ErrorCode",
        "import com.subhrodip.squarewise.errors.code.ErrorDefinition",
        "import com.subhrodip.squarewise.errors.code.ErrorSeverity",
        "import com.subhrodip.squarewise.errors.code.RetryPolicy",
        "",
        "/** Governed, statically compiled error definitions for the " + owner + " domain. */",
        f"object {object_name} {{",
    ]
    constant_names: list[str] = []
    for record in records:
        name = str(record["errorName"])
        constant_names.append(name)
        lines.extend(
            [
                "    /** " + str(record["numericCode"]) + ": " + str(record["title"]) + " */",
                f"    val {name}: ErrorDefinition = {definition(record)}",
                "",
            ]
        )
    lines.extend(["    /** All definitions in authoritative catalog order. */", "    val all: List<ErrorDefinition> = listOf("])
    lines.extend(f"        {name}," for name in constant_names)
    lines.extend(["    )"])
    lines.extend(["}", ""])
    return "\n".join(lines)


def render_parity_test(records: list[dict[str, Any]]) -> str:
    """Render a deterministic compiled-catalog parity test from YAML records."""
    lines = [
        "package com.subhrodip.squarewise.errors.catalog",
        "",
        "import org.junit.jupiter.api.Assertions.assertEquals",
        "import org.junit.jupiter.api.Test",
        "",
        "/** Verifies that generated definitions preserve every authoritative identity field. */",
        "class CatalogParityTest {",
        "    @Test",
        "    fun `compiled catalogs match authoritative identities`() {",
        "        val expected = listOf(",
    ]
    for record in records:
        status = record.get("httpStatus")
        lines.append(
            "            Expected(" + ", ".join(
                [
                    kotlin_string(str(record["numericCode"])),
                    kotlin_string(str(record["errorName"])),
                    kotlin_string(str(record["title"])),
                    str(status) if status is not None else "null",
                ]
            ) + "),"
        )
    lines.extend(
        [
            "        )",
            "        assertEquals(expected.size, ErrorCatalog.all.size)",
            "        expected.zip(ErrorCatalog.all).forEach { (record, actual) ->",
            "            assertEquals(record.numericCode, actual.numericCode.value)",
            "            assertEquals(record.errorName, actual.errorName)",
            "            assertEquals(record.title, actual.title)",
            "            assertEquals(record.httpStatus, actual.httpStatus)",
            "        }",
            "    }",
            "",
            "    private data class Expected(val numericCode: String, val errorName: String, val title: String, val httpStatus: Int?)",
            "}",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    """Generate one deterministic Kotlin object for each catalog owner."""
    records = yaml.safe_load(SOURCE.read_text(encoding="utf-8"))
    if not isinstance(records, list):
        raise ValueError("error catalog must be a YAML sequence")
    grouped: dict[str, list[dict[str, Any]]] = {owner: [] for owner in OWNER_TO_OBJECT}
    for record in records:
        if not isinstance(record, dict) or record.get("owner") not in grouped:
            raise ValueError(f"unsupported catalog owner: {record!r}")
        grouped[str(record["owner"])].append(record)
    DESTINATION.mkdir(parents=True, exist_ok=True)
    for owner, owner_records in grouped.items():
        path = DESTINATION / f"{OWNER_TO_OBJECT[owner]}.kt"
        path.write_text(render_catalog(owner, owner_records), encoding="utf-8", newline="\n")
        print(f"wrote {len(owner_records)} records to {path.relative_to(ROOT)}")
    parity_path = ROOT / "libs/errors/src/test/kotlin/com/subhrodip/squarewise/errors/catalog/CatalogParityTest.kt"
    parity_path.parent.mkdir(parents=True, exist_ok=True)
    parity_path.write_text(render_parity_test(records), encoding="utf-8", newline="\n")
    print(f"wrote parity test for {len(records)} records to {parity_path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
