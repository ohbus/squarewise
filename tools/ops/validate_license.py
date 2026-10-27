"""Validate the repository's first-party AGPL-3.0-only license metadata."""

from __future__ import annotations

from pathlib import Path
import sys
import tomllib
from typing import Any, Mapping


ROOT = Path(__file__).resolve().parents[2]
LICENSE_PATH = ROOT / "LICENSE"
PYPROJECT_PATH = ROOT / "pyproject.toml"
README_PATH = ROOT / "README.md"

REFERENCE_PATHS: tuple[Path, ...] = (
    README_PATH,
    ROOT / "CONTRIBUTING.md",
    ROOT / "docs" / "README.md",
    ROOT / "docs" / "operations" / "licensing.md",
    ROOT / "docs" / "operations" / "ci.md",
    ROOT / "docs" / "security" / "supply-chain-evidence.md",
    ROOT / "docs" / "implementation" / "production-readiness-roadmap.md",
    ROOT / "docs" / "operations" / "production-readiness-plan.md",
    ROOT / "docs" / "reviews" / "production-readiness-audit.md",
)


def validate_license_text(text: str) -> list[str]:
    """Return findings for the canonical AGPL-3.0-only license text."""
    findings: list[str] = []
    if not text.startswith("GNU AFFERO GENERAL PUBLIC LICENSE\nVersion 3"):
        findings.append("LICENSE must start with the canonical GNU AGPL v3.0 heading")
    if "MIT License" in text or "Permission is hereby granted" in text:
        findings.append("LICENSE still contains MIT license text")
    if "Remote Network Interaction" not in text:
        findings.append("LICENSE is missing AGPL section 13")
    return findings


def validate_project_metadata(document: Mapping[str, Any]) -> list[str]:
    """Return findings for the Python project's SPDX license metadata."""
    project = document.get("project")
    if not isinstance(project, dict):
        return ["pyproject.toml is missing the [project] table"]

    findings: list[str] = []
    if project.get("license") != "AGPL-3.0-only":
        findings.append("pyproject.toml project.license must be AGPL-3.0-only")

    license_files = project.get("license-files")
    if not isinstance(license_files, list) or "LICENSE" not in license_files:
        findings.append("pyproject.toml project.license-files must include LICENSE")
    return findings


def validate_references(references: Mapping[str, str]) -> list[str]:
    """Return findings for active repository references to the project license."""
    findings: list[str] = []
    for name, text in references.items():
        if "MIT License" in text or "No GPL in MIT project" in text:
            findings.append(f"{name} contains a stale MIT project-license reference")
    return findings


def validate_repository() -> list[str]:
    """Validate checked-in license, metadata, and active documentation references."""
    findings = validate_license_text(LICENSE_PATH.read_text(encoding="utf-8"))
    try:
        metadata = tomllib.loads(PYPROJECT_PATH.read_text(encoding="utf-8"))
    except (FileNotFoundError, tomllib.TOMLDecodeError) as error:
        findings.append(f"cannot parse pyproject.toml: {error}")
    else:
        findings.extend(validate_project_metadata(metadata))

    references: dict[str, str] = {}
    for path in REFERENCE_PATHS:
        if not path.is_file():
            findings.append(f"missing license reference file: {path.relative_to(ROOT)}")
        else:
            references[str(path.relative_to(ROOT))] = path.read_text(encoding="utf-8")
    findings.extend(validate_references(references))
    if "AGPL-3.0-only" not in references.get("README.md", ""):
        findings.append("README.md must identify AGPL-3.0-only")
    return findings


def main() -> int:
    """Run license validation and return a process status."""
    findings = validate_repository()
    if findings:
        print("License validation failed:")
        for finding in findings:
            print(f"  - {finding}")
        return 1
    print("AGPL-3.0-only license validation passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
