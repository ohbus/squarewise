# QA-10 test-gap summary

**Status:** In progress  
**Last regenerated:** 2026-10-02  
**Scope:** unit, integration, deployed E2E, and environment-owned acceptance evidence

This page is the reviewer entry point. The linked ledgers are authoritative for
the exact records; this summary separates discovery signals from executed test
evidence.

## Evidence state

| Area | Current evidence | Still missing |
| --- | --- | --- |
| JaCoCo branches | 37 production methods / 63 missed branches; all assigned to a QA row, record-level acceptance criterion, and test boundary | 4 structural-invariant mappings are reviewed against boundary tests; 33 identity/session/network/origin/recurrence/search/sync/notification/realtime/gateway/decoder/settlement/database/recurring/group-store/error/telemetry mappings are behavior-covered at tested boundaries; 0 open design |
| Exact branch lines | 50 source-line records account for all 63 missed branches | Each line remains open until behavior proof or an evidence-backed structural classification is recorded |
| Concrete execution | 1 zero-instruction method is recorded separately | Inline telemetry (DbTelemetry.measureQuery) remains a compiler mapping |
| Contract operations | 54 operations: 45 REST and 9 GraphQL; 54 request-shaped source signals | Static source is not execution evidence; every operation still needs its required persona, failure, durability, async, concurrency, and isolation artifact |
| Bruno source | 18 operation references; 36 operations have no Bruno source reference | Bruno is supplementary and cannot substitute for the deployed E2E matrix; surface-aware path/method matching prefers false negatives over cross-operation credit |
| Runtime E2E | Fresh isolated `qa10` local-OIDC stack is green: acceptance scenarios passed, signed Bruno passed 71/71 requests and 78/78 assertions, and the product lifecycle journey passed | Hosted-CI rerun and complete operation attribution remain open; the normalized local artifact credits only 18 uniquely attributable operations and leaves 36 source-only |

## Required closure rule

A source reference, test-file reference, green unit suite, or shared happy-path
journey does not close a record by itself. Closure requires the exact behavior
listed in the record-level acceptance criteria, including authorization and
negative paths, durable state, messaging/outbox effects, replay/concurrency,
isolation, and redaction where applicable.

Structural classification is permitted only when the existing behavior tests
cover every reachable path and source/bytecode review proves the residual branch
is compiler-generated, unreachable under an enforced invariant, or a framework
instrumentation mapping. No implementation deletion, JaCoCo exclusion, guard
weakening, or contract change is an acceptable coverage shortcut.

## Authoritative ledgers

- [Repository-wide gap audit](test-coverage-gap-audit.md): scope, QA-row matrix, and unit/integration/E2E acceptance criteria.
- [Current branch ledger](qa10-current-branch-gap-ledger.md): method-level JaCoCo records, exact test targets, and per-record acceptance.
- [Branch-line ledger](qa10-branch-line-gap-ledger.md): every JaCoCo source line with missed branches.
- [Concrete execution-gap ledger](qa10-execution-gap-ledger.md): methods with no covered instructions.
- [Operation acceptance ledger](qa10-operation-acceptance-ledger.md): all REST/GraphQL operations, source signals, and required deployed evidence.

## Reproduction commands

```text
./gradlew.bat test jacocoTestReport --rerun-tasks --no-daemon --console=plain
uv run --frozen --no-build python tools/coverage/report_branch_gaps.py --format markdown
uv run --frozen --no-build python tools/coverage/report_branch_lines.py --format markdown
uv run --frozen --no-build python tools/coverage/report_execution_gaps.py --format markdown
uv run --frozen --no-build python tools/coverage/report_operation_test_gaps.py --format markdown
make acceptance-live
make e2e-live
```

The first four commands provide local discovery evidence. The final two are
environment-owned and must produce retained execution artifacts before QA-10 can
claim deployed acceptance closure. On this Windows workstation GNU Make is not
installed; use the direct `uv run ...` target-equivalent commands documented in
the QA-10 progress ledger when reproducing the local evidence.
