# Error-path performance budget evidence

## Reproducible workloads

The JMH module under `tools/benchmarks/jmh/` measures six-digit decomposition,
compiled catalog lookup, additive Problem Details serialization, and governed
versus ordinary exception creation:

```powershell
./gradlew.bat :tools:benchmarks:jmh:run --no-daemon
```

The k6 workload in `tests/performance/k6/error_storm_test.js` models 2,500
contained error responses per second alongside 2,500 authenticated successful
operations per second, while sampling JVM heap usage and process CPU through
the Prometheus actuator endpoint once per second. It defaults to ten minutes
and requires an explicit signed `BEARER_TOKEN`.

## Budgets

| Measurement | Target | Evidence status |
| --- | ---: | --- |
| Static lookup | <50 ns/op, 0 B/op | Must be populated from JMH output on the named JVM/host |
| Error response p99 | <25 ms at 2,500 req/s | Must be populated from k6 output on a production-like stack |
| Valid-operation p99 degradation | <5% during storm | Requires baseline and storm k6 runs on the same environment |
| Heap/GC stability | No continuous growth | Requires JVM GC profiler and service telemetry during the soak |

## 2026-10-07 local measurement

The JMH module compiled and ran on JDK 25.0.4.1. The full configured run
reported decomposition at 7.639 ns/op, while the optimized indexed catalog
lookup measured 2.427 ns/op in a focused two-iteration follow-up. Governed
exception creation measured 1,207.547 ns/op, and standard exception creation
at 1,041.401 ns/op. The indexed lookup meets the <50 ns budget, while the
governed exception is not cheaper than ordinary stack capture. A focused
`-prof gc` run measured indexed lookup at 2.389 +/- 0.588 ns/op, with zero
observed GC events and allocation below the profiler resolution (`about
10^-6 B/op`); this is consistent with, but does not mathematically prove, the
0 B/op budget. After adding
the Java-time Jackson module, a standalone two-iteration serialization run
reported 0.843 us/op, below its 5 us technical target.

A one-second-per-scenario k6 wiring smoke attempted the 2,500 error/s plus
2,500 valid-operations/s schedule, but the single-host local stack refused
connections under the burst: 864 iterations were dropped and all k6 thresholds
were crossed. This is evidence of the local environment ceiling, not a valid
10-minute capacity result.

## Evidence boundary

The repository now contains runnable benchmark and load-test definitions, but
the acceptance thresholds are environment-dependent measurements. A local
single-host Compose run is useful for wiring and regression evidence only; it
does not prove 10M+-DAU production capacity. Record CPU, RAM, JVM flags,
container limits, JMH JSON output, k6 summary, GC, heap, database, and broker
telemetry before treating this task as complete.
