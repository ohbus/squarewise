"""Unit and integration tests for acceptance test runner."""

from http.server import BaseHTTPRequestHandler, HTTPServer, ThreadingHTTPServer
import json
import socket
import tempfile
import threading
import unittest
from pathlib import Path
from urllib.parse import urlparse
import sys
from tests.http_constants import ACCEPTANCE_FAULT, AUTHORIZATION, CONTENT_LENGTH, CONTENT_TYPE, GRAPHQL_PATH

sys.path.insert(0, str(Path(__file__).resolve().parent))
try:
    from . import runner
except ImportError:
    import runner


class MockServicesHandler(BaseHTTPRequestHandler):
    """Mock HTTP handler simulating BFF and Expense Core endpoints."""

    def log_message(self, format: str, *args: object) -> None:
        # Suppress standard HTTP server console logging during tests
        pass

    renames = 0

    def do_PATCH(self) -> None:
        if self.headers.get(AUTHORIZATION) != "Bearer test-user":
            self.send_response(401)
            self.send_header(CONTENT_TYPE, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"code": "UNAUTHENTICATED"}).encode("utf-8"))
            return
        if self.headers.get(ACCEPTANCE_FAULT) == "rollback":
            self.send_response(409)
            self.send_header(CONTENT_TYPE, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"code": "TEST_ROLLBACK"}).encode("utf-8"))
            return
        MockServicesHandler.renames += 1
        status = 409 if MockServicesHandler.renames > 1 else 200
        self.send_response(status)
        self.send_header(CONTENT_TYPE, "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"revision": 1}).encode("utf-8"))

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path

        if path.endswith("/members"):
            if self.headers.get(AUTHORIZATION) != "Bearer test-user":
                self.send_response(401)
                self.send_header(CONTENT_TYPE, "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"code": "UNAUTHENTICATED"}).encode("utf-8"))
                return
            self.send_response(200)
            self.send_header(CONTENT_TYPE, "application/json")
            self.end_headers()
            self.wfile.write(
                json.dumps(
                    [
                        {"membershipId": "00000000-0000-0000-0000-000000000001", "status": "ACTIVE"},
                        {"membershipId": "00000000-0000-0000-0000-000000000002", "status": "ACTIVE"},
                    ]
                ).encode("utf-8")
            )
            return

        if path == "/actuator/health":
            self.send_response(200)
            self.send_header(CONTENT_TYPE, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "UP"}).encode("utf-8"))
            return

        if path.endswith("/balances"):
            self.send_response(200)
            self.send_header(CONTENT_TYPE, "application/json")
            self.end_headers()
            self.wfile.write(
                json.dumps(
                    {
                        "groupId": "00000000-0000-0000-0000-000000000001",
                        "balances": [
                            {
                                "participantId": "00000000-0000-0000-0000-000000000002",
                                "amount": {"currency": "EUR", "minor": "0"},
                            }
                        ],
                    }
                ).encode("utf-8")
            )
            return

        if path.endswith("/sync/snapshot") or path.endswith("/sync/changes"):
            self.send_response(200)
            self.send_header(CONTENT_TYPE, "application/json")
            self.end_headers()
            self.wfile.write(
                json.dumps(
                    {
                        "changes": [
                            {
                                "revision": 1,
                                "entityId": "00000000-0000-0000-0000-000000000003",
                                "deleted": False,
                                "payload": '{"amountMinor":1000}',
                            }
                        ],
                        "nextCursor": None,
                        "hasMore": False,
                    }
                ).encode("utf-8")
            )
            return

        if path == "/expense-core/v1/groups":
            self.send_response(200)
            self.send_header(CONTENT_TYPE, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps([{"groupId": "00000000-0000-0000-0000-000000000001"}]).encode("utf-8"))
            return

        self.send_response(404)
        self.end_headers()

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path
        length = int(self.headers.get(CONTENT_LENGTH, 0))
        body = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"

        if path == "/expense-core/v1/groups":
            self.send_response(201)
            self.send_header(CONTENT_TYPE, "application/json")
            self.end_headers()
            self.wfile.write(
                json.dumps(
                    {
                        "groupId": "00000000-0000-0000-0000-000000000001",
                        "name": "Acceptance Trip",
                        "revision": 0,
                    }
                ).encode("utf-8")
            )
            return

        if "/expenses" in path:
            self.send_response(201)
            self.send_header(CONTENT_TYPE, "application/json")
            self.end_headers()
            self.wfile.write(
                json.dumps(
                    {
                        "expenseId": "00000000-0000-0000-0000-000000000003",
                        "version": 1,
                        "amount": {"currency": "EUR", "minor": "1000"},
                    }
                ).encode("utf-8")
            )
            return

        if "/settlements" in path:
            self.send_response(201)
            self.send_header(CONTENT_TYPE, "application/json")
            self.end_headers()
            self.wfile.write(
                json.dumps(
                    {
                        "id": "00000000-0000-0000-0000-000000000004",
                        "status": "RECORDED",
                    }
                ).encode("utf-8")
            )
            return

        if path == GRAPHQL_PATH:
            self.send_response(200)
            self.send_header(CONTENT_TYPE, "application/json")
            self.end_headers()
            parsed_body = json.loads(body) if body else {}
            query = parsed_body.get("query", "")
            if "fanoutFailure" in query:
                self.wfile.write(
                    json.dumps(
                        {
                            "errors": [
                                {
                                    "message": "upstream service unavailable",
                                    "extensions": {"code": "UPSTREAM_UNAVAILABLE"},
                                }
                            ]
                        }
                    ).encode("utf-8")
                )
            else:
                self.wfile.write(
                    json.dumps(
                        {
                            "data": {
                                "groups": [
                                    {
                                        "id": "00000000-0000-0000-0000-000000000001",
                                        "name": "Acceptance Trip",
                                    }
                                ]
                            }
                        }
                    ).encode("utf-8")
                )
            return

        self.send_response(404)
        self.end_headers()


def find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class RunnerTest(unittest.TestCase):
    def setUp(self) -> None:
        MockServicesHandler.renames = 0

    @classmethod
    def setUpClass(cls) -> None:
        MockServicesHandler.renames = 0
        cls.port = find_free_port()
        cls.server = ThreadingHTTPServer(("127.0.0.1", cls.port), MockServicesHandler)
        cls.server_thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.server_thread.start()
        cls.server_url = f"http://127.0.0.1:{cls.port}"

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()

    def test_argument_parsing_defaults(self) -> None:
        args = runner.parse_args([])
        self.assertEqual(args.task_id, "QA-04")
        self.assertFalse(args.require_services)
        self.assertEqual(args.timeout, 2.0)
        self.assertEqual(args.bff_url, "http://localhost:28080")
        self.assertEqual(args.expense_core_url, "http://localhost:28082")
        self.assertIsNone(args.report_path)

    def test_argument_parsing_custom(self) -> None:
        args = runner.parse_args([
            "--task-id", "QA-01",
            "--require-services",
            "--timeout", "4.5",
            "--bff-url", "http://bff:9000",
            "--expense-core-url", "http://expense:9002",
            "--report-path", "custom-report.json",
        ])
        self.assertEqual(args.task_id, "QA-01")
        self.assertTrue(args.require_services)
        self.assertEqual(args.timeout, 4.5)
        self.assertEqual(args.bff_url, "http://bff:9000")
        self.assertEqual(args.expense_core_url, "http://expense:9002")
        self.assertEqual(args.report_path, Path("custom-report.json"))

    def test_probe_success(self) -> None:
        status, detail = runner.probe(f"{self.server_url}/actuator/health", timeout=1.0)
        self.assertEqual(status, "passed")
        self.assertEqual(detail, "HTTP 200 status=UP")

    def test_probe_connection_refused(self) -> None:
        unused_port = find_free_port()
        status, detail = runner.probe(f"http://127.0.0.1:{unused_port}/actuator/health", timeout=0.5)
        self.assertEqual(status, "blocked")
        self.assertRegex(detail, r"Services offline: (Connection refused|timed out)")

    def test_check_contracts(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        result = runner.check_contracts(repo_root)
        self.assertEqual(result["id"], "QA-CONTRACTS")
        self.assertEqual(result["status"], "passed")
        self.assertEqual(result["exit_code"], 0)

    def test_all_scenarios_pass_against_mock_services(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as tmpdir:
            temp_report = Path(tmpdir) / "qa-04.json"
            exit_code, report = runner.run_acceptance_suite(
                repo_root=repo_root,
                bff_url=self.server_url,
                expense_core_url=self.server_url,
                timeout=2.0,
                require_services=True,
                report_path=temp_report,
                task_id="QA-04",
            )
            self.assertEqual(exit_code, 0)
            self.assertTrue(temp_report.exists())
            self.assertEqual(report["task"], "QA-04")

            statuses = {r["id"]: r["status"] for r in report["results"]}
            self.assertEqual(statuses["QA-CONTRACTS"], "passed")
            self.assertEqual(statuses["QA-HEALTH"], "passed")
            self.assertEqual(statuses["QA-GROUP-EXPENSE-SETTLEMENT"], "passed")
            self.assertEqual(statuses["QA-OFFLINE-REPLAY"], "passed")
            self.assertEqual(statuses["QA-GRAPHQL-HTTP-RESYNC"], "passed")
            self.assertEqual(statuses["QA05-ROLLBACK"], "passed")
            self.assertEqual(statuses["QA05-CONCURRENCY"], "passed")
            self.assertEqual(statuses["QA05-AUTHORIZATION"], "passed")
            self.assertEqual(statuses["QA05-BFF-FANOUT"], "passed")
            self.assertEqual(statuses["QA05-RECOVERY"], "passed")

    def test_offline_services_without_require_services(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        unused_port = find_free_port()
        offline_url = f"http://127.0.0.1:{unused_port}"

        with tempfile.TemporaryDirectory() as tmpdir:
            temp_report = Path(tmpdir) / "qa-01.json"
            exit_code, report = runner.run_acceptance_suite(
                repo_root=repo_root,
                bff_url=offline_url,
                expense_core_url=offline_url,
                timeout=0.2,
                require_services=False,
                report_path=temp_report,
            )
            self.assertEqual(exit_code, 0)
            statuses = {r["id"]: r["status"] for r in report["results"]}
            self.assertEqual(statuses["QA-HEALTH"], "blocked")
            self.assertEqual(statuses["QA-GROUP-EXPENSE-SETTLEMENT"], "blocked")
            self.assertEqual(statuses["QA-OFFLINE-REPLAY"], "blocked")
            self.assertEqual(statuses["QA-GRAPHQL-HTTP-RESYNC"], "blocked")

    def test_offline_services_with_require_services(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        unused_port = find_free_port()
        offline_url = f"http://127.0.0.1:{unused_port}"

        with tempfile.TemporaryDirectory() as tmpdir:
            temp_report = Path(tmpdir) / "qa-01.json"
            exit_code, _ = runner.run_acceptance_suite(
                repo_root=repo_root,
                bff_url=offline_url,
                expense_core_url=offline_url,
                timeout=0.2,
                require_services=True,
                report_path=temp_report,
            )
            self.assertEqual(exit_code, 1)

    def test_service_failure_marks_scenario_failed(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        # Start a server that returns 500 for /actuator/health
        class FailingHandler(BaseHTTPRequestHandler):
            def log_message(self, format: str, *args: object) -> None:
                pass
            def do_GET(self) -> None:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(b'{"error": "internal error"}')
            def do_POST(self) -> None:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(b'{"error": "internal error"}')

        port = find_free_port()
        fail_server = HTTPServer(("127.0.0.1", port), FailingHandler)
        thread = threading.Thread(target=fail_server.serve_forever, daemon=True)
        thread.start()
        try:
            fail_url = f"http://127.0.0.1:{port}"
            with tempfile.TemporaryDirectory() as tmpdir:
                temp_report = Path(tmpdir) / "qa-01.json"
                exit_code, report = runner.run_acceptance_suite(
                    repo_root=repo_root,
                    bff_url=fail_url,
                    expense_core_url=fail_url,
                    timeout=1.0,
                    require_services=False,
                    report_path=temp_report,
                )
                self.assertEqual(exit_code, 1)
                health = next(r for r in report["results"] if r["id"] == "QA-HEALTH")
                self.assertEqual(health["status"], "failed")
        finally:
            fail_server.shutdown()
            fail_server.server_close()


if __name__ == "__main__":
    unittest.main()
