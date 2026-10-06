# ERRC-01B: Audit error contracts and compatibility surface

Read the current error catalog and schema, all REST OpenAPI documents, GraphQL
schema and mapping documentation, event contracts, API status, error operations
guides, test matrices, Bruno assertions, and error validators. Report findings
to the coordinator; make no files or contract changes.

The audit must identify every public compatibility surface, HTTP-status
equivalence requirement, additive migration option, downstream consumer risk,
schema/versioning rule, test obligation, and rollback condition.

## Findings delivered

- Established that API v1 must retain the current symbolic `code`; the compatible
  addition is optional `numericCode` plus `errorName`. A numeric `code` is a future
  versioned breaking contract, and `legacyCode` would be redundant in v1.
- Found schema/runtime/document drift, duplicated reduced OpenAPI Problem models,
  bodyless security responses, incomplete Expense Core non-2xx coverage, weak Bruno
  assertions, and a validator that proves parsing rather than full semantic parity.
- Found that the BFF discards upstream code/source/request identity and reconstructs
  errors from status, while event failures need attempt/retry/parking metadata rather
  than an HTTP envelope.
- Required explicit per-error status, alias, classification, retry, headers,
  disclosure, lifecycle, transport, owner, and runbook metadata; category-derived
  mappings are rejected.
- Defined consumer-first additive rollout, mixed-version evidence, compatibility
  window, canary/rollback, OpenAPI dereference/parity, source/catalog parity, and
  breaking-history gates.

This was a read-only audit. The coordinator incorporated the findings into commit
`33e5cea`; no public contract was changed by this child task.
