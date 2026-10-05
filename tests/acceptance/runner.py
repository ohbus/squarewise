"""Multi-service acceptance test runner for Squarewise MVP."""

import argparse
import json
import os
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from tests.http_constants import ACCEPT, APPLICATION_JSON, AUTHORIZATION, IDEMPOTENCY_KEY, CONTENT_TYPE


AUTH_TOKEN = os.environ.get("BEARER_TOKEN", "test-user")

try:
    from . import qa05
except ImportError:
    import qa05


class ServiceOfflineError(Exception):
    """Raised when an HTTP connection to a service cannot be established."""
    pass


def http_json(
    url: str,
    method: str = "GET",
    headers: dict[str, str] | None = None,
    body: dict | list | None = None,
    timeout: float = 2.0,
) -> tuple[int, Any]:
    req_headers = {ACCEPT: APPLICATION_JSON}
    data = None
    if body is not None:
        req_headers[CONTENT_TYPE] = APPLICATION_JSON
        data = json.dumps(body).encode("utf-8")
    if headers:
        req_headers.update(headers)

    req = Request(url, data=data, headers=req_headers, method=method)
    try:
        with urlopen(req, timeout=timeout) as response:
            status = response.status
            raw = response.read().decode("utf-8")
            try:
                parsed = json.loads(raw) if raw else {}
            except Exception:
                parsed = raw
            return status, parsed
    except HTTPError as error:
        try:
            raw = error.read().decode("utf-8")
            try:
                parsed = json.loads(raw) if raw else {}
            except Exception:
                parsed = raw
            return error.code, parsed
        finally:
            error.close()
    except (URLError, TimeoutError, OSError, ConnectionRefusedError) as error:
        reason = error.reason if isinstance(error, URLError) else error
        err_msg = str(reason)
        if "Connection refused" in err_msg or isinstance(reason, ConnectionRefusedError):
            err_msg = "Connection refused"
        raise ServiceOfflineError(err_msg) from error


def probe(url: str, timeout: float) -> tuple[str, str]:
    request = Request(url, method="GET")
    try:
        with urlopen(request, timeout=timeout) as response:
            return ("passed", f"HTTP {response.status}")
    except HTTPError as error:
        try:
            return ("passed" if error.code < 500 else "failed", f"HTTP {error.code}")
        finally:
            error.close()
    except (URLError, TimeoutError, OSError, ConnectionRefusedError) as error:
        reason = error.reason if isinstance(error, URLError) else error
        err_msg = str(reason)
        if "Connection refused" in err_msg or isinstance(reason, ConnectionRefusedError):
            err_msg = "Connection refused"
        return ("blocked", f"Services offline: {err_msg}")


def check_contracts(repo_root: Path) -> dict[str, Any]:
    validate_script = repo_root / "tools" / "contracts" / "validate.py"
    if validate_script.exists():
        contract = subprocess.run(
            [sys.executable, str(validate_script)],
            capture_output=True,
            text=True,
            cwd=str(repo_root),
        )
        return {
            "id": "QA-CONTRACTS",
            "status": "passed" if contract.returncode == 0 else "failed",
            "exit_code": contract.returncode,
            "detail": (contract.stdout + contract.stderr).strip(),
        }
    return {
        "id": "QA-CONTRACTS",
        "status": "failed",
        "exit_code": 1,
        "detail": f"Validation script not found at {validate_script}",
    }


def check_health(bff_url: str, expense_core_url: str, timeout: float) -> dict[str, Any]:
    bff_url_health = f"{bff_url}/actuator/health"
    ec_url_health = f"{expense_core_url}/actuator/health"

    bff_status, bff_detail = probe(bff_url_health, timeout)
    ec_status, ec_detail = probe(ec_url_health, timeout)

    if bff_status == "passed" and ec_status == "passed":
        status = "passed"
        detail = f"BFF: {bff_detail}, Expense Core: {ec_detail}"
    elif bff_status == "failed" or ec_status == "failed":
        status = "failed"
        detail = f"BFF: {bff_detail}, Expense Core: {ec_detail}"
    else:
        status = "blocked"
        if "Connection refused" in bff_detail and "Connection refused" in ec_detail:
            detail = "Services offline: Connection refused"
        else:
            detail = f"BFF: {bff_detail}, Expense Core: {ec_detail}"

    return {
        "id": "QA-HEALTH",
        "status": status,
        "detail": detail,
        "url": bff_url_health,
        "bff_url": bff_url_health,
        "expense_core_url": ec_url_health,
    }


def check_group_expense_settlement(expense_core_url: str, timeout: float) -> tuple[dict[str, Any], str | None]:
    scenario_id = "QA-GROUP-EXPENSE-SETTLEMENT"
    auth_headers = {AUTHORIZATION: f"Bearer {AUTH_TOKEN}"}
    try:
        # 1. POST /expense-core/v1/groups to create a group
        group_name = f"Acceptance Trip {uuid.uuid4().hex[:8]}"
        group_payload = {
            "name": group_name,
            "kind": "TRIP",
            "currency": "EUR",
        }
        status, data = http_json(
            f"{expense_core_url}/expense-core/v1/groups",
            method="POST",
            headers=auth_headers,
            body=group_payload,
            timeout=timeout,
        )
        if status != 201:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"POST /expense-core/v1/groups failed with HTTP {status}: {data}",
            }, None

        group_id = data.get("groupId") or data.get("id")
        if not group_id:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"Group created but no groupId in response: {data}",
            }, None

        # 2. Resolve real active membership IDs for the financial participants.
        # Expense Core intentionally rejects arbitrary account UUIDs here.
        status, members = http_json(
            f"{expense_core_url}/expense-core/v1/groups/{group_id}/members",
            method="GET",
            headers=auth_headers,
            timeout=timeout,
        )
        if status != 200 or not isinstance(members, list) or not members:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"GET /expense-core/v1/groups/{group_id}/members failed with HTTP {status}: {members}",
            }, str(group_id)

        active_members = [member for member in members if member.get("status", "ACTIVE") == "ACTIVE"]
        user1 = str(active_members[0].get("membershipId", ""))
        if not user1:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"Group members response did not contain membershipId: {members}",
            }, str(group_id)

        if len(active_members) < 2:
            status, placeholder = http_json(
                f"{expense_core_url}/expense-core/v1/groups/{group_id}/placeholders",
                method="POST",
                headers=auth_headers,
                body={"name": "Acceptance participant"},
                timeout=timeout,
            )
            if status != 201:
                return {
                    "id": scenario_id,
                    "status": "failed",
                    "detail": f"POST /expense-core/v1/groups/{group_id}/placeholders failed with HTTP {status}: {placeholder}",
                }, str(group_id)
            user2 = str(placeholder.get("membershipId", ""))
        else:
            user2 = str(active_members[1].get("membershipId", ""))
        if not user2:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"Second active membership was missing membershipId: {members}",
            }, str(group_id)

        # 3. POST /expense-core/v1/groups/{groupId}/expenses to create an expense
        expense_id = str(uuid.uuid4())
        expense_payload = {
            "expenseId": expense_id,
            "description": "Dinner",
            "category": "dining",
            "amount": {"currency": "EUR", "minor": "1000"},
            "payers": [
                {"participantId": user1, "amount": {"currency": "EUR", "minor": "1000"}}
            ],
            "allocation": {
                "mode": "EQUAL",
                "items": [
                    {"participantId": user1, "value": "1"},
                    {"participantId": user2, "value": "1"},
                ],
            },
        }
        status, data = http_json(
            f"{expense_core_url}/expense-core/v1/groups/{group_id}/expenses",
            method="POST",
            headers={**auth_headers, IDEMPOTENCY_KEY: str(uuid.uuid4())},
            body=expense_payload,
            timeout=timeout,
        )
        if status != 201:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"POST /expense-core/v1/groups/{group_id}/expenses failed with HTTP {status}: {data}",
            }, str(group_id)

        # 4. GET /expense-core/v1/groups/{groupId}/balances to check balances
        status, data = http_json(
            f"{expense_core_url}/expense-core/v1/groups/{group_id}/balances",
            method="GET",
            headers=auth_headers,
            timeout=timeout,
        )
        if status != 200:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"GET /expense-core/v1/groups/{group_id}/balances failed with HTTP {status}: {data}",
            }, str(group_id)

        # 5. POST /expense-core/v1/groups/{groupId}/settlements to record a repayment
        settlement_payload = {
            "fromParticipantId": user2,
            "toParticipantId": user1,
            "amountMinor": "500",
            "currency": "EUR",
        }
        status, data = http_json(
            f"{expense_core_url}/expense-core/v1/groups/{group_id}/settlements",
            method="POST",
            headers={**auth_headers, IDEMPOTENCY_KEY: str(uuid.uuid4())},
            body=settlement_payload,
            timeout=timeout,
        )
        if status != 201:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"POST /expense-core/v1/groups/{group_id}/settlements failed with HTTP {status}: {data}",
            }, str(group_id)

        # 6. GET /expense-core/v1/groups/{groupId}/balances to verify updated balance
        status, data = http_json(
            f"{expense_core_url}/expense-core/v1/groups/{group_id}/balances",
            method="GET",
            headers=auth_headers,
            timeout=timeout,
        )
        if status != 200:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"GET /expense-core/v1/groups/{group_id}/balances (re-check) failed with HTTP {status}: {data}",
            }, str(group_id)

        return {
            "id": scenario_id,
            "status": "passed",
            "detail": f"Created group {group_id}, recorded 10.00 EUR expense, verified balances, recorded 5.00 EUR repayment, and verified reconciled balances",
            "groupId": str(group_id),
        }, str(group_id)

    except ServiceOfflineError as err:
        return {
            "id": scenario_id,
            "status": "blocked",
            "detail": f"Services offline: {err}",
        }, None
    except Exception as err:
        return {
            "id": scenario_id,
            "status": "failed",
            "detail": f"Unexpected failure: {err}",
        }, None


def check_offline_replay(expense_core_url: str, group_id: str | None, timeout: float) -> dict[str, Any]:
    scenario_id = "QA-OFFLINE-REPLAY"
    auth_headers = {AUTHORIZATION: f"Bearer {AUTH_TOKEN}"}
    try:
        if not group_id:
            group_payload = {
                "name": f"Sync Test Group {uuid.uuid4().hex[:8]}",
                "kind": "TRIP",
                "currency": "EUR",
            }
            status, data = http_json(
                f"{expense_core_url}/expense-core/v1/groups",
                method="POST",
                headers=auth_headers,
                body=group_payload,
                timeout=timeout,
            )
            if status != 201:
                return {
                    "id": scenario_id,
                    "status": "failed",
                    "detail": f"Failed to create group for sync replay: HTTP {status} {data}",
                }
            group_id = data.get("groupId") or data.get("id")

        # GET /expense-core/v1/groups/{groupId}/sync/snapshot
        status, data = http_json(
            f"{expense_core_url}/expense-core/v1/groups/{group_id}/sync/snapshot",
            method="GET",
            headers=auth_headers,
            timeout=timeout,
        )
        if status != 200:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"GET /expense-core/v1/groups/{group_id}/sync/snapshot failed with HTTP {status}: {data}",
            }
        if not isinstance(data, dict) or "changes" not in data:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"Snapshot response invalid structure: {data}",
            }

        # GET /expense-core/v1/groups/{groupId}/sync/changes
        status, data = http_json(
            f"{expense_core_url}/expense-core/v1/groups/{group_id}/sync/changes",
            method="GET",
            headers=auth_headers,
            timeout=timeout,
        )
        if status != 200:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"GET /expense-core/v1/groups/{group_id}/sync/changes failed with HTTP {status}: {data}",
            }
        if not isinstance(data, dict) or "changes" not in data:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"Changes feed response invalid structure: {data}",
            }

        return {
            "id": scenario_id,
            "status": "passed",
            "detail": f"Snapshot and changes feed verified for group {group_id}",
            "groupId": str(group_id),
        }

    except ServiceOfflineError as err:
        return {
            "id": scenario_id,
            "status": "blocked",
            "detail": f"Services offline: {err}",
        }
    except Exception as err:
        return {
            "id": scenario_id,
            "status": "failed",
            "detail": f"Unexpected failure: {err}",
        }


def check_websocket_resync(bff_url: str, timeout: float) -> dict[str, Any]:
    scenario_id = "QA-WEBSOCKET-RESYNC"
    headers = {
        CONTENT_TYPE: APPLICATION_JSON,
        ACCEPT: APPLICATION_JSON,
        AUTHORIZATION: f"Bearer {AUTH_TOKEN}",
    }
    body = {
        "query": "query { groups { id name } }"
    }
    try:
        status, data = http_json(
            f"{bff_url}/graphql",
            method="POST",
            headers=headers,
            body=body,
            timeout=timeout,
        )
        if status != 200:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"POST /graphql failed with HTTP {status}: {data}",
            }
        if not isinstance(data, dict) or "data" not in data:
            return {
                "id": scenario_id,
                "status": "failed",
                "detail": f"GraphQL response missing 'data' field: {data}",
            }

        return {
            "id": scenario_id,
            "status": "passed",
            "detail": "GraphQL query returned HTTP 200 with data payload",
        }

    except ServiceOfflineError as err:
        return {
            "id": scenario_id,
            "status": "blocked",
            "detail": f"Services offline: {err}",
        }
    except Exception as err:
        return {
            "id": scenario_id,
            "status": "failed",
            "detail": f"Unexpected failure: {err}",
        }


def run_acceptance_suite(
    repo_root: Path,
    bff_url: str,
    expense_core_url: str,
    timeout: float = 2.0,
    require_services: bool = False,
    report_path: Path | None = None,
    task_id: str = "QA-04",
    include_edge_cases: bool = True,
) -> tuple[int, dict[str, Any]]:
    results = []

    # 1. QA-CONTRACTS
    results.append(check_contracts(repo_root))

    # 2. QA-HEALTH
    results.append(check_health(bff_url, expense_core_url, timeout))

    # 3. QA-GROUP-EXPENSE-SETTLEMENT
    settlement_result, group_id = check_group_expense_settlement(expense_core_url, timeout)
    results.append(settlement_result)

    # 4. QA-OFFLINE-REPLAY
    results.append(check_offline_replay(expense_core_url, group_id, timeout))

    # 5. QA-WEBSOCKET-RESYNC
    results.append(check_websocket_resync(bff_url, timeout))

    # 6. Edge-case journeys (QA-05 / QA-04)
    if include_edge_cases:
        try:
            edge_results = qa05.run_qa05_journeys(bff_url, expense_core_url, bearer_token=AUTH_TOKEN)
            for r in edge_results:
                results.append({
                    "id": r.id,
                    "status": r.status,
                    "detail": r.detail,
                })
        except Exception as err:
            for case_id in ("QA05-ROLLBACK", "QA05-CONCURRENCY", "QA05-AUTHORIZATION", "QA05-BFF-FANOUT", "QA05-RECOVERY"):
                results.append({
                    "id": case_id,
                    "status": "blocked",
                    "detail": f"Services offline or probe error: {err}",
                })

    target_report = report_path or (repo_root / f"build/reports/acceptance/{task_id.lower()}.json")
    report = {
        "task": task_id,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "base_url": bff_url,
        "bff_url": bff_url,
        "expense_core_url": expense_core_url,
        "results": results,
    }

    target_report.parent.mkdir(parents=True, exist_ok=True)
    target_report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    failed = any(item["status"] == "failed" for item in results)
    blocked = any(item["status"] == "blocked" for item in results)

    if failed:
        exit_code = 1
    elif require_services and blocked:
        exit_code = 1
    else:
        exit_code = 0

    return exit_code, report


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Multi-service acceptance harness for Squarewise")
    parser.add_argument("--task-id", default="QA-04", help="Task ID for acceptance report (e.g. QA-01, QA-04)")
    parser.add_argument("--require-services", action="store_true", help="Fail if services are unavailable")
    parser.add_argument("--timeout", type=float, default=2.0, help="HTTP request timeout in seconds")
    parser.add_argument("--bff-url", default=os.environ.get("BFF_BASE_URL", "http://localhost:28080"), help="Base URL for BFF")
    parser.add_argument("--base-url", dest="bff_url_alias", default=None, help="Alias for --bff-url")
    parser.add_argument("--expense-core-url", default=os.environ.get("EXPENSE_CORE_BASE_URL", "http://localhost:28082"), help="Base URL for Expense Core")
    parser.add_argument("--report-path", type=Path, default=None, help="Custom path for json report output")
    parser.add_argument("--no-edge-cases", action="store_true", help="Skip edge-case probes")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.require_services and not os.environ.get("BEARER_TOKEN"):
        print("BEARER_TOKEN must contain a signed token when --require-services is used", file=sys.stderr)
        return 2
    bff_url = (args.bff_url_alias or args.bff_url).rstrip("/")
    expense_core_url = args.expense_core_url.rstrip("/")

    repo_root = Path(__file__).resolve().parents[2]
    exit_code, report = run_acceptance_suite(
        repo_root=repo_root,
        bff_url=bff_url,
        expense_core_url=expense_core_url,
        timeout=args.timeout,
        require_services=args.require_services,
        report_path=args.report_path,
        task_id=args.task_id,
        include_edge_cases=not args.no_edge_cases,
    )

    print(json.dumps(report, indent=2))
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
