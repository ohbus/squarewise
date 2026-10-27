"""Tests for aggregate JaCoCo-to-Cobertura conversion."""

from __future__ import annotations

import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from tools.coverage.jacoco_to_cobertura import build_report, convert


class JacocoToCoberturaTest(unittest.TestCase):
    """Verify deterministic line coverage conversion and failure behavior."""

    def test_aggregates_modules_and_resolves_repository_paths(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self._write_report(root, "app/accounts", "com/example", "Accounts.kt", ((10, 1), (11, 0)))
            self._write_report(root, "libs/ids", "com/example", "Ids.java", ((4, 2),))
            output = root / "build/coverage/cobertura.xml"

            convert(root, output)

            document = ET.parse(output)
            coverage = document.getroot()
            self.assertEqual(coverage.get("lines-covered"), "2")
            self.assertEqual(coverage.get("lines-valid"), "3")
            classes = document.findall("./packages/package/classes/class")
            self.assertEqual(
                [clazz.get("filename") for clazz in classes],
                [
                    "app/accounts/src/main/kotlin/com/example/Accounts.kt",
                    "libs/ids/src/main/kotlin/com/example/Ids.java",
                ],
            )

    def test_missing_reports_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, "No JaCoCo reports"):
                build_report(Path(temporary))

    def _write_report(
        self,
        root: Path,
        module: str,
        package: str,
        source_name: str,
        lines: tuple[tuple[int, int], ...],
    ) -> None:
        source_root = root / module / "src/main/kotlin" / Path(*package.split("/"))
        source_root.mkdir(parents=True)
        (source_root / source_name).write_text("source\n", encoding="utf-8")
        report_path = root / module / "build/reports/jacoco/test/jacocoTestReport.xml"
        report_path.parent.mkdir(parents=True)
        line_xml = "".join(f'<line nr="{number}" ci="{hits}"/>' for number, hits in lines)
        report_path.write_text(
            f'<report name="test"><package name="{package}"><sourcefile name="{source_name}">{line_xml}</sourcefile></package></report>',
            encoding="utf-8",
        )


if __name__ == "__main__":
    unittest.main()
