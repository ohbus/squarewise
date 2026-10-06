# BFF-09: Verify GraphQL transport error mapping for group operations

Exercise GraphQL queries and mutations through the transport layer, not only
controller unit calls. Verify upstream authorization, validation, timeout, and
malformed-response failures become stable GraphQL errors without leaking
internal messages, while successful updates emit exactly one invalidation.
Depends on BFF-07 and owns GraphQL schema/controller transport tests.
