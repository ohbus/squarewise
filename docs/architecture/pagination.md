# Pagination policy

Pagination is selected per endpoint based on growth and consistency needs.

| Endpoint shape | Strategy | Reason |
|---|---|---|
| Group list, inbox, expense list, search/export | Cursor with bounded `limit` | Results grow and must remain stable while writes occur |
| Recurring schedules and occurrence history | First page with bounded `limit` | Existing compatibility reads must not materialize unbounded history; cursors are the follow-up |
| Sync snapshot and change feed | Opaque cursor / revision token | Offline recovery requires resumable, gap-free ordering |
| Balances, profile, preferences, allocation preview, create/update/delete commands | No pagination | Each response is a bounded resource or a single command result |
| Invitations and settlements by ID | No pagination | Point operations addressed by an immutable identifier |

Cursor tokens are opaque, scoped to the authenticated subject and resource,
expire when the retention window requires, and return a next cursor plus a
bounded page. Page-number pagination is reserved for explicitly bounded,
non-mutating administrative views where random access is required; it is not
used for financial history or synchronization.

Every collection contract must declare its maximum page size, ordering key,
cursor semantics, authorization scope, and expired-cursor behavior before
implementation. A collection must never silently return an unbounded array.

The current group REST/BFF list compatibility shape accepts a bounded `limit` of
1 through 100. Full cursor connections remain required before raising that cap;
the BFF group detail response likewise keeps its documented 1,000-expense
look-ahead until the GraphQL contract exposes expense cursors.

Recurring schedule and occurrence reads currently accept a bounded `limit` from
1 through 100 and return only the first page. This is a compatibility cap;
cursor contracts are required before exposing deeper history.
