# BFF-07: Complete bounded GraphQL group-member resolution

Resolve members consistently for single and list group queries without masking
authorization/upstream errors. Add bounded batching/fanout, gateway HTTP tests,
update mutation/invalidation tests, and GraphQL transport error coverage. Depends
on BFF-06 and CORE-17. Owns BFF group gateway/controller/tests and GraphQL schema.

## Implementation notes

`listGroups` now enriches each group through bounded sequential fanout with a
maximum concurrency of four. Member-fetch failures propagate to the GraphQL
caller, while the single-group path uses the same strict behavior.
