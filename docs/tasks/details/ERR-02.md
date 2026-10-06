# ERR-02: Typed shared error model

Refactor `libs/errors` to expose typed definitions and domain exceptions with
code, status, component, operation, retryability, severity, and safe metadata.
Generate a unique error ID per response, preserve causes for logs, and extend
the problem schema without returning sensitive values. Keep request-ID MDC
correlation and provide compatibility mapping during migration.

Validation: shared-library tests, contract validation, and diff checks.
