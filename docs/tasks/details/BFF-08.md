# BFF-08: Test bounded group-member fanout behavior

## Objective

Add gateway HTTP tests for zero, one, and many groups; verify at most four member requests are concurrent (as bounded by `flatMapSequential(..., 4)`), list order matches the group response, and no duplicate member request occurs. Cover timeout, 404, 401/403, 5xx, malformed member payloads, and single group resolution.

## Acceptance Criteria

- Zero, one, and many (10) groups member resolution covered with HTTP doubles.
- Concurrency bound of at most 4 concurrent member requests verified via atomic active tracking under artificial delay.
- Order of groups from upstream response is strictly preserved in the returned list.
- Request count matches exactly 1 member request per group (no redundant calls).
- Timeout, 404, 401, 403, and 503 upstream errors propagate through `UpstreamServiceException`.
- Malformed member payload decoding failure is handled gracefully.
- Single group resolution (`getGroup`) and 404 propagation verified.
- Bearer authorization headers are forwarded cleanly.
- Structured KDoc doc comments (`/** ... */`) document test intent, invariants, and edge cases.

## Validation Commands

- `./gradlew :app:bff:test --rerun-tasks --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Evidence

- Added `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/BffFanoutTest.kt` covering all fanout, error, ordering, and concurrency bound scenarios using OkHttp `MockWebServer`.
- `./gradlew :app:bff:test --rerun-tasks --no-daemon` completed successfully on 2026-09-18.
- `git diff --check` passed cleanly.
