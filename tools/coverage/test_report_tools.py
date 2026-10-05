"""Regression tests for QA-10 coverage inventory tooling."""

from __future__ import annotations

from collections import Counter
from contextlib import redirect_stdout
from io import StringIO
import json
from pathlib import Path
import re
import tempfile
import unittest

from tools.coverage.report_branch_gaps import (
    all_gaps,
    evidence_target,
    line_gaps,
    line_markdown,
    main as branch_report_main,
    markdown as render_branch_gaps,
)
from tools.coverage.report_execution_gaps import (
    all_gaps as execution_gaps,
    markdown as render_execution_gaps,
)
from tools.coverage.report_operation_test_gaps import (
    acceptance_criteria,
    callable_references,
    inventory,
    load_execution_artifact,
    main as operation_report_main,
    references,
    render_markdown as render_operation_markdown,
)
from tools.coverage.normalize_bruno_execution import normalize as normalize_bruno
from tests.e2e.test_concurrency_subscriptions import write_execution_evidence
from tests.e2e.qa10_evidence import load_execution_specs


ROOT = Path(__file__).resolve().parents[2]


class CoverageInventoryTest(unittest.TestCase):
    """Protect the exhaustive branch and operation discovery invariants."""

    def test_subscription_evidence_writer_emits_strict_operation_record(self) -> None:
        """Ensure the live subscription reporter cannot omit required attribution."""

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "nested" / "subscription.json"
            write_execution_evidence(
                path,
                "abc123",
                "ci-compose-oidc",
                [{
                    "surface": "GraphQL Subscription",
                    "operation": "groupChanged",
                    "status": "passed",
                    "artifact": str(path),
                    "assertions": ["subscription delivery was verified"],
                }],
            )

            document = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual("qa10-operation-execution-v1", document["schema"])
            self.assertEqual("abc123", document["source_revision"])
            self.assertEqual(
                {("GraphQL Subscription", "groupChanged"): "EXECUTION-ARTIFACT-PASSED"},
                load_execution_artifact(path),
            )

    def test_product_evidence_specs_name_contract_operations(self) -> None:
        """Prevent product evidence metadata from silently drifting from contracts."""

        specifications = load_execution_specs(ROOT / "tests/e2e/product-operation-specs.json")
        contract_operations = {(item.surface, item.operation) for item in inventory(ROOT)}
        self.assertTrue(specifications)
        self.assertTrue(
            all((spec["surface"], spec["operation"]) in contract_operations for spec in specifications)
        )

    def test_every_current_branch_gap_has_a_qa_row(self) -> None:
        gaps = all_gaps(ROOT)

        self.assertEqual(35, len(gaps))
        self.assertTrue(all(gap.qa_row.startswith("QA10-") for gap in gaps))
        self.assertTrue(all(gap.assignment_basis for gap in gaps))
        self.assertTrue(all(gap.acceptance_criteria for gap in gaps))
        self.assertTrue(all(gap.record_acceptance for gap in gaps))
        self.assertTrue(all(gap.closure_status for gap in gaps))
        line_inventory = line_gaps(ROOT)
        ownership = {(gap.source_file, gap.source_line): gap.method for gap in line_inventory}
        self.assertEqual("<init>", ownership[("RecurrenceSchedule.kt", 11)])
        self.assertEqual("<init>", ownership[("DbOperationPolicy.kt", 28)])
        self.assertTrue(all(gap.next_action for gap in gaps))
        self.assertTrue(all(gap.evidence_target for gap in gaps))
        for gap in gaps:
            target = gap.evidence_target.split(" (")[0]
            if ".../" in target:
                module_root, suffix = target.split("/src/test/.../", maxsplit=1)
                matches = list((ROOT / module_root / "src/test").rglob(suffix))
                self.assertTrue(matches, f"missing evidence target for {gap.class_name}")
            else:
                self.assertIn(": identify a focused", target)
        self.assertEqual(
            31,
            sum(gap.closure_status == "BEHAVIOR-COVERED-MAPPING" for gap in gaps),
        )
        self.assertEqual(
            4,
            sum(gap.closure_status == "STRUCTURAL-INVARIANT" for gap in gaps),
        )
        self.assertEqual(
            0,
            sum(gap.closure_status == "OPEN-DESIGN" for gap in gaps),
        )
        group_store_gaps = [gap for gap in gaps if "JpaGroupStore" in gap.class_name]
        self.assertEqual(9, len(group_store_gaps))
        self.assertTrue(
            all(gap.closure_status == "BEHAVIOR-COVERED-MAPPING" for gap in group_store_gaps)
        )
        self.assertTrue(
            all("JpaGroupStoreClaimTest" in gap.record_acceptance for gap in group_store_gaps)
        )
        recurring_gaps = [gap for gap in gaps if "RecurringExpenseService" in gap.class_name]
        self.assertEqual(5, len(recurring_gaps))
        self.assertTrue(
            all(gap.closure_status == "BEHAVIOR-COVERED-MAPPING" for gap in recurring_gaps)
        )
        self.assertTrue(
            all("RecurringExpenseOptionalOutboxTest" in gap.record_acceptance for gap in recurring_gaps)
        )
        error_handler_gaps = [gap for gap in gaps if "GlobalErrorHandler" in gap.class_name]
        self.assertEqual(0, len(error_handler_gaps))
        db_telemetry_gaps = [gap for gap in gaps if "DbTelemetry" in gap.class_name]
        self.assertEqual(0, len(db_telemetry_gaps))
        for class_name, test_name in {
            "ClientAddressResolver": "ClientAddressResolverTest",
            "EmailAddress": "EmailAddressTest",
            "SessionPolicy": "SessionPolicyTest",
            "BrowserOriginPolicy": "BrowserOriginPolicyTest",
            "RecurrenceSchedule": "RecurrencePolicyTest",
            "SearchController": "SearchControllerTest",
            "ExpenseSearch": "ExpenseSearchTest",
            "SyncController": "SyncControllerTest",
            "EmailDispatcher": "EmailDispatcherTest",
            "FallbackJwtDecoder": "FallbackJwtDecoderTest",
            "SettlementSuggestionEngine": "SettlementSuggestionTest",
            "DbReaderHealth": "DbReaderHealthTest",
            "DbOperationPolicy": "DbOperationPolicyTest",
        }.items():
            candidate_gaps = [gap for gap in gaps if class_name in gap.class_name]
            self.assertTrue(candidate_gaps, f"missing inventory record for {class_name}")
            self.assertTrue(
                all(gap.closure_status == "BEHAVIOR-COVERED-MAPPING" for gap in candidate_gaps)
            )
            self.assertTrue(all(test_name in gap.record_acceptance for gap in candidate_gaps))
        expected_counts = {
            "QA10-A01": 0,
            "QA10-A02": 0,
            "QA10-A03": 1,
            "QA10-A04": 0,
            "QA10-A05": 1,
            "QA10-A06": 0,
            "QA10-A07": 2,
            "QA10-A08": 1,
            "QA10-B01": 1,
            "QA10-B02": 0,
            "QA10-B03": 3,
            "QA10-B04": 1,
            "QA10-C01": 5,
            "QA10-C02": 0,
            "QA10-C03": 6,
            "QA10-C04": 9,
            "QA10-C05": 1,
            "QA10-C06": 1,
            "QA10-D01": 0,
            "QA10-D02": 0,
            "QA10-D03": 1,
            "QA10-D04": 0,
            "QA10-E01": 0,
            "QA10-E02": 2,
            "QA10-E03": 0,
            "QA10-E04": 0,
            "QA10-E05": 0,
        }
        actual_counts = Counter(gap.qa_row for gap in gaps)
        self.assertEqual(
            expected_counts,
            {row: actual_counts.get(row, 0) for row in expected_counts},
        )
        self.assertEqual(61, sum(gap.missed_branches for gap in gaps))

    def test_evidence_targets_follow_public_or_service_boundaries(self) -> None:
        """Keep generated targets actionable without treating them as coverage."""

        self.assertIn("ProfileControllerTest.kt", evidence_target("app/accounts", "ProfileController"))
        self.assertIn("RecurrencePolicyTest.kt (constructor boundary)", evidence_target("app/expense-core", "RecurrenceSchedule"))
        self.assertIn("DbTelemetryTest.kt", evidence_target("libs/observability", "DbTelemetry"))

    def test_operation_inventory_is_complete_and_assigned(self) -> None:
        operations = inventory(ROOT)

        self.assertEqual(54, len(operations))
        self.assertEqual(
            {"REST": 45, "GraphQL Query": 4, "GraphQL Mutation": 4, "GraphQL Subscription": 1},
            Counter(item.surface for item in operations),
        )
        self.assertTrue(all(item.acceptance_row == "QA10-E2E01" for item in operations))
        self.assertTrue(all(acceptance_criteria(item) for item in operations))
        self.assertTrue(
            all(item.e2e_status == "SOURCE-REFERENCE-ONLY" for item in operations)
        )
        self.assertTrue(
            all(
                item.e2e_callable_status == "CALLABLE-SOURCE-REFERENCE-ONLY"
                for item in operations
            )
        )
        self.assertTrue(
            all(item.e2e_request_status == "REQUEST-SOURCE-REFERENCE-ONLY" for item in operations)
        )
        self.assertTrue(
            all(item.execution_status == "NO-EXECUTION-ARTIFACT-INGESTED" for item in operations)
        )
        self.assertEqual(
            18,
            sum(item.bruno_status == "BRUNO-SOURCE-REFERENCE-ONLY" for item in operations),
        )
        self.assertEqual(
            36,
            sum(item.bruno_status == "NO-BRUNO-SOURCE-REFERENCE" for item in operations),
        )
        self.assertEqual(
            0,
            sum(not item.has_e2e_signal for item in operations),
        )
        self.assertEqual(
            36,
            sum(not item.has_bruno_signal for item in operations),
        )

    def test_bruno_normalizer_records_unique_graphql_execution(self) -> None:
        """Credit only a uniquely mapped, assertion-backed Bruno request."""

        report = [
            {
                "path": "bff/graphql-me",
                "status": "passed",
                "testResults": [{"name": "GraphQL response has no errors"}],
                "assertionResults": [],
            }
        ]
        with tempfile.TemporaryDirectory() as directory:
            report_path = Path(directory) / "bruno.json"
            report_path.write_text(json.dumps(report), encoding="utf-8")
            artifact = normalize_bruno(
                ROOT,
                report_path,
                "source-sha",
                "ci-compose-oidc",
                "build/reports/e2e/bruno.json",
            )

        self.assertEqual("qa10-operation-execution-v1", artifact["schema"])
        self.assertEqual(
            [{
                "surface": "GraphQL Query",
                "operation": "me",
                "status": "passed",
                "artifact": "build/reports/e2e/bruno.json",
                "assertions": ["GraphQL response has no errors"],
            }],
            artifact["operations"],
        )

    def test_bruno_normalizer_rejects_ambiguous_or_mismatched_source(self) -> None:
        """Do not credit a REST request from a misclassified or shared fixture."""

        report = [
            {
                "path": "accounts/unmapped-request",
                "status": "passed",
                "testResults": [{"name": "profile response is successful"}],
            }
        ]
        with tempfile.TemporaryDirectory() as directory:
            report_path = Path(directory) / "bruno.json"
            report_path.write_text(json.dumps(report), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "no unique"):
                normalize_bruno(
                    ROOT,
                    report_path,
                    "source-sha",
                    "ci-compose-oidc",
                    "build/reports/e2e/bruno.json",
                )

    def test_concrete_zero_execution_inventory_is_complete_and_assigned(self) -> None:
        gaps = execution_gaps(ROOT)

        self.assertEqual(1, len(gaps))
        self.assertTrue(all(gap.qa_row.startswith("QA10-") for gap in gaps))
        self.assertTrue(all(gap.acceptance_criteria for gap in gaps))
        self.assertTrue(all(gap.next_action for gap in gaps))
        self.assertEqual(
            1,
            sum(gap.status == "INLINE-EXPANDED" for gap in gaps),
        )
        self.assertTrue(
            all(
                gap.status in {"INLINE-EXPANDED", "NO-INSTRUCTION-EXECUTION"}
                for gap in gaps
            )
        )
        self.assertEqual(
            {
                "libs/observability": 1,
            },
            Counter(gap.module for gap in gaps),
        )
        self.assertNotIn("AccountIdentityStore", {gap.class_name for gap in gaps})
        self.assertNotIn("<init>", {gap.method for gap in gaps})
        self.assertNotIn("$default", " ".join(gap.method for gap in gaps))
        self.assertEqual("measureQuery", gaps[0].method)

    def test_committed_execution_gap_ledger_matches_current_inventory(self) -> None:
        gaps = execution_gaps(ROOT)
        ledger = (ROOT / "docs/quality/qa10-execution-gap-ledger.md").read_text(
            encoding="utf-8"
        )
        marker = "## Exact current records\n\n"
        self.assertIn(marker, ledger)
        self.assertEqual(
            render_execution_gaps(gaps), ledger.split(marker, maxsplit=1)[1].strip()
        )

    def test_operation_json_exposes_e2e_status(self) -> None:
        output = StringIO()

        with redirect_stdout(output):
            result = operation_report_main(
                ["--root", str(ROOT), "--format", "json"]
            )

        self.assertEqual(0, result)
        self.assertIn('"e2e_status": "SOURCE-REFERENCE-ONLY"', output.getvalue())
        self.assertIn(
            '"e2e_callable_status": "CALLABLE-SOURCE-REFERENCE-ONLY"',
            output.getvalue(),
        )
        self.assertIn(
            '"e2e_request_status": "REQUEST-SOURCE-REFERENCE-ONLY"',
            output.getvalue(),
        )
        self.assertIn(
            '"execution_status": "NO-EXECUTION-ARTIFACT-INGESTED"',
            output.getvalue(),
        )

    def test_execution_artifact_marks_only_declared_operation(self) -> None:
        artifact = {
            "schema": "qa10-operation-execution-v1",
            "source_revision": "abc123",
            "environment": "ci-compose",
            "operations": [
                {
                    "surface": "REST",
                    "operation": "getMe",
                    "status": "passed",
                    "artifact": "reports/get-me.json",
                    "assertions": ["profile identity and ownership"],
                }
            ],
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "qa10-execution.json"
            path.write_text(json.dumps(artifact), encoding="utf-8")

            items = inventory(ROOT, path)

        get_me = next(item for item in items if item.operation == "getMe")
        get_group = next(item for item in items if item.operation == "getGroup")
        self.assertEqual("EXECUTION-ARTIFACT-PASSED", get_me.execution_status)
        self.assertEqual("NO-EXECUTION-ARTIFACT-INGESTED", get_group.execution_status)

    def test_execution_artifact_requires_traceable_assertions(self) -> None:
        artifact = {
            "schema": "qa10-operation-execution-v1",
            "source_revision": "abc123",
            "environment": "ci-compose",
            "operations": [
                {
                    "surface": "REST",
                    "operation": "getMe",
                    "status": "passed",
                    "artifact": "reports/get-me.json",
                    "assertions": [],
                }
            ],
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "invalid-qa10-execution.json"
            path.write_text(json.dumps(artifact), encoding="utf-8")

            with self.assertRaises(ValueError):
                load_execution_artifact(path)

    def test_operation_references_do_not_accept_identifier_substrings(self) -> None:
        sources = (
            ("tests/e2e/unrelated.py", "groups = []\ngroupId = 'x'"),
            ("tests/e2e/actual.py", "query = '{ group }'"),
        )

        self.assertEqual(("tests/e2e/actual.py",), references("group", sources))

    def test_callable_operation_references_require_a_named_python_callable(self) -> None:
        sources = (
            ("tests/e2e/module_only.py", "query = '{ group }'"),
            ("tests/e2e/journey.py", "def main():\n    return '{ group }'"),
        )

        self.assertEqual(
            ("tests/e2e/journey.py::main",),
            callable_references("group", sources),
        )

    def test_request_signal_is_surface_aware(self) -> None:
        """Avoid crediting a REST operation from a GraphQL field with the same ID."""

        from tools.coverage.report_operation_test_gaps import OperationEvidence, request_references

        sources = (("tests/e2e/journey.py", 'query = "mutation { createExpense { id } }"\npath = "/expense-core/v1/groups/{group_id}/expenses"'),)
        graphql = OperationEvidence("GraphQL Mutation", "createExpense", "BFF", None, None, (), (), (), ())
        rest = OperationEvidence("REST", "createExpense", "Expense Core API", "POST", "/expense-core/v1/groups/{groupId}/expenses", (), (), (), ())

        self.assertEqual(("tests/e2e/journey.py",), request_references(graphql, sources))
        self.assertEqual(("tests/e2e/journey.py",), request_references(rest, sources))

    def test_committed_operation_acceptance_ledger_matches_current_inventory(self) -> None:
        operations = inventory(ROOT)
        ledger = (ROOT / "docs/quality/qa10-operation-acceptance-ledger.md").read_text(
            encoding="utf-8"
        )
        marker = "## Per-operation records\n\n"
        self.assertIn(marker, ledger)
        self.assertEqual(
            render_operation_markdown(operations),
            ledger.split(marker, maxsplit=1)[1].strip(),
        )

    def test_branch_closure_gate_rejects_current_gaps(self) -> None:
        output = StringIO()

        with redirect_stdout(output):
            result = branch_report_main(
                ["--root", str(ROOT), "--format", "json", "--fail-on-gaps"]
            )

        self.assertEqual(1, result)
        self.assertIn('"qa_row"', output.getvalue())

    def test_branch_markdown_is_an_exact_per_record_ledger(self) -> None:
        gaps = all_gaps(ROOT)
        rendered = render_branch_gaps(gaps)
        rows = rendered.splitlines()

        self.assertEqual(37, len(rows))
        self.assertEqual(
            "| Module | QA row | Production class | Source | Method | Line | Missed | Covered | Assignment | Report | QA-row acceptance | Record acceptance | Evidence target | Closure status | Next action |",
            rows[0],
        )
        self.assertEqual(
            "| --- | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- | --- | --- |",
            rows[1],
        )
        self.assertEqual(
            sum(gap.missed_branches for gap in gaps),
            sum(int(row.split("|")[7].strip()) for row in rows[2:]),
        )

    def test_source_line_inventory_accounts_for_every_missed_branch(self) -> None:
        gaps = line_gaps(ROOT)

        self.assertEqual(48, len(gaps))
        self.assertEqual(61, sum(gap.missed_branches for gap in gaps))
        self.assertTrue(all(gap.package for gap in gaps))
        self.assertTrue(all(gap.source_file for gap in gaps))
        self.assertTrue(all(gap.class_name for gap in gaps))
        self.assertTrue(all(gap.method for gap in gaps))
        self.assertTrue(all(gap.qa_row.startswith("QA10-") for gap in gaps))
        self.assertTrue(all(gap.acceptance_criteria for gap in gaps))
        self.assertTrue(all(gap.closure_status for gap in gaps))
        self.assertTrue(all(gap.source_line > 0 for gap in gaps))

        for gap in gaps:
            package_path = Path(gap.package)
            candidates = [
                ROOT / gap.module / "src" / source_root / package_path / gap.source_file
                for source_root in ("main/kotlin", "main/java")
            ]
            source = next((candidate for candidate in candidates if candidate.exists()), None)
            self.assertIsNotNone(source, f"Missing production source for {gap}")
            assert source is not None
            line_count = len(source.read_text(encoding="utf-8").splitlines())
            self.assertLessEqual(gap.source_line, line_count)

    def test_committed_source_line_ledger_matches_current_inventory(self) -> None:
        ledger = (
            ROOT / "docs/quality/qa10-branch-line-gap-ledger.md"
        ).read_text(encoding="utf-8")
        marker = "| Module | Package | Source | Class | Method | Line | Missed | Covered | QA row | QA-row acceptance | Record acceptance | Evidence target | Closure status | Next action | Report |\n"
        self.assertIn(marker, ledger)
        self.assertEqual(
            line_markdown(line_gaps(ROOT)),
            ledger[ledger.index(marker) :].strip(),
        )

    def test_committed_branch_ledger_matches_current_inventory(self) -> None:
        gaps = all_gaps(ROOT)
        ledger = (ROOT / "docs/quality/qa10-current-branch-gap-ledger.md").read_text(
            encoding="utf-8"
        )
        marker = "## Exact current records\n\n"
        self.assertIn(marker, ledger)
        self.assertEqual(render_branch_gaps(gaps), ledger.split(marker, maxsplit=1)[1].strip())

    def test_audit_documents_every_acceptance_row(self) -> None:
        audit = (ROOT / "docs/quality/test-coverage-gap-audit.md").read_text(
            encoding="utf-8"
        )
        expected_rows = [
            *(f"QA10-A0{number}" for number in range(1, 9)),
            *(f"QA10-B0{number}" for number in range(1, 5)),
            *(f"QA10-C0{number}" for number in range(1, 7)),
            *(f"QA10-D0{number}" for number in range(1, 5)),
            *(f"QA10-E0{number}" for number in range(1, 6)),
            *(f"QA10-E2E0{number}" for number in range(1, 8)),
        ]

        for row in expected_rows:
            with self.subTest(row=row):
                self.assertIn(row, audit)

    def test_audit_source_file_counts_match_repository(self) -> None:
        """Keep the documented source/test inventory synchronized with the tree."""

        kotlin_files = tuple(
            path
            for directory in (ROOT / "app", ROOT / "libs")
            for path in directory.rglob("*.kt")
        )
        test_files = tuple(
            path for path in kotlin_files if "src" in path.parts and "test" in path.parts
        )
        production_count = len(kotlin_files) - len(test_files)
        test_count = len(test_files)
        audit = (ROOT / "docs/quality/test-coverage-gap-audit.md").read_text(
            encoding="utf-8"
        )

        self.assertIn(
            f"{production_count} Kotlin production files and {test_count}",
            audit,
        )

    def test_every_openapi_rest_path_has_a_central_endpoint_literal(self) -> None:
        """Prevent contract paths from bypassing the shared endpoint vocabulary."""

        endpoint_source = (
            ROOT
            / "libs/ids/src/main/kotlin/com/subhrodip/squarewise/ids/contracts/ApiEndpoints.kt"
        ).read_text(encoding="utf-8")
        composed_paths = {
            "/allocations/preview": "PATH_ALLOCATION_PREVIEW",
            "/groups/{groupId}/expenses": "PATH_GROUP_EXPENSES",
            "/groups/{groupId}/expenses/{expenseId}": "PATH_GROUP_EXPENSE_BY_ID",
            "/groups/{groupId}/balances": "PATH_GROUP_BALANCES",
            "/groups/{groupId}/settlements": "PATH_GROUP_SETTLEMENTS",
            "/groups/{groupId}/settlements/{settlementId}/reversal": "PATH_GROUP_SETTLEMENT_REVERSAL",
            "/groups/{groupId}/settlements/suggestions": "PATH_GROUP_SETTLEMENT_SUGGESTIONS",
            "/groups/{groupId}/sync/changes": "PATH_GROUP_SYNC_CHANGES",
            "/groups/{groupId}/sync/snapshot": "PATH_GROUP_SYNC_SNAPSHOT",
            "/groups/{groupId}/search": "PATH_GROUP_SEARCH",
            "/groups/{groupId}/export": "PATH_GROUP_EXPORT",
            "/groups/{groupId}/schedules": "PATH_GROUP_SCHEDULES",
            "/groups/{groupId}/schedules/{scheduleId}": "PATH_GROUP_SCHEDULE_BY_ID",
            "/groups/{groupId}/schedules/{scheduleId}/pause": "PATH_GROUP_SCHEDULE_PAUSE",
            "/groups/{groupId}/schedules/{scheduleId}/resume": "PATH_GROUP_SCHEDULE_RESUME",
            "/groups/{groupId}/invites/{token}/revoke": "PATH_GROUP_INVITE_REVOKE",
            "/inbox/{notificationId}/read": "PATH_INBOX_MARK_READ",
        }
        for contract_path in sorted((ROOT / "contracts/rest").glob("*.openapi.json")):
            document = json.loads(contract_path.read_text(encoding="utf-8"))
            for path in document["paths"]:
                with self.subTest(contract=contract_path.name, path=path):
                    literal_present = f'"{path}"' in endpoint_source
                    constant_name = composed_paths.get(path)
                    constant_present = constant_name is not None and (
                        f"const val {constant_name}" in endpoint_source
                    )
                    self.assertTrue(literal_present or constant_present)

    def test_error_catalog_codes_match_the_kotlin_error_enum(self) -> None:
        """Prevent public error codes from drifting between YAML and Kotlin."""

        catalog = (ROOT / "contracts/errors/error-catalog.yaml").read_text(encoding="utf-8")
        enum_source = (
            ROOT
            / "libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/domain/ErrorCode.kt"
        ).read_text(encoding="utf-8")
        catalog_codes = re.findall(r"(?m)^- code: (ERR-[0-9]{2})$", catalog)
        enum_codes = re.findall(r'(?m)^    ERR_[0-9]{2}\("(ERR-[0-9]{2})",', enum_source)

        self.assertEqual(catalog_codes, enum_codes)
        self.assertEqual(len(enum_codes), len(set(enum_codes)))

    def test_event_envelope_fields_have_shared_headers_and_version_floor(self) -> None:
        """Keep the event schema envelope and AMQP metadata vocabulary aligned."""

        schema = json.loads(
            (ROOT / "contracts/events/envelope.schema.json").read_text(encoding="utf-8")
        )
        event_constants = (
            ROOT
            / "libs/ids/src/main/kotlin/com/subhrodip/squarewise/ids/events/EventConstants.kt"
        ).read_text(encoding="utf-8")
        header_values = set(
            re.findall(r'(?m)^        const val [A-Z_]+: String = "([^"]+)"', event_constants)
        )
        schema_version_match = re.search(
            r"CURRENT_SCHEMA_VERSION: Int = ([0-9]+)", event_constants
        )
        if schema_version_match is None:
            raise AssertionError("EventConstants must declare CURRENT_SCHEMA_VERSION")
        schema_version = int(schema_version_match.group(1))
        expected_headers = {
            "event-id",
            "event-type",
            "schema-version",
            "aggregate-id",
            "group-id",
            "group-revision",
            "occurred-at",
        }
        self.assertTrue(expected_headers.issubset(header_values))
        self.assertEqual(1, schema["properties"]["schemaVersion"]["minimum"])
        self.assertGreaterEqual(schema_version, schema["properties"]["schemaVersion"]["minimum"])
        self.assertEqual(
            {"eventId", "eventType", "schemaVersion", "aggregateId", "groupId", "groupRevision", "occurredAt", "payload"},
            set(schema["required"]),
        )

    def test_event_inventory_and_broker_acceptance_are_documented(self) -> None:
        """Keep the discovered event backlog tied to explicit acceptance criteria."""

        audit = (ROOT / "docs/quality/test-coverage-gap-audit.md").read_text(
            encoding="utf-8"
        )
        section = audit.split("### Event-type and routing inventory still requiring closure", 1)[1]
        for event_type in (
            "auth.email.requested.v1",
            "group.renamed.v1",
            "group.archived.v1",
            "member.placeholder_added.v1",
            "member.removed.v1",
            "invitation.revoked.v1",
            "invitation.claimed.v1",
            "expense.created",
            "expense.updated",
            "expense.deleted",
            "recurring.schedule.paused",
        ):
            with self.subTest(event_type=event_type):
                self.assertIn(f"`{event_type}`", section)
        for criterion in (
            "versioned registry",
            "RabbitMQ integration evidence",
            "acknowledgement, retry, deduplication, and dead-letter",
            "encrypted credentials never",
        ):
            with self.subTest(criterion=criterion):
                self.assertIn(criterion, section)

    def test_production_event_inventory_matches_documented_event_values(self) -> None:
        """Detect event-type additions or omissions before broker integration tests."""

        production_sources = [
            path.read_text(encoding="utf-8")
            for source_root in (ROOT / "app", ROOT / "libs")
            for path in source_root.rglob("*.kt")
            if "src" in path.parts and "main" in path.parts
        ]
        event_like_prefixes = (
            "auth.email.",
            "group.",
            "member.",
            "invitation.",
            "expense.",
            "recurring.",
            "settlement.",
        )
        source_literals = {
            value
            for source in production_sources
            for value in re.findall(r'"([a-z][a-z0-9_.-]+)"', source)
            if value.startswith(event_like_prefixes)
        }
        expected_versioned = {
            "auth.email.requested.v1",
            "group.renamed.v1",
            "group.archived.v1",
            "member.placeholder_added.v1",
            "member.removed.v1",
            "invitation.revoked.v1",
            "invitation.claimed.v1",
        }
        expected_unversioned = {
            "expense.created",
            "expense.updated",
            "expense.deleted",
            "recurring.schedule.paused",
        }
        versioned = {value for value in source_literals if value.endswith(".v1")}
        self.assertEqual(expected_versioned, versioned)
        self.assertTrue(expected_unversioned.issubset(source_literals))

        audit = (ROOT / "docs/quality/test-coverage-gap-audit.md").read_text(
            encoding="utf-8"
        )
        section = audit.split("### Event-type and routing inventory still requiring closure", 1)[1]
        for event_type in sorted(expected_versioned | expected_unversioned):
            with self.subTest(event_type=event_type):
                self.assertIn(f"`{event_type}`", section)

    def test_ordered_bruno_financial_fixture_preserves_required_state(self) -> None:
        """Keep financial requests valid until their dependent lifecycle teardown."""

        settlement = (
            ROOT / "tools/bruno/expense-core/settlements/record-settlement.bru"
        ).read_text(encoding="utf-8")
        self.assertRegex(settlement, r"(?m)^  Idempotency-Key: bruno-settlement-\{\{\$randomUUID\}\}$")

        lifecycle_member_removal = ROOT / "tools/bruno/expense-core/lifecycle/remove-member.bru"
        self.assertTrue(lifecycle_member_removal.is_file())
        self.assertFalse(
            (ROOT / "tools/bruno/expense-core/groups/remove-member.bru").exists()
        )

    def test_auth_email_e2e_destination_is_wired_and_documented(self) -> None:
        """Prevent the passwordless E2E suite from becoming an unexecuted orphan."""

        makefile = (ROOT / "Makefile").read_text(encoding="utf-8")
        workflow = (ROOT / ".github/workflows/_reusable-ci.yml").read_text(
            encoding="utf-8"
        )
        readme = (ROOT / "tests/e2e/README.md").read_text(encoding="utf-8")

        self.assertIn("e2e-auth-email:", makefile)
        self.assertIn("e2e-all: e2e-auth-email", makefile)
        self.assertIn("make e2e-auth-email", workflow)
        self.assertIn("test_auth_email_delivery.py", readme)

    def test_audit_requires_per_record_and_operation_evidence(self) -> None:
        audit = (ROOT / "docs/quality/test-coverage-gap-audit.md").read_text(
            encoding="utf-8"
        )

        for requirement in (
            "### Per-record closure contract",
            "test file and test name",
            "exact JaCoCo mapping",
            "| Authentication |",
            "| Authorization |",
            "| Durable state |",
            "| Asynchronous state |",
            "| Replay and concurrency |",
            "| Isolation and redaction |",
            "### Operation-specific E2E acceptance matrix",
            "### Current per-record ledger status",
            "does **not** yet have closure evidence for",
            "Passwordless authentication",
            "Expense financial/search",
            "Notifications: `getPreferences`",
            "### Reviewed structural branch candidates",
            "The private `ProfileController.mapErrorCode` record is intentionally **not**",
            "do not simplify the terminal guard",
        ):
            with self.subTest(requirement=requirement):
                self.assertIn(requirement, audit)

    def test_audit_has_complete_acceptance_cells_for_unit_and_e2e_rows(self) -> None:
        audit = (ROOT / "docs/quality/test-coverage-gap-audit.md").read_text(
            encoding="utf-8"
        )
        unit_rows = re.findall(
            r"(?m)^\| (QA10-(?:A0[1-8]|B0[1-4]|C0[1-6]|D0[1-4]|E0[1-5]))"
            r" \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$",
            audit,
        )
        self.assertEqual(27, len(unit_rows))
        for row, target, missing, acceptance in unit_rows:
            with self.subTest(row=row):
                self.assertTrue(target.strip())
                self.assertTrue(missing.strip())
                self.assertTrue(acceptance.strip())

        e2e_section = audit.split("## Missing deployed E2E and environment tests", 1)[1]
        e2e_rows = re.findall(
            r"(?m)^\| (QA10-E2E0[1-7]) \| ([^|]+) \| ([^|]+) \|$",
            e2e_section,
        )
        self.assertEqual(7, len(e2e_rows))
        for row, destination, acceptance in e2e_rows:
            with self.subTest(row=row):
                self.assertTrue(destination.strip())
                self.assertTrue(acceptance.strip())

    def test_audit_names_each_current_a07_method_target(self) -> None:
        audit = (ROOT / "docs/quality/test-coverage-gap-audit.md").read_text(
            encoding="utf-8"
        )
        targets = (
            "LoginVerificationService.kt:60",
            "SessionPolicy.kt:82",
        )
        for target in targets:
            with self.subTest(target=target):
                self.assertIn(target, audit)

    def test_audit_lists_every_current_gap_row_count_and_missing_e2e_operation(self) -> None:
        audit = (ROOT / "docs/quality/test-coverage-gap-audit.md").read_text(
            encoding="utf-8"
        )
        gaps = all_gaps(ROOT)
        row_counts = Counter(gap.qa_row for gap in gaps)
        for row, count in row_counts.items():
            with self.subTest(row=row):
                self.assertIn(f"| {row} | {count} |", audit)

        operations = inventory(ROOT)
        missing_e2e = [item.operation for item in operations if not item.has_e2e_signal]
        self.assertEqual(0, len(missing_e2e))
        matrix = audit.split("### Operation-specific E2E acceptance matrix", 1)[1].split(
            "The GraphQL roots currently have literal E2E references", 1
        )[0]
        for operation in (item.operation for item in operations):
            with self.subTest(operation=operation):
                self.assertRegex(
                    matrix,
                    rf"(?<![A-Za-z0-9_])`{re.escape(operation)}`(?![A-Za-z0-9_])",
                )

    def test_ci_documentation_uses_current_branch_baseline(self) -> None:
        ci = (ROOT / "docs/operations/ci.md").read_text(encoding="utf-8")

        self.assertIn("35 methods containing 61 missed", ci)
        self.assertNotIn("220 missed-branch methods", ci)

    def test_qa10_audit_documents_current_baseline_and_live_limitations(self) -> None:
        audit = (ROOT / "docs/quality/test-coverage-gap-audit.md").read_text(
            encoding="utf-8"
        )
        ledger = (ROOT / "docs/quality/qa10-operation-acceptance-ledger.md").read_text(
            encoding="utf-8"
        )

        self.assertIn(
            "freshly regenerated JaCoCo XML baseline records **35",
            audit,
        )
        self.assertIn("containing **61 missed branches**", audit)
        self.assertIn("The historical execution was **NOT-GREEN**", ledger)

    def test_qa10_task_detail_uses_current_baseline_not_historical_counts(self) -> None:
        detail = (ROOT / "docs/tasks/details/QA-10.md").read_text(encoding="utf-8")

        self.assertIn(
            "current regenerated authoritative ledger is\n35 records with 61 missed branches",
            detail,
        )
        self.assertNotIn(
            "The regenerated inventory now\nreports 86 records and 158 missed branches",
            detail,
        )
        self.assertNotIn(
            "The full wrapper run moved the current\ninventory from 41 records / 84 missed branches",
            detail,
        )

    def test_ci_python_tooling_is_locked_and_build_hooks_are_disabled(self) -> None:
        workflow = (ROOT / ".github/workflows/_reusable-ci.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            "astral-sh/setup-uv@d0cc045d04ccac9d8b7881df0226f9e82c39688e",
            workflow,
        )
        for line in workflow.splitlines():
            if "uv sync" in line or "uv run" in line:
                self.assertIn("--frozen", line)
                self.assertIn("--no-build", line)

    def test_ci_third_party_actions_are_immutable(self) -> None:
        workflow_paths = sorted((ROOT / ".github/workflows").glob("*.yml"))
        action_refs: list[str] = []
        for path in workflow_paths:
            action_refs.extend(
                re.findall(r"uses:\s*([^\s#]+)", path.read_text(encoding="utf-8"))
            )

        for ref in action_refs:
            owner = ref.split("/", 1)[0]
            if owner == "actions" or ref.startswith("./"):
                continue
            self.assertRegex(
                ref,
                r"^[^@]+@[0-9a-f]{40}$",
                msg=f"third-party action must use a full commit SHA: {ref}",
            )

    def test_change_audit_reaches_current_branch_tip(self) -> None:
        audit = (ROOT / "docs/quality/test-coverage-change-audit.md").read_text(
            encoding="utf-8"
        )

        for marker in (
            "through the current audited branch tip",
            "`2110ffd` is the explicit restoration/audit commit",
            "`3b3a3da` changes only `libs/security/build.gradle.kts`",
            "`cff7f76`\nchanges CI/Makefile Python execution",
            "`9b2fa85`",
            "`6ef0803`",
            "`52f270c`",
            "`bb35873`",
            "`8034208`",
            "`dbac7e7`",
            "`c4b8852`",
            "`69f2d27`",
            "`4b391a6`",
            "`cb02a39`",
            "`811bcdd`",
            "`2a532eb`",
            "`0e66e36`",
            "`7d8e316`",
            "`95390e8`",
            "`ce406b6`",
            "`7ee42a1`",
            "`0f79f86`",
            "`05c9417`",
            "`48fe0db`",
            "`0e267b7`",
            "`6b267bf`",
            "`fa41e93`",
            "`5007283`",
            "`01f5513`",
            "`9422d9a`",
            "`6f2d5f9`",
            "`4145a05`",
            "`eb045bb`",
            "`8b26630`",
            "`c5b924c`",
            "`9692227`",
            "`b60f458`",
            "`ff5fac3`",
            "`9b3e19e`",
            "`080a970`",
            "`cb41222`",
            "`47603a8`",
            "`53ad6ed`",
            "`3a98e20`",
            "`5e4a0a4`",
            "`a399b2a`",
            "`6adca2d`",
            "`13f6c30`",
            "`35c0242`",
            "`c4d3553`",
            "`dcdc2c2`",
            "`7a9689f`",
            "`433ebe2`",
            "`5592be7`",
            "`7d1fbce`",
            "`b84502b`",
            "`80c3f5d`",
            "`22a6be3`",
            "`dce224a`",
            "`bcb5c8a`",
            "`eb5b6f5`",
            "`efbc4b9`",
            "`7f184e4`",
            "`6a2027e`",
            "`a4c7f8f`",
            "`5fc7009`",
            "`bf15f07`",
            "`14eeb9c`",
            "`1974d8f`",
            "`d661fb8`",
            "with no production implementation\nor contract-file change",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, audit)


if __name__ == "__main__":
    unittest.main()
