# DOC-11: Document endpoint-specific pagination policy

Document endpoint-specific pagination policy across collection resources in Squarewise,
establishing bounded pagination limits, opaque cursor semantics, and point operation guidance.
Authoritative architecture specification is maintained in `docs/architecture/pagination.md`.

## Deliverables & Decisions

- Declared bounded pagination strategy for collection endpoints across Accounts, Expense Core, and Notifications.
- Specified cursor encoding, opaque offset/token structures, expiry handling, and maximum page bounds (e.g. 100 for search, 50 for inbox).
- Point operations (e.g. `GET /groups/{id}`, `GET /profiles/{id}`) remain unpaginated.

## Owned Paths

- `docs/architecture/pagination.md`
- `docs/tasks/details/DOC-11.md`

## Verification Evidence

- `python3 tools/contracts/validate.py` passed with valid pagination policy.
- `git diff --check` passed cleanly.
