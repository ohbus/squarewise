# ERRC-16: Implement servlet and reactive security boundaries

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 3 — Transport boundaries
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Implement dedicated, structured security error entry points and access-denied handlers for Spring Security across both Servlet (Accounts, Expense Core, Notifications) and Reactive WebFlux (BFF) stacks. Eliminate empty/bodyless security responses, preserve standard challenge headers (`WWW-Authenticate`), enforce anti-enumeration protections (404 hiding for protected entities), and format security errors as compliant RFC 9457 Problem Details.

## Dependencies

- Preceding: [`ERRC-15: Implement servlet Problem mapper and containment`](ERRC-15.md)

## Owned Paths

- `docs/tasks/details/ERRC-16.md`
- `libs/security/src/main/kotlin/com/subhrodip/squarewise/security/errors/`
- `libs/security/src/test/kotlin/com/subhrodip/squarewise/security/errors/`

## Architecture & Design Patterns

- **Ports & Adapters (Security Boundary Adapter)**: Adapts Spring Security's `AuthenticationEntryPoint` and `AccessDeniedHandler` interfaces to produce standardized Squarewise Problem Details.
- **Principle of Least Privilege & Least Disclosure**: Prevents user enumeration by ensuring unauthenticated or unauthorized access to non-public endpoints returns uniform 401 or anti-enumeration 404 responses without distinguishing whether an ID exists or is unauthorized.
- **Dual-Stack Architectural Alignment**: Provides parallel, isolated implementations for both Spring MVC Servlet filters and Spring WebFlux reactive filters.
- **Strict SOLID File Separation**: Separate files for Servlet entry point, Servlet access denied, WebFlux entry point, WebFlux access denied, and security challenge builder.

## Common Libraries & Framework Integration

- **`libs/security`**: Security filters, JWT authentication, and entry point configurations.
- **`libs/errors`**: ProblemDetails DTO and platform security error definitions (`PlatformErrors.SECURITY_*`).

## Technical Requirements & Deliverables

1. **Servlet Security Handlers (Dedicated Files)**:
   - `ServletProblemAuthenticationEntryPoint.kt`:
     - Implements `AuthenticationEntryPoint`.
     - Writes 401 ProblemDetails (`927101` / `AUTH_UNAUTHENTICATED`) to `HttpServletResponse`.
     - Sets header `WWW-Authenticate: Bearer error="invalid_token"`.
   - `ServletProblemAccessDeniedHandler.kt`:
     - Implements `AccessDeniedHandler`.
     - Writes 403 ProblemDetails (`927501` / `AUTH_ACCESS_DENIED`) or anti-enumeration 404.
2. **Reactive WebFlux Security Handlers (Dedicated Files)**:
   - `ReactiveProblemAuthenticationEntryPoint.kt`:
     - Implements `ServerAuthenticationEntryPoint`.
     - Serializes 401 ProblemDetails to WebFlux `ServerHttpResponse`.
   - `ReactiveProblemAccessDeniedHandler.kt`:
     - Implements `ServerAccessDeniedHandler`.
     - Serializes 403 ProblemDetails to WebFlux `ServerHttpResponse`.
3. **Security Challenge Builder (`SecurityChallengeHeaderBuilder.kt`)**:
   - RFC 6750 compliant `WWW-Authenticate` header generator.
4. **Integration Tests (`SecurityErrorFilterTest.kt`, `ReactiveSecurityErrorFilterTest.kt`)**:
   - Verify filter chain catches unauthenticated requests before reaching controller advice.
   - Assert presence of `WWW-Authenticate` header and compliant ProblemDetails body.

## Acceptance Criteria

1. Every handler and utility resides in its own isolated `.kt` file under `com.subhrodip.squarewise.security.errors`.
2. Unauthenticated requests trigger formatted JSON ProblemDetails with HTTP 401 and `WWW-Authenticate` header.
3. Access-denied events produce 403 or anti-enumeration 404 ProblemDetails; zero bodyless HTTP responses.
4. Both Servlet and Reactive stacks have 100% test coverage for authentication and access-denied paths.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :libs:security:test :libs:security:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JaCoCo test report showing 100% coverage across security error handler classes.
- Test logs verifying exact JSON body and header output on unauthenticated requests.

## Rollout & Rollback Strategy

- Replaces default Spring Security error handling in `libs/security`.
- Backward-compatible; clients expecting 401/403 now receive structured JSON rather than empty responses.
- Rollback: Revert security filter configuration if header conflicts emerge.

## Implementation Notes and Evidence

- Added dedicated servlet and WebFlux authentication-entry-point and
  access-denied handlers under `libs/security`, with non-empty Problem Details
  bodies and RFC 6750 `WWW-Authenticate: Bearer error="invalid_token"`.
- Added opt-in anti-enumeration behavior that maps protected-resource denial to
  the static 404 `RESOURCE_NOT_FOUND` definition without revealing existence.
- Added a provider-independent security body renderer using only compiled
  Platform catalog definitions; no token, exception, or request payload text is
  serialized.
- JaCoCo package evidence on 2026-10-07: `com/subhrodip/squarewise/security/errors`
  reports 38/38 lines and 12/12 branches.
- Validation passed: `./gradlew.bat :libs:security:test
  :libs:security:jacocoTestReport --rerun-tasks --no-daemon --console=plain`,
  `uv run python tools/contracts/validate.py`, and `git diff --check`.
