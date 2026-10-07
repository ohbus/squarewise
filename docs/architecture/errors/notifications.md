# Notifications error ownership guide

Status: allocated and authoritative under `ERRC-04`; matches `contracts/errors/error-catalog.yaml`.

Notifications owns Domains `31`-`34`. Delivery failures are primarily asynchronous;
they must not invent HTTP statuses or overwrite the originating business event.

## Allocated public families

| Candidate | Name | Current-compatible HTTP | Legacy `code` | Required distinction |
|---|---|:---:|---|---|
| `311101` | `INBOX_CURSOR_INVALID` | 400 | `VALIDATION_FAILED` | Cursor decode/version failure |
| `311102` | `INBOX_LIMIT_OUT_OF_RANGE` | 400 | `VALIDATION_FAILED` | Bounded pagination |
| `317201` | `INBOX_NOTIFICATION_NOT_FOUND` | 404 | `NOT_FOUND` | Missing or caller-hidden notification |
| `317202` | `INBOX_ACCESS_HIDDEN` | 404 | `NOT_FOUND` | Cross-profile anti-enumeration |
| `331101` | `NOTIFICATION_PREFERENCE_INVALID` | 400 | `VALIDATION_FAILED` | Unsupported channel/frequency/locale |
| `341101` | `EMAIL_RECIPIENT_REQUIRED` | 400 | `VALIDATION_FAILED` | Existing request validation |
| `341102` | `EMAIL_TEMPLATE_INPUT_INVALID` | 400 | `VALIDATION_FAILED` | Missing/invalid bounded template data |

## Asynchronous delivery families

Allocate no-HTTP definitions for invalid envelope, unsupported event version,
deserialization failure, duplicate delivery, preference suppression, destination
missing, template missing/render failure, provider rejection, provider throttling,
provider timeout/unavailability, SMTP/TLS/authentication failure, retry exhaustion,
dead-letter/parking failure, acknowledgement failure, and worker shutdown/cancellation.

Provider diagnostic codes may be bounded internal fields; provider response text and
recipient addresses never become public detail or metric labels. A deliberate user
preference suppression is an expected terminal outcome, not an infrastructure error.

## Consumer and exception rules

- Parse and validate envelopes before dispatch; distinguish poison from transient
  failure without matching exception messages.
- Fatal JVM failures and cancellation are rethrown and never acknowledged.
- Duplicate/idempotent delivery records are explicit successful terminal outcomes.
- Retry decisions come from the registered policy, are bounded/jittered, and preserve
  delivery idempotency.
- A channel exception may constrain overrides to one delivery family; it cannot accept
  an Accounts, Expense, or arbitrary platform definition.
- `MailException`/`MailSendException` are translated at the email adapter and never
  leak provider/Jakarta Mail text.

## Required tests

Cover inbox validation and authorization, malformed/unsupported envelopes, duplicate
delivery, preferences/opt-out, missing destination/template, template injection and
render failure, provider reject/throttle/timeout/TLS/auth/outage, retry timing and
exhaustion, parking and ack failure, redelivery after crash, graceful cancellation,
fatal propagation, personal-data redaction, and stable REST envelope behavior.
