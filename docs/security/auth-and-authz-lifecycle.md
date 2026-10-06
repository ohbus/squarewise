# Authentication and authorization lifecycle architecture

## Purpose

This document provides a comprehensive, authoritative specification of the authentication and
authorization lifecycle for both unauthenticated and authenticated actors across Squarewise /
Squarewise. It details the complete state transitions, authorization matrix, sequence flows,
event emissions, and security guarantees across REST, GraphQL, WebSocket, and asynchronous worker
boundaries.

---

## 1. Actor Classification & Trust Boundary

Squarewise distinguishes four classes of callers:

1. **Anonymous / Unauthenticated Actors:** External callers without identity credentials. Access is
   restricted to explicitly allow-listed public ingress paths (`POST /accounts/v1/auth/login/start`,
   `POST /accounts/v1/auth/login/verify`, and liveness probes).
2. **Authenticated Users (Browser SPA/SSR):** Interactive users whose sessions are maintained via
   `Secure`, `HttpOnly`, `SameSite=Lax` cookies with strict double-submit CSRF tokens on mutations.
3. **Authenticated Users (Native App / External API):** Programmatic clients supplying an
   `Authorization: Bearer <JWT>` header with short-lived tokens and rotating refresh tokens.
4. **Internal Workload Identities:** Service-to-service internal calls carrying verified workload
   assertions (e.g. `X-Squarewise-Workload-Role`) over mTLS/private mesh.

---

## 2. Authentication & Authorization Lifecycle Matrix

The matrix below maps actor states to operations, required security policies, audit events, and
exact negative/error outcomes.

| Actor State | Operation / Endpoint | Authentication Check | Authorization / Policy | Security Audit Event Emitted | Failure Response & Status |
|---|---|---|---|---|---|
| **Unauthenticated** | `POST /auth/login/start` | Public boundary | Redis sliding-window rate limit (per IP & email); email canonicalization; anti-enumeration | `AUTH_LOGIN_REQUESTED` (on admission) | `429 Too Many Requests` (`RATE_LIMITED`, `Retry-After: 60`) or `400 Bad Request` |
| **Unauthenticated** | `POST /auth/login/verify` | Public boundary | Single-use credential validation against salted SHA-256 hash; attempt counter `< 5`; expiry `< 10m` | `AUTH_LOGIN_FAILED` (on invalid) or `AUTH_LOGIN_SUCCESS` (on success) | `401 Unauthorized` (`INVALID_CREDENTIAL`) or `429 Too Many Requests` |
| **Unauthenticated** | `POST /graphql` | None provided | Denied by security filter | `AUTH_CREDENTIAL_MISSING` | `401 Unauthorized` / GraphQL Problem Error |
| **Unauthenticated** | `GET /v1/expenses` | None provided | Denied at REST resource-server filter | None | `401 Unauthorized` (`WWW-Authenticate: Bearer error="unauthorized"`) |
| **Unauthenticated** | `WS /subscriptions` | Missing connection token | Handshake rejected at WebSocket gateway | `AUTH_HANDSHAKE_REJECTED` | WebSocket close frame `4401 Unauthorized` |
| **Authenticated** | `POST /auth/token/refresh` | Opaque refresh token | Token family lookup; reuse/replay detection; Redis refresh rate limit | `AUTH_TOKEN_ROTATED` (success) or `AUTH_TOKEN_REPLAY_DETECTED` (compromise) | `401 Unauthorized` (triggers immediate family-wide revocation on reuse) |
| **Authenticated** | `POST /auth/logout` | Active session cookie or Bearer | Double-submit CSRF match (`Origin` + `X-CSRF-Token`); revoke token family in PostgreSQL | `AUTH_SESSION_REVOKED` | `204 No Content` (idempotent, cookies cleared) |
| **Authenticated** | `GET /accounts/v1/accounts/me` | Valid Bearer JWT | Subject match (`sub == account.id` or matching provider link) | None | `403 Forbidden` (`FORBIDDEN_RESOURCE`) |
| **Authenticated** | `GET /accounts/v1/accounts/{id}` | Valid Bearer JWT | Cross-account access denied unless caller has `INTERNAL_WORKLOAD` role | `AUTH_ACCESS_DENIED` | `403 Forbidden` (`UNAUTHORIZED_ACCESS`) |
| **Authenticated** | `POST /v1/groups` | Valid Bearer JWT | Valid subject; non-suspended account | `GROUP_CREATED` | `400 Bad Request` (`VALIDATION_ERROR`) |
| **Authenticated** | `GET /v1/groups/{id}` | Valid Bearer JWT | Active membership in `group_members` for subject | None | `403 Forbidden` (`NOT_GROUP_MEMBER`) or `404 Not Found` (resource hiding) |
| **Authenticated** | `POST /v1/expenses` | Valid Bearer JWT | Active group membership; group not archived; idempotency key non-conflicting | `EXPENSE_CREATED` | `403 Forbidden` (non-member) or `409 Conflict` (`GROUP_ARCHIVED` / `IDEMPOTENCY_CONFLICT`) |
| **Authenticated** | `POST /v1/settlements` | Valid Bearer JWT | Active membership; participant in debt pair; non-zero amount | `SETTLEMENT_RECORDED` | `400 Bad Request` or `403 Forbidden` |
| **Authenticated** | `WS /subscriptions` | Valid Bearer JWT | Active membership in group channel; subscription continuity re-check on changes | `WS_SUBSCRIBED` / `WS_UNSUBSCRIBED` | Subscription terminated if membership revoked |

---

## 3. Sequence Flow: Unauthenticated Lifecycle

```mermaid
%% Source: contracts/rest/accounts.openapi.json, docs/security/endpoint-authentication-matrix.md
sequenceDiagram
    autonumber
    participant U as Unauthenticated Client
    participant B as GraphQL BFF / Gateway
    participant R as Redis (Rate Limit)
    participant A as Accounts Service
    participant DB as Accounts PostgreSQL
    participant M as Outbox / Notification Worker

    Note over U,M: Phase 1: Login Request (Anti-Enumeration)
    U->>B: POST /auth/login/start {"email": "user@example.com"}
    B->>A: POST /accounts/v1/auth/login/start
    A->>R: Atomic Lua check rate-limit (IP + Email buckets)
    alt Rate Limit Exceeded
        R-->>A: Limit exhausted
        A-->>B: 429 Too Many Requests (Retry-After: 60)
        B-->>U: 429 Too Many Requests
    else Admitted
        R-->>A: OK
        A->>A: Normalize email, generate single-use high-entropy token
        A->>DB: Store hashed credential (auth_credentials) with expiry (5-10m)
        A->>DB: Write transactional outbox (auth-email-requested.v1)
        DB-->>A: Commit
        A-->>B: 202 Accepted {"status":"accepted","retryAfterSeconds":60}
        B-->>U: 202 Accepted {"status":"accepted","retryAfterSeconds":60}
        A->>M: Relay outbox event
        M-->>U: Send Magic Link / Code via Email
    end

    Note over U,M: Phase 2: Verification & Session Establishment
    U->>B: POST /auth/login/verify {"credential": "one-time-value"}
    B->>A: POST /accounts/v1/auth/login/verify
    A->>DB: Hash token and select auth_credentials (FOR UPDATE)
    alt Invalid, Expired, or Already Replayed
        DB-->>A: Credential not found or expired
        A-->>B: 401 Unauthorized (Problem Details: INVALID_CREDENTIAL)
        B-->>U: 401 Unauthorized
    else Valid Credential
        A->>DB: Mark consumed = true (atomic single-use)
        A->>A: Mint short-lived access JWT (5-10m) + opaque refresh token
        A->>DB: Store hashed refresh token in auth_sessions (bound to family)
        A->>DB: Insert security audit event (LOGIN_SUCCESS)
        DB-->>A: Commit
        A-->>B: 200 OK + JWT access token + refresh token
        alt Browser Client
            B-->>U: 200 OK (Set-Cookie: HttpOnly, Secure, SameSite=Lax) + X-CSRF-Token
        else Native / API Client
            B-->>U: 200 OK {"accessToken": "...", "refreshToken": "..."}
        end
    end
```

---

## 4. Sequence Flow: Authenticated Lifecycle & Resource Authorization

```mermaid
%% Source: contracts/rest/accounts.openapi.json, docs/security/endpoint-authentication-matrix.md
sequenceDiagram
    autonumber
    participant U as Authenticated Client
    participant B as GraphQL BFF / Gateway
    participant E as Expense Core / Downstream Service
    participant DB as Service PostgreSQL
    participant A as Accounts Service
    participant ADB as Accounts PostgreSQL

    Note over U,ADB: Phase 1: Protected Operation & Resource Authorization
    alt Browser Request
        U->>B: POST /graphql (Cookie: squarewise_access=...)
        B->>B: Extract JWT from Secure HttpOnly Cookie
    else Native / API Request
        U->>B: POST /graphql (Authorization: Bearer <JWT>)
    end
    B->>E: POST /v1/expenses (Forward Authorization: Bearer <JWT>)
    E->>E: Local JWT Verification (signature, exp, iss, aud, alg)
    alt Invalid Signature, Expired, or Untrusted Token
        E-->>B: 401 Unauthorized (Bearer error="invalid_token")
        B-->>U: 401 Unauthorized
    else Valid Token
        E->>DB: Query Resource & Membership (e.g. group_members for sub)
        alt Not a member of the group
            DB-->>E: Member record not found
            E-->>B: 403 Forbidden (Problem Details: NOT_GROUP_MEMBER)
            B-->>U: 403 Forbidden / GraphQL Error
        else Resource Archived or Ineligible
            E-->>B: 409 Conflict (Problem Details: GROUP_ARCHIVED)
            B-->>U: 409 Conflict
        else Authorized Active Member
            E->>DB: Atomic mutation (expense + audit + outbox)
            DB-->>E: Commit
            E-->>B: 201 Created
            B-->>U: 200 OK / GraphQL Response
        end
    end

    Note over U,ADB: Phase 2: Token Refresh & Family Rotation
    U->>B: POST /auth/token/refresh (with refresh cookie / bearer + CSRF)
    B->>A: POST /accounts/v1/auth/token/refresh
    A->>ADB: SELECT FROM auth_sessions WHERE token_hash = hash(token) FOR UPDATE
    alt Refresh Token Replayed (Reuse Detected)
        ADB-->>A: Token already marked consumed/revoked
        A->>ADB: Revoke ALL tokens in family (compromise response)
        ADB-->>A: Commit
        A-->>B: 401 Unauthorized (Session revoked)
        B-->>U: 401 Unauthorized (Clear Cookies)
    else Token Expired or Revoked
        ADB-->>A: Session expired
        A-->>B: 401 Unauthorized
        B-->>U: 401 Unauthorized
    else Valid Active Refresh Token
        A->>ADB: Mark current refresh token revoked
        A->>A: Mint new Access JWT + new Refresh Token (family preserved)
        A->>ADB: Insert new token into auth_sessions
        ADB-->>A: Commit
        A-->>B: 200 OK (New Tokens)
        B-->>U: 200 OK (Rotated Cookies or Payload)
    end

    Note over U,ADB: Phase 3: Explicit Logout & Invalidation
    U->>B: POST /auth/logout (Origin + CSRF / Bearer)
    B->>A: POST /accounts/v1/auth/logout
    A->>ADB: UPDATE auth_sessions SET revoked_at = NOW() WHERE family_id = ...
    A->>ADB: Insert security audit event (SESSION_REVOKED)
    ADB-->>A: Commit
    A-->>B: 204 No Content
    B-->>U: 204 No Content (Clear all cookies)
```

---

## 5. Security & Domain Event Lifecycles

Events in Squarewise follow the strict transactional outbox pattern to guarantee at-least-once delivery
without distributed two-phase commits.

### A. Authentication & Credential Events
- **`auth-email-requested.v1`**:
  - *Trigger:* Successful `POST /accounts/v1/auth/login/start`.
  - *Payload:* Encrypted email payload envelope, expiry timestamp, delivery channel (`LINK` or `CODE`).
  - *Consumer:* Notifications service / SMTP worker. Deduplicated by message ID.
- **`security-audit-event.v1`**:
  - *Trigger:* State changes in security posture (`LOGIN_SUCCESS`, `LOGIN_FAILED`, `SESSION_REVOKED`, `TOKEN_REUSE_DETECTED`, `CREDENTIAL_PURGED`).
  - *Payload:* Timestamp, actor subject, event type, origin IP (sanitized/hashed), user-agent hash.

### B. Financial & Resource Authorization Events
- **`expense.created.v1` / `expense.updated.v1`**:
  - *Trigger:* Committed transaction in Expense Core.
  - *Payload:* `groupId`, `expenseId`, `revision`, `actorSubject`, `allocationsSummary`.
  - *Consumers:*
    - **Notifications Service:** Dispatches inbox alerts to group participants.
    - **GraphQL BFF:** Publishes real-time change hints to active WebSocket group subscribers. Subscribers
      fetch authoritative balance updates over HTTPS queries.
- **`group.membership-revoked.v1`**:
  - *Trigger:* A participant is removed from a group or leaves.
  - *Consumers:*
    - **GraphQL BFF:** Immediately terminates active WebSocket change feeds for that `(subject, groupId)` tuple.
    - **Expense Core:** Rejects subsequent read/mutation operations with `403 NOT_GROUP_MEMBER`.
