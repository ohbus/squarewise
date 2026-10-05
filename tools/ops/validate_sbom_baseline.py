"""Validate the repository's centralized dependency-version and container SBOM baseline."""

from __future__ import annotations

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
VERSION_PATTERN = re.compile(r"\b\d+\.\d+(?:\.\d+)?(?:[-+][\w.]+)?\b")

REQUIRED_VERSION_ENV_KEYS: tuple[str, ...] = (
    "POSTGRES_IMAGE",
    "RABBITMQ_IMAGE",
    "REDIS_IMAGE",
    "MAILPIT_IMAGE",
    "KEYCLOAK_IMAGE",
    "JVM_BUILD_IMAGE",
    "JVM_RUNTIME_IMAGE",
    "DEVCONTAINER_BASE_IMAGE",
    "DEVCONTAINER_JDK_VERSION",
    "DEVCONTAINER_NODE_VERSION",
    "PYTHON_VERSION",
    "UV_VERSION",
    "MERMAID_CLI_VERSION",
)


def parse_env_file(path: Path) -> dict[str, str]:
    """Parse key-value definitions from an environment example file."""
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        trimmed = line.strip()
        if not trimmed or trimmed.startswith("#") or "=" not in trimmed:
            continue
        key, _, val = trimmed.partition("=")
        values[key.strip()] = val.strip()
    return values


def validate_gradle_catalog() -> list[str]:
    """Ensure JVM dependency versions are strictly centralized in gradle/libs.versions.toml."""
    catalog = ROOT / "gradle" / "libs.versions.toml"
    if not catalog.is_file():
        return ["missing centralized Gradle version catalog: gradle/libs.versions.toml"]

    findings: list[str] = []
    for path in sorted((ROOT / "app").rglob("build.gradle.kts")) + sorted(
        (ROOT / "libs").rglob("build.gradle.kts")
    ):
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if "implementation(" in line and VERSION_PATTERN.search(line):
                findings.append(f"{path.relative_to(ROOT)}:{number}")
    return findings


def validate_container_and_toolchain_baseline() -> list[str]:
    """Ensure container and toolchain versions are centralized in infra/versions.env.example."""
    versions_file = ROOT / "infra" / "versions.env.example"
    if not versions_file.is_file():
        return ["missing centralized container version baseline: infra/versions.env.example"]

    version_vars = parse_env_file(versions_file)
    findings: list[str] = []

    for key in REQUIRED_VERSION_ENV_KEYS:
        if key not in version_vars or not version_vars[key]:
            findings.append(f"infra/versions.env.example missing required version key: {key}")

    # Validate alignment with infra/local/.env.example
    local_env_file = ROOT / "infra" / "local" / ".env.example"
    if local_env_file.is_file():
        local_vars = parse_env_file(local_env_file)
        for key in REQUIRED_VERSION_ENV_KEYS:
            if key in local_vars and key in version_vars:
                if local_vars[key] != version_vars[key]:
                    findings.append(
                        f"version drift between infra/versions.env.example ({version_vars[key]}) "
                        f"and infra/local/.env.example ({local_vars[key]}) for key: {key}"
                    )

    # Validate pyproject.toml python version
    pyproject_file = ROOT / "pyproject.toml"
    if pyproject_file.is_file() and "PYTHON_VERSION" in version_vars:
        content = pyproject_file.read_text(encoding="utf-8")
        expected_python = version_vars["PYTHON_VERSION"]
        if f'requires-python = ">={expected_python}"' not in content:
            findings.append(
                f"pyproject.toml requires-python does not align with PYTHON_VERSION={expected_python}"
            )

    return findings


def validate_docker_drift_prevention() -> list[str]:
    """Ensure Dockerfiles use parameterized ARGs for base images rather than hardcoded tags."""
    findings: list[str] = []
    dockerfiles: list[Path] = [
        ROOT / "infra" / "docker" / "Dockerfile.jvm",
        ROOT / "infra" / "docker" / "Dockerfile.fast",
        ROOT / "infra" / "docker" / "Dockerfile.dev",
        ROOT / "infra" / "docs" / "Dockerfile",
        ROOT / "tests" / "fixtures" / "invalid_subject_oidc" / "Dockerfile",
        ROOT / ".devcontainer" / "Dockerfile",
    ]

    from_pattern = re.compile(r"^FROM\s+([^\s]+)", re.MULTILINE)

    for dockerfile in dockerfiles:
        if not dockerfile.is_file():
            continue
        content = dockerfile.read_text(encoding="utf-8")
        for match in from_pattern.finditer(content):
            base_image = match.group(1)
            # Base image should be a variable reference (e.g. ${JVM_BUILD_IMAGE}) or scratch/local stage name
            if ":" in base_image and not base_image.startswith("${") and not base_image.startswith("$"):
                findings.append(
                    f"{dockerfile.relative_to(ROOT)} has unparameterized hardcoded FROM base image: {base_image}. "
                    "Use ARG parameterization to prevent version drift."
                )

    return findings


def main() -> int:
    """Validate all repository dependency, container, and toolchain version baselines."""
    findings: list[str] = []
    findings.extend(validate_gradle_catalog())
    findings.extend(validate_container_and_toolchain_baseline())
    findings.extend(validate_docker_drift_prevention())

    if findings:
        print("Dependency and container SBOM baseline validation failed:")
        for finding in findings:
            print(f"  - {finding}")
        return 1

    print("SBOM dependency baseline validation passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
