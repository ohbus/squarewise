# Platform and shared-library error ownership guide

Status: allocated and authoritative under `ERRC-04`; matches `contracts/errors/error-catalog.yaml`.

Domain `9` owns failures whose semantic meaning is genuinely cross-cutting. A shared
JAR that implements Accounts, Expense, or Notifications behavior keeps that business
namespace; being a library does not make an error Platform-owned.

## Allocated platform families

| Candidate | Name | Default HTTP when applicable | Purpose |
|---|---|:---:|---|
| `911101` | `REQUEST_VALIDATION_FAILED` | 400 | Framework validation fallback |
| `911102` | `REQUEST_BODY_MALFORMED` | 400 | Safe parser-independent body failure |
| `911103` | `REQUEST_VALUE_INVALID` | 400 | Type/missing parameter fallback |
| `911104` | `REQUEST_BODY_TOO_LARGE` | 413 | Declared body bound |
| `911105` | `MEDIA_TYPE_UNSUPPORTED` | 415 | Unsupported request content type |
| `911106` | `METHOD_NOT_ALLOWED` | 405 | Preserve `Allow` |
| `911107` | `REPRESENTATION_NOT_ACCEPTABLE` | 406 | Negotiation failure policy |
| `919201` | `RESOURCE_NOT_FOUND` | 404 | Framework route/resource fallback only |
| `919301` | `RESOURCE_CONFLICT` | 409 | Framework conflict fallback only |
| `919901` | `UNEXPECTED_INTERNAL_ERROR` | 500 | Static final non-fatal catchall |
| `919902` | `RESPONSE_SERIALIZATION_FAILED` | 500 | Output boundary failure |
| `927101` | `AUTHENTICATION_REQUIRED` | 401 | Security fallback with challenge |
| `927501` | `ACCESS_DENIED` | 403 | Authenticated policy denial |
| `927502` | `CSRF_REJECTED` | 403 | Browser mutation security |
| `927503` | `ORIGIN_REJECTED` | 403 | Origin policy |
| `927801` | `SECURITY_RATE_LIMITED` | 429 | Shared security admission fallback |
| `934801` | `DATABASE_UNAVAILABLE` | 503 | Required database unavailable |
| `934802` | `DATABASE_OPERATION_TIMEOUT` | 503 | Persistence timeout when not domain-translated |
| `934601` | `DATABASE_DATA_INCONSISTENT` | 500 | Unknown integrity/corruption |
| `945701` | `MESSAGE_PUBLISH_FAILED` | - | Publish/nack/protocol failure |
| `945801` | `BROKER_UNAVAILABLE` | - | Broker/circuit/timeout availability |
| `945702` | `MESSAGE_ENVELOPE_INVALID` | - | Shared envelope protocol |
| `948901` | `PLATFORM_CONFIGURATION_INVALID` | - | Startup configuration failure |
| `958901` | `OBSERVABILITY_PIPELINE_FAILED` | - | Telemetry degradation; avoid recursive logging |
| `968901` | `IDENTIFIER_GENERATION_FAILED` | 500 | ID source invariant/availability |

Candidate digits are subject to catalog freeze. Async/startup-only failures have no
invented HTTP status.

## Library propagation and translation

- Preserve a platform definition if its meaning and remediation remain the same.
- Translate once when a bounded context promises a different semantic outcome; keep
  the platform exception as cause.
- Libraries expose typed failures or results through narrow public contracts. They do
  not throw arbitrary generic exceptions intentionally.
- Framework exception mapping uses direct tables/branches. Unknown dependency
  exceptions terminate at the nearest owning boundary as a static internal error.
- Runtime discovery by reflection, annotations, classpath scanning, or `ServiceLoader`
  is prohibited. Build-time generation and explicit manifests are allowed.

## Fatal and degraded infrastructure

Out-of-memory, VM, linkage, thread death, and cancellation are not catalog responses;
they follow the fatal policy and allow restart. Observability failure must not recurse
or block the primary response. Failure to obtain a request ID uses a bounded fallback
without exposing generator details. Database/broker/config failures log safe static
identity and never credentials, hosts, schema text, or secrets publicly.

## Required tests

Cover every Spring request exception, security entry point/access-denied path,
response serialization, async timeout, client disconnect, unknown non-fatal fallback,
fatal propagation, cancellation/interrupt, database/broker timeout and outage, message
envelope/version failure, startup configuration/schema/migration failure, telemetry
degradation, ID failure, reflection/static-analysis gates, redaction, bounded validation
violations, required headers, and zero leakage through container default error pages.
