# ERRC-26: Performance, allocation, and regional load evidence

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 5 — Clients, acceptance, scale, rollout
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Conduct rigorous micro-benchmarks (JMH) and regional load testing (k6) to measure memory allocation, error resolution latency, exception instantiation overhead, and garbage collection pressure under heavy failure storms. Provide empirical evidence proving that the static six-digit error architecture supports Squarewise's 10M+-DAU scale targets without memory leaks, CPU spikes, or latency degradation.

## Dependencies

- Preceding: [`ERRC-19`](ERRC-19.md), [`ERRC-20`](ERRC-20.md), [`ERRC-21`](ERRC-21.md), [`ERRC-22`](ERRC-22.md), [`ERRC-23`](ERRC-23.md), [`ERRC-24`](ERRC-24.md), [`ERRC-25`](ERRC-25.md)

## Owned Paths

- `docs/tasks/details/ERRC-26.md`
- `tools/benchmarks/jmh/`
- `tests/performance/k6/error_storm_test.js`
- `docs/operations/performance-budget-evidence.md`

## Architecture & Design Patterns

- **Measure Before Optimize Principle**: Rely on empirical, reproducible profiler metrics rather than theoretical speed claims.
- **Allocation-Free Hot Path**: Verify that resolving an `ErrorCode` and mapping to an `ErrorDefinition` incurs zero new heap object allocations on the happy or failure resolution path.
- **Backpressure & Graceful Degradation Under Load**: Verify that downstream error storms (e.g. 5,000 invalid requests/sec) do not exhaust JVM heap or degrade performance of concurrent valid 2xx transactions.
- **Cardinally Bounded Telemetry Overhead**: Verify that emitting Micrometer error metrics during high-throughput failure spikes does not introduce GC pauses or memory bloat.

## Common Libraries & Framework Integration

- **Java Microbenchmark Harness (JMH)**: Nanosecond-accurate execution and allocation profiling (`-prof gc`).
- **k6 Load Testing**: High-throughput distributed HTTP error injection.
- **`libs/observability`**: Micrometer metric overhead verification.

## Technical Requirements & Deliverables

1. **JMH Microbenchmarks (`tools/benchmarks/jmh/`)**:
   - `ErrorCodeResolutionBenchmark.kt`:
     - Measures latency and heap allocations for `ErrorCode("213201")` decomposition and static catalog lookup.
     - Target: < 20 nanoseconds per operation, 0 bytes/op allocated.
   - `ProblemSerializationBenchmark.kt`:
     - Measures Jackson JSON serialization of `ProblemDetailsDto`.
     - Target: < 5 microseconds per serialization.
   - `ExceptionCreationBenchmark.kt`:
     - Compares standard exception stack capture vs governed `SquarewiseException`.
2. **k6 Error Storm Scenario (`tests/performance/k6/error_storm_test.js`)**:
   - Injects sustained 2,500 errors/sec (validation failures, auth rejections, missing resources) alongside 2,500 valid operations/sec.
   - Measures p95 and p99 response latencies, CPU utilization, and JVM heap growth over 10-minute duration.
3. **Performance Evidence Documentation (`docs/operations/performance-budget-evidence.md`)**:
   - Documents exact benchmark environment (CPU, RAM, JVM version, JVM flags).
   - Records baseline vs six-digit error handling performance numbers and GC profiles.

## Acceptance Criteria

1. JMH benchmarks demonstrate static error lookup occurs in < 50ns with 0 bytes allocated per lookup.
2. Under sustained 2,500 errors/second load, p99 latency for error responses remains < 25ms.
3. Concurrent happy-path 2xx request latencies degrade by < 5% during an active error storm.
4. JVM heap stabilizes under error injection without continuous upward memory drift (zero memory leak).
5. Complete benchmark reports and profiling charts are documented and approved.
6. Validation commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :tools:benchmarks:jmh:run --no-daemon
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JMH benchmark execution output recording operations/sec and GC allocations.
- k6 performance summary report confirming p95/p99 latency thresholds under error storms.

## Rollout & Rollback Strategy

- Performance verification milestone.
- Zero production code impact.
- Rollback: If benchmarks fail budgets, block Phase 5 rollout and optimize hot paths under `ERRC-10`/`ERRC-15`.
