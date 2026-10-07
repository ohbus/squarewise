# Error ownership guides

Status: authoritative error inventory under `ERRC-04`; reconciled with `contracts/errors/error-catalog.yaml`.

These guides turn the canonical standard into bounded-context ownership decisions.
The codes defined herein are allocated in the authoritative machine-readable catalog
`contracts/errors/error-catalog.yaml` and govern runtime error definitions across all domains.

| Guide | Domain | Primary concerns |
|---|:---:|---|
| [Accounts](accounts.md) | `1` | Profile, authentication, sessions, lifecycle, identity |
| [Expense Core](expense-core.md) | `2` | Groups, membership, expenses, settlements, recurrence, sync, outbox |
| [Notifications](notifications.md) | `3` | Inbox, delivery, preferences, email |
| [BFF](bff.md) | `4` | GraphQL, upstream transport, live updates |
| [Platform libraries](platform-libraries.md) | `9` | Error framework, security, persistence, messaging, observability, IDs |

Every guide records current compatibility status, candidate semantic families,
boundary translation, and required tests. The eventual catalog task must reconcile
the guides with every source throw, framework callback, contract response, event
consumer, scheduler, and startup path. A candidate is removed or split when client
remediation, disclosure, side effects, retry, or ownership differ.

## Shared review checklist

- Does the condition have distinct caller/operator remediation?
- Is the domain/module the semantic owner rather than the current process?
- Are anti-enumeration and authorization disclosures explicit?
- Is status/classification explicit and v1-compatible?
- Is the safe detail catalog-controlled and localization-ready?
- Are retry, idempotency, partial side effects, and required headers defined?
- Can structured diagnostics remain bounded and non-sensitive?
- Is a leaf exception genuinely useful, or is a governed base/typed outcome simpler?
- Does a reusable exception constrain its override to one compatible family?
- Are REST, GraphQL, WebSocket, message, scheduled, startup, and internal transports
  marked accurately?
- Are redaction, concurrency, cancellation, fatality, and failure-path tests named?
