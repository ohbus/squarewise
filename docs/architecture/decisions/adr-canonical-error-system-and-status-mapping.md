# Architectural Decision: Canonical Pre-Launch Error System Architecture

**Status**: Accepted & In Progress  
**Context**: Pre-launch Greenfield Unification (No production clients live)  
**Branch**: `refactor/remove-legacy-error-codes-and-use-422`  
**Supercedes**: Transitional migration shims and legacy code preservation in `ERRC-01` / `ERRC-30`

---

## 1. Context & Motivation

Squarewise originally specified an additive Strangler Fig migration (`ERRC-01` through `ERRC-31`) to preserve backward compatibility for hypothetical live v1 clients using flat legacy codes (`ERR-01` through `ERR-12`, and transitional strings like `VALIDATION_FAILED`, `NOT_FOUND`).

Because the application is **not yet in production**, retaining legacy mapping tables, dual representation shims, and transitional backward-compatibility fields imposes unnecessary technical debt, cognitive overhead, and runtime indirection.

This architecture decision establishes the definitive pre-launch error handling standard:
1. Complete removal of `legacyCode` from definitions, schemas, catalogs, tools, and response bodies.
2. Repurposing of top-level `code` to represent the high-level **Error Category / Family** for fast client-side switching.
3. Addition of `messageKey` in Problem Details responses for seamless frontend internationalization (i18n).
4. Richer parameter-level field violations including `messageKey` and `rejectedValue`.
5. Strict adherence to RFC HTTP status code semantics across the 400 series:
   - **HTTP 400 (Bad Request)**: Reserved strictly for malformed syntax / unparseable request bytes.
   - **HTTP 422 (Unprocessable Content)**: Heavily leveraged for syntax-valid payloads failing bean validation, field bounds, or domain business rules.
   - **HTTP 410 (Gone)**: Expired cursors and tokens.
   - **HTTP 409 (Conflict)**: Concurrency, state collisions, and lifecycle incompatibilities.
6. **Zero reflection and ultra-fast propagation** across the hot path ($O(1)$ identity checking, zero runtime YAML parsing, compile-time static singletons).

---

## 2. HTTP Status Code Hierarchy (400 Series)

| HTTP Status | Semantics | Emitted Errors / Triggers |
|---|---|---|
| **400 Bad Request** | **Strictly syntax / transport-level unparseable requests** | `REQUEST_BODY_MALFORMED` (invalid JSON syntax, unreadable byte stream), `GRAPHQL_OPERATION_INVALID` (malformed query syntax). |
| **401 Unauthorized** | Missing, malformed, expired, or rejected authentication credentials | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS`, `TOKEN_REVOKED`, `SESSION_EXPIRED`. |
| **403 Forbidden** | Authenticated principal lacks authorization for target resource or security policy rejection | `ACCESS_DENIED`, `GROUP_ACCESS_DENIED`, `CSRF_REJECTED`, `ORIGIN_REJECTED`. |
| **404 Not Found** | Target resource does not exist (or obscured for anti-enumeration) | `GROUP_NOT_FOUND`, `EXPENSE_NOT_FOUND`, `SETTLEMENT_NOT_FOUND`, `PROFILE_NOT_FOUND`, `RESOURCE_NOT_FOUND`. |
| **405 Method Not Allowed** | HTTP method unsupported for endpoint (includes `Allow` header) | `METHOD_NOT_ALLOWED`. |
| **406 Not Acceptable** | Server cannot satisfy `Accept` header representation | `REPRESENTATION_NOT_ACCEPTABLE`. |
| **409 Conflict** | State collision, concurrent modification, or lifecycle conflict | `GROUP_NAME_CONFLICT`, `EXPENSE_VERSION_CONFLICT`, `SETTLEMENT_VERSION_CONFLICT`, `EXPENSE_IDEMPOTENCY_CONFLICT`, `DUPLICATE_EXPENSE_REJECTED`, `GROUP_ARCHIVED`, `SETTLEMENT_ALREADY_REVERSED`. |
| **410 Gone** | Resource, token, or sync cursor once existed but has permanently expired | `SYNC_CURSOR_EXPIRED`, `INVITATION_EXPIRED`. |
| **413 Content Too Large** | Request body exceeds maximum allowed size | `REQUEST_BODY_TOO_LARGE`. |
| **415 Unsupported Media Type** | `Content-Type` header missing or unsupported | `MEDIA_TYPE_UNSUPPORTED`. |
| **422 Unprocessable Content** | Request syntax is valid, but payload fails semantic constraints, Bean Validation, or business rules | `REQUEST_VALIDATION_FAILED`, `REQUEST_VALUE_INVALID`, `PROFILE_REQUEST_INVALID`, `TIMEZONE_INVALID`, `GROUP_REQUEST_INVALID`, `EXPENSE_REQUEST_INVALID`, `ALLOCATION_SUM_MISMATCH`, `PARTICIPANT_SET_INVALID`, `INSUFFICIENT_BALANCE`, `SEARCH_QUERY_INVALID`, `EXPORT_REQUEST_INVALID`, `INBOX_LIMIT_OUT_OF_RANGE`, `NOTIFICATION_PREFERENCE_INVALID`. |
| **429 Too Many Requests** | Rate limit or admission quota exceeded (includes `Retry-After: 60`) | `SECURITY_RATE_LIMITED`, `LOGIN_RATE_LIMITED`, `REFRESH_RATE_LIMITED`, `SUBSCRIPTION_LIMIT_EXCEEDED`. |

---

## 3. Response Schema & Taxonomy

### RFC 9457 Problem Details Shape
```json
{
  "type": "https://squarewise.example/problems/allocation_sum_mismatch",
  "title": "Allocation sum mismatch",
  "status": 422,
  "code": "BUSINESS_RULE_VIOLATION",
  "numericCode": "233501",
  "errorName": "ALLOCATION_SUM_MISMATCH",
  "messageKey": "error.expense.allocation_sum_mismatch",
  "detail": "The sum of participant allocations does not match the total expense amount.",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "source": "squarewise-expense-core",
  "timestamp": "2026-10-08T14:00:00Z",
  "violations": []
}
```

### Form Validation Response (422)
```json
{
  "type": "https://squarewise.example/problems/request_validation_failed",
  "title": "Validation failed",
  "status": 422,
  "code": "VALIDATION_ERROR",
  "numericCode": "911101",
  "errorName": "REQUEST_VALIDATION_FAILED",
  "messageKey": "error.request.validation_failed",
  "detail": "One or more request parameters failed validation.",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "source": "squarewise-accounts",
  "timestamp": "2026-10-08T14:00:00Z",
  "violations": [
    {
      "field": "totalAmountMinor",
      "message": "must be greater than 0",
      "messageKey": "validation.positive",
      "rejectedValue": -50
    }
  ]
}
```

### Purpose of Each Field
1. `status` (Integer): Standard HTTP status code.
2. `code` (Enum String): Broad Error Family / Category for high-level client switching (`VALIDATION_ERROR`, `BUSINESS_RULE_VIOLATION`, `NOT_FOUND`, `STATE_CONFLICT`, `RESOURCE_GONE`, `AUTHENTICATION_ERROR`, `AUTHORIZATION_ERROR`, `RATE_LIMIT_EXCEEDED`, `MALFORMED_REQUEST`, `INTERNAL_ERROR`).
3. `numericCode` (6-digit String): Canonical machine code (`DMLCEE`) for monitoring, alerting, telemetry, and exact machine lookup.
4. `errorName` (String): Immutable SCREAMING_SNAKE_CASE leaf error identifier.
5. `messageKey` (String): Standard hierarchical translation key for frontend internationalization (e.g., `i18n.t(res.messageKey)`).
6. `detail` (String): Default sanitized, human-readable English message.
7. `violations` (Array): Field-level errors with field path, message, `messageKey`, and sanitized `rejectedValue`.

---

## 4. Zero-Reflection & High-Performance Implementation Design

1. **Static Pre-Compiled Singletons**: All error definitions are compiled objects (`SimpleErrorDefinition`). No runtime YAML parsing, reflection, or classpath scanning.
2. **$O(1)$ Identity Verification**: Exception construction validates definition membership using an immutable hash set rather than iterating through lists.
3. **Embedded Defaults + Injectable Overrides**: Custom exception classes embed sensible catalog defaults while accepting optional definition overrides:
   - `DomainValidationException(violations, definition = PlatformErrors.REQUEST_VALIDATION_FAILED)`
   - `EntityNotFoundException(resource, definition = PlatformErrors.RESOURCE_NOT_FOUND)`
   - `ConcurrencyConflictException(definition = PlatformErrors.RESOURCE_CONFLICT)`
   - `BusinessRuleViolationException(definition)`

---

## 5. Migration Execution Checklist

- [ ] **Phase 1: Contracts & Schema**:
  - Update `contracts/errors/error-catalog.schema.json` and `problem.schema.json`.
  - Update `contracts/errors/error-catalog.yaml`: drop `legacyCode`, update HTTP statuses to 422/410, assign category codes.
  - Update OpenAPI specs (`contracts/rest/*.openapi.json`): add 422 responses, reserve 400 for malformed syntax.
- [ ] **Phase 2: Core Errors Library**:
  - Update `ErrorDefinition.kt`, `SimpleErrorDefinition.kt`, `ProblemDetailsDto.kt`, `ViolationDto.kt`.
  - Update `ErrorCatalog.kt`, `SquarewiseException.kt`, and custom exceptions.
  - Update `GlobalErrorAdvice.kt` and `GlobalErrorHandler.kt`.
  - Regenerate static catalogs via `generate_error_catalogs.py`.
- [ ] **Phase 3: Applications & Handlers**:
  - Update controllers, validation error handling, and security error filters.
- [ ] **Phase 4: Tests & Verification**:
  - Update test assertions from 400 to 422.
  - Run `make contracts`, `make python-typecheck`, and `./gradlew test`.
