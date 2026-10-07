# Accounts error ownership guide

Status: allocated and authoritative under `ERRC-04`; matches `contracts/errors/error-catalog.yaml`.

Accounts owns Domains `11`-`15`. Existing v1 symbolic codes and statuses remain until
an approved contract change. Profile-existence hiding and identity disclosure require
security review before changing 401/404 behavior.

## Allocated public families

| Candidate | Name | Current-compatible HTTP | Legacy `code` | Required distinction |
|---|---|:---:|---|---|
| `117101` | `PROFILE_SUBJECT_INVALID` | 401 | `UNAUTHENTICATED` | Missing/malformed authenticated subject |
| `111101` | `PROFILE_REQUEST_INVALID` | 400 | `VALIDATION_FAILED` | Bounded field validation |
| `111102` | `TIMEZONE_INVALID` | 400 | `VALIDATION_FAILED` | Unsupported or malformed time zone |
| `117201` | `AUTHENTICATED_PROFILE_NOT_FOUND` | 404 | `NOT_FOUND` | Anti-enumeration for absent authenticated profiles |
| `117202` | `FOREIGN_PROFILE_ACCESS_DENIED` | 403 | `FORBIDDEN` | Object-level authorization without hiding |
| `117203` | `BATCH_LOOKUP_UNAUTHORIZED` | 403 | `FORBIDDEN` | Internal workload authority missing |
| `127101` | `LOGIN_CODE_INVALID` | 401 | `UNAUTHENTICATED` | Wrong, malformed, expired, or consumed code may split after threat review |
| `127801` | `LOGIN_RATE_LIMITED` | 429 | `RATE_LIMITED` | Admission rejection with retry guidance |
| `127802` | `LOGIN_LIMITER_UNAVAILABLE` | 429 initially | `RATE_LIMITED` | Operationally distinct store outage; later 503 needs contract review |
| `137101` | `REFRESH_TOKEN_INVALID` | 401 | `UNAUTHENTICATED` | Malformed/signature/claims invalid; security diagnostics remain internal |
| `137401` | `SESSION_EXPIRED` | 401 | `UNAUTHENTICATED` | Reauthentication remediation |
| `137402` | `SESSION_REVOKED` | 401 | `UNAUTHENTICATED` | Revoked family/device/session |
| `137403` | `REFRESH_REPLAY_DETECTED` | 401 | `UNAUTHENTICATED` | Replay response and family revocation |
| `137404` | `SESSION_SUBJECT_MISMATCH` | 401 | `UNAUTHENTICATED` | Token/profile subject mismatch |
| `137801` | `REFRESH_RATE_LIMITED` | 429 | `RATE_LIMITED` | Refresh admission rejection |
| `147401` | `ACCOUNT_DELETION_ALREADY_REQUESTED` | 409 | `CONFLICT` | Lifecycle idempotency/state |
| `147402` | `ACCOUNT_EXPORT_ALREADY_PENDING` | 409 | `CONFLICT` | Export workflow state |
| `157301` | `IDENTITY_ALREADY_LINKED` | 409 | `CONFLICT` | Issuer/subject uniqueness |

The catalog-freeze task must decide whether invalid login-code subconditions are
publicly distinct or intentionally collapsed to prevent oracle behavior. Distinct
internal definitions may share the same public contract only when operators require
separate remediation and disclosure remains safe.

## Non-HTTP and infrastructure families

Account email enqueue/publish failure, credential-provider timeout, identity-store
unavailability, token-signing/key rotation failure, audit/outbox failure, configuration
failure, and scheduled deletion/export retry exhaustion require registered internal or
async definitions. Platform failures are preserved unless Accounts changes the
semantic contract, for example translating an email transport outage into
`LOGIN_DELIVERY_TEMPORARILY_UNAVAILABLE`.

## Exception and boundary rules

- Authentication filter failures are written by the security adapter, not controller
  advice, and preserve `WWW-Authenticate` where required.
- Token library exceptions never cross the security boundary and never expose claim,
  issuer, key, or parser text.
- `LoginCodeInvalidException` may bake one public definition while using internal
  bounded reason diagnostics; callers may not inject an arbitrary definition.
- Rate exhaustion and rate-store outage are different definitions even during the
  period when both map to HTTP 429.
- Database uniqueness is translated only from a known named constraint; unknown data
  integrity failures remain persistence/internal errors.

## Required tests

Cover missing/malformed/expired/revoked/replayed tokens, issuer/audience/subject
mismatch, key-provider failure, filter-chain 401/403, CSRF/origin rejection, login and
refresh quotas, limiter outage, concurrent refresh rotation, profile disclosure,
duplicate identity, invalid locale/time zone, deletion/export idempotency, mail/outbox
failure, redaction, required headers, and stable v1 envelope fields.
