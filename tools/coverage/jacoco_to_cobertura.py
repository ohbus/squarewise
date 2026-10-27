"""Convert the repository's JaCoCo XML reports to Cobertura XML."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from tools.coverage.report_branch_gaps import module_name, report_paths


@dataclass(frozen=True)
class SourceCoverage:
    """Line coverage for one repository source file."""

    package: str
    source_path: str
    lines: tuple[tuple[int, int], ...]


def _rate(covered: int, total: int) -> str:
    """Format a Cobertura line rate without division-by-zero output."""

    return f"{covered / total:.6f}" if total else "0.000000"


def _source_path(root: Path, report: Path, package: str, source_name: str) -> Path:
    """Resolve a JaCoCo source file against its owning JVM module."""

    module_root = root / module_name(root, report)
    package_path = Path(*package.split("/")) if package else Path()
    candidates = tuple(
        module_root / source_root / package_path / source_name
        for source_root in ("src/main/kotlin", "src/main/java")
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    searched = ", ".join(str(candidate) for candidate in candidates)
    raise ValueError(f"JaCoCo source file {source_name!r} was not found; searched {searched}")


def _parse_report(root: Path, report: Path) -> list[SourceCoverage]:
    """Read source-file line hits from one JaCoCo report."""

    document = ET.parse(report)
    coverage: list[SourceCoverage] = []
    for package in document.findall("./package"):
        package_name = package.get("name", "")
        for source_file in package.findall("./sourcefile"):
            source_name = source_file.get("name")
            if not source_name:
                raise ValueError(f"JaCoCo sourcefile without a name in {report}")
            source_path = _source_path(root, report, package_name, source_name)
            parsed_lines: list[tuple[int, int]] = []
            for line in source_file.findall("./line"):
                number = line.get("nr")
                covered = line.get("ci")
                if number is None or covered is None:
                    raise ValueError(f"JaCoCo line without nr/ci in {report}")
                parsed_lines.append((int(number), int(covered)))
            lines = tuple(parsed_lines)
            if lines:
                coverage.append(
                    SourceCoverage(
                        package=package_name,
                        source_path=source_path.relative_to(root).as_posix(),
                        lines=lines,
                    )
                )
    return coverage


def _class_element(parent: ET.Element, source: SourceCoverage) -> None:
    """Append one Cobertura class for a source file."""

    covered = sum(hits > 0 for _, hits in source.lines)
    total = len(source.lines)
    package = ET.SubElement(
        parent,
        "package",
        {"name": source.package, "line-rate": _rate(covered, total), "branch-rate": "0.000000"},
    )
    classes = ET.SubElement(package, "classes")
    clazz = ET.SubElement(
        classes,
        "class",
        {
            "name": Path(source.source_path).stem,
            "filename": source.source_path,
            "line-rate": _rate(covered, total),
            "branch-rate": "0.000000",
        },
    )
    ET.SubElement(clazz, "methods")
    lines = ET.SubElement(clazz, "lines")
    for number, hits in source.lines:
        ET.SubElement(lines, "line", {"number": str(number), "hits": str(hits)})


def build_report(root: Path) -> ET.ElementTree:
    """Build a deterministic aggregate Cobertura document from JaCoCo XML."""

    reports = report_paths(root)
    if not reports:
        raise ValueError("No JaCoCo reports were found under app/ or libs/")
    sources = [source for report in reports for source in _parse_report(root, report)]
    if not sources:
        raise ValueError("JaCoCo reports contained no source-file line coverage")
    sources.sort(key=lambda source: (source.package, source.source_path))
    if len({source.source_path for source in sources}) != len(sources):
        raise ValueError("JaCoCo reports contain duplicate source-file paths")

    total = sum(len(source.lines) for source in sources)
    covered = sum(sum(hits > 0 for _, hits in source.lines) for source in sources)
    coverage = ET.Element(
        "coverage",
        {
            "line-rate": _rate(covered, total),
            "branch-rate": "0.000000",
            "lines-covered": str(covered),
            "lines-valid": str(total),
            "version": "1.0",
        },
    )
    source_root = ET.SubElement(coverage, "sources")
    ET.SubElement(source_root, "source").text = "."
    packages = ET.SubElement(coverage, "packages")
    for source in sources:
        _class_element(packages, source)
    return ET.ElementTree(coverage)


def convert(root: Path, output: Path) -> None:
    """Write the aggregate Cobertura report to ``output``."""

    output.parent.mkdir(parents=True, exist_ok=True)
    document = build_report(root)
    ET.indent(document, space="  ")
    document.write(output, encoding="utf-8", xml_declaration=True)


def main(argv: Sequence[str] | None = None) -> int:
    """Parse command-line arguments and convert JaCoCo reports."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args(argv)
    try:
        convert(arguments.root.resolve(), arguments.output)
    except (ET.ParseError, OSError, ValueError) as error:
        print(f"coverage conversion failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
