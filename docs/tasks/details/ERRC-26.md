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
make load-k6-validate
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JMH benchmark execution output recording operations/sec and GC allocations.
- k6 performance summary report confirming p95/p99 latency thresholds under error storms.

## Implementation Notes and Evidence

- Added the `tools:benchmarks:jmh` module with three JMH benchmark classes and a `run` compatibility task matching the declared validation command.
- Added `tests/performance/k6/error_storm_test.js` with explicit baseline and
  storm modes. The storm mode supports a `BASELINE_VALID_P99_MS` input and
  enforces the valid-operation p99 at no more than 105% of that baseline, in
  addition to the error p99 and dropped-iteration thresholds. Storm mode fails
  setup without a positive baseline, preventing an absolute threshold from being
  misreported as degradation evidence. Both modes require a bearer token and
  sample JVM heap, process CPU, aggregate GC pause seconds, and GC collection
  count from the authenticated Prometheus actuator endpoint once per second,
  using the same setup token as the valid-operation scenario.
  Injected 4xx responses are marked as expected k6 statuses so the error-rate
  threshold detects unexpected failures rather than the intended error workload.
  The exported k6 summary explicitly includes p95 and p99 statistics for the
  required latency evidence.
- Added an immutable startup-built `ErrorCatalog.byNumericCode` index and parity coverage. The current-head full JMH run on JDK 25.0.4.1 measured indexed lookup at 2.432 +/- 2.025 ns/op, decomposition at 7.499 +/- 1.370 ns/op, serialization at 0.878 +/- 0.275 us/op, governed exception creation at 1,187.047 +/- 163.300 ns/op, and standard exception creation at 1,044.582 +/- 116.176 ns/op.
- The one-second k6 wiring smoke crossed thresholds with 864 dropped iterations and connection refusals from the single-host local stack. No 10-minute, production-like regional capacity evidence exists yet.
- After authenticating actuator telemetry and classifying intended 4xx responses as
  expected, a one-second local baseline/storm wiring smoke at one error and one
  valid operation per second passed all four checks with zero dropped iterations
  and zero unexpected HTTP failures. This validates harness wiring only; it is
  not production-like capacity evidence.
- A fresh one-second target-rate smoke at 2,500 errors/s plus 2,500 valid
  operations/s dropped 3,078 iterations, crossed all performance thresholds,
  and recorded 5.57% unexpected HTTP failures. The observed p99 values were
  4.50 s for error responses and 4.56 s for valid operations; telemetry recorded
  about 24 GC collections and 0.494 s cumulative pause time. This confirms the
  local single-host ceiling and is not production-like acceptance evidence.
- A focused JMH run with `-prof gc` measured indexed lookup at 2.389 +/- 0.588 ns/op,
  zero observed GC events, and allocation below the profiler resolution (`about
  10^-6 B/op`). This is consistent with the 0 B/op budget but is not an exact
  zero-allocation proof because JMH reports a rounded lower bound.
- `mingw32-make load-k6-validate` passed with eight valid scripts, and `k6 inspect`
  confirmed the ten-minute baseline/storm scenario definitions, 2,500 error and
  valid-operation rates, p99 thresholds, telemetry sampling, dropped-iteration
  gate, and p95/p99 summary statistics. This validates harness configuration only.
- A fresh local authenticated one-second baseline at one error and one
  valid-operation per second passed all three checks with zero dropped
  iterations and zero unexpected HTTP failures. The documented hostname-preserving
  Keycloak token path was required; a token minted against `localhost` was
  rejected by the services' configured internal issuer. This is local fixture and
  wiring evidence only.
- A fresh local authenticated one-second storm at one error and one
  valid-operation per second passed all five checks with zero dropped iterations,
  zero unexpected HTTP failures, error p99 2.23 ms, valid-operation p99 13.52 ms,
  and telemetry showing 45 cumulative JVM collections with 0.663 seconds of
  cumulative pause at the scrape point. The valid-operation degradation gate used
  the preceding baseline p99 of 14.3 ms. This is low-rate local smoke evidence,
  not production-like capacity or soak evidence.
- ERRC-26 remains in progress until a production-like k6/GC/heap run produces the
  required p99, valid-operation degradation, and stability evidence.

## Rollout & Rollback Strategy

- Performance verification milestone.
- Zero production code impact.
- Rollback: If benchmarks fail budgets, block Phase 5 rollout and optimize hot paths under `ERRC-10`/`ERRC-15`.
