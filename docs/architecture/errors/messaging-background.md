# Messaging and background failure contract

Status: contract-defined under `ERRC-08`; runtime adoption belongs to later
transport and bounded-context migration tasks.

## Immutable event and separate failure record

`contracts/events/envelope.schema.json` remains the business event contract.
`error-envelope.schema.json` stores failure metadata beside it and references the
same `eventId`, `eventType`, and `schemaVersion`; no failure handler may rewrite
the business payload, event type, aggregate identity, or event revision.

Terminal failures are copied to `dead-letter.schema.json`. Its
`messagePayloadBase64` is a bounded immutable snapshot for diagnosis/replay, not
a replacement event. Credentials, tokens, recipient addresses, provider text,
stack traces, and unbounded exception messages must not enter a failure record.

## Retry and terminal states

Transient failures use at most three retries after the initial delivery attempt,
with bounded exponential backoff and jitter (for example 1s, 2s, and 4s within
the configured maximum). `attemptCount` therefore cannot exceed four. A fourth
failed attempt becomes `DEAD_LETTERED`; poison failures (invalid JSON, unknown
schema version, invalid signature, or impossible envelope metadata) skip retry
and dead-letter immediately. Duplicate deliveries are acknowledged only after
the consumer-local idempotency decision succeeds.

Schedulers use the same explicit outcomes: `COMPLETED`, `FAILED_RETRYABLE`, and
`FAILED_TERMINAL`. A failed item is isolated from the batch; worker cancellation,
out-of-memory, linkage failures, and thread death are rethrown to the process
fatal policy and are never converted into an application failure record.

Startup failures are fatal and use the catalogued static identities for missing
configuration, migration failure, and broker/database availability. They do not
publish a partially initialized business event.
