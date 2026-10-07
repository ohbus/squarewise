# Error-path performance budget evidence

## Reproducible workloads

The JMH module under `tools/benchmarks/jmh/` measures six-digit decomposition,
compiled catalog lookup, additive Problem Details serialization, and governed
versus ordinary exception creation:

```powershell
./gradlew.bat :tools:benchmarks:jmh:run --no-daemon
```

Run the k6 scenarios through the repository Docker wrapper:

```powershell
make load-k6-error-storm LOAD_MODE=baseline DURATION=10m
make load-k6-error-storm LOAD_MODE=storm BASELINE_VALID_P99_MS=<baseline-p99-ms> DURATION=10m
```

The k6 workload in `tests/performance/k6/error_storm_test.js` supports a
`LOAD_MODE=baseline` run for valid-operation p99 measurement and a
`LOAD_MODE=storm` run that models 2,500 contained error responses per second
alongside 2,500 authenticated successful operations per second. The storm run
requires `BASELINE_VALID_P99_MS` and sets the valid-operation threshold to 105%
of that baseline. Storm mode fails setup without a positive baseline, so an
absolute threshold cannot be mistaken for degradation evidence. Both modes
sample JVM heap usage, process CPU, aggregate GC pause seconds, and GC
collection count through the authenticated Prometheus actuator endpoint once per second,
default to ten minutes, and require an explicit signed `BEARER_TOKEN`.
Expected contained 4xx responses are configured as expected k6 statuses, so the
storm failure-rate threshold measures transport/unexpected-response failures
instead of counting the injected error responses themselves.
The harness also configures p95 and p99 in the exported k6 summary so the
required percentile evidence is not dependent on k6's default summary set.

## Budgets

| Measurement | Target | Evidence status |
| --- | ---: | --- |
| Static lookup | <50 ns/op, 0 B/op | Must be populated from JMH output on the named JVM/host |
| Error response p99 | <25 ms at 2,500 req/s | Must be populated from k6 output on a production-like stack |
| Valid-operation p99 degradation | <5% during storm | Requires baseline and storm k6 runs on the same environment |
| Heap/GC stability | No continuous growth | Requires JVM GC profiler plus k6 heap, GC pause, and collection telemetry during the soak |

## 2026-10-07 local measurement

The JMH module compiled and ran on JDK 25.0.4.1. The current-head full configured
run reported indexed catalog lookup at 2.432 +/- 2.025 ns/op, decomposition at
7.499 +/- 1.370 ns/op, governed exception creation at 1,187.047 +/- 163.300
ns/op, standard exception creation at 1,044.582 +/- 116.176 ns/op, and
serialization at 0.878 +/- 0.275 us/op. The indexed lookup meets the <50 ns
budget, while the governed exception is not cheaper than ordinary stack
capture. A focused
`-prof gc` run measured indexed lookup at 2.389 +/- 0.588 ns/op, with zero
observed GC events and allocation below the profiler resolution (`about
10^-6 B/op`); this is consistent with, but does not mathematically prove, the
0 B/op budget. After adding
the Java-time Jackson module, a standalone two-iteration serialization run
reported 0.843 us/op, below its 5 us technical target; the fresh full run
reproduced that result at 0.846 us/op.

A one-second-per-scenario k6 target-rate smoke attempted the 2,500 error/s plus
2,500 valid-operations/s schedule after the harness fixes. The single-host
local stack dropped 3,078 iterations, recorded 5.57% unexpected HTTP failures,
and measured p99 values of 4.50 s for error responses and 4.56 s for valid
operations; the telemetry samples included about 24 GC collections and 0.494 s
cumulative GC pause time. This is evidence of the local environment ceiling,
not a valid 10-minute capacity result.

A fresh authenticated one-second baseline followed by a one-second storm at one
error/s and one valid-operation/s passed the harness checks with zero dropped
iterations and zero unexpected HTTP failures. Baseline valid-operation p99 was
14.3 ms; storm error p99 was 2.23 ms and valid-operation p99 was 13.52 ms. The
storm telemetry sample reported 45 JVM collections and 0.663 seconds cumulative
pause at the scrape point. The run required the documented internal-issuer
Keycloak connection and local profile enrollment. This is wiring and low-rate
local smoke evidence only, not production-like capacity or soak evidence.

## Evidence boundary

The repository now contains runnable benchmark and load-test definitions, but
the acceptance thresholds are environment-dependent measurements. A local
single-host Compose run is useful for wiring and regression evidence only; it
does not prove 10M+-DAU production capacity. Record CPU, RAM, JVM flags,
container limits, JMH JSON output, k6 summary, GC, heap, database, and broker
telemetry before treating this task as complete.
