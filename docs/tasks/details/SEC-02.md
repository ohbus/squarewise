# SEC-02: Security audit remediation and hardening closure

## Status, objective, and evidence boundary

**Status:** done.

**Objective:** Implement and verify fixes for the 13 codebase and configuration
security audit findings (H-1, H-2, M-1 through M-5, L-1 through L-6).

## Scope and implementation deliverables

### Phase 1: Edge & HTTP Security Hardening (H-1, H-2)
- Added shared `HttpHeadersConfiguration` in `libs/security` enforcing `HSTS` (31536000s, preload, includeSubDomains), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `X-XSS-Protection: 0`, `Referrer-Policy: strict-origin-when-cross-origin`, `Content-Security-Policy`, and `Permissions-Policy` across Servlet (`HttpSecurity`) and Reactive WebFlux (`ServerHttpSecurity`).
- Integrated `HttpHeadersConfiguration` into production security filter chains across Accounts, Expense Core, Notifications, and BFF.
- Added actuator operational constants (`ACTUATOR_PROMETHEUS`, `ACTUATOR_METRICS`, `ACTUATOR_INFO`) to `ApiEndpoints.Operations`.

### Phase 2: Secrets, Container & Build Pipeline Hygiene (M-1, L-1, L-4, L-5)
- Added `CryptographicSecretSanityGuard` in `libs/security` preventing startup with predictable well-known test keys under `production` and `staging` profiles.
- Created root `.dockerignore` excluding local `.env`, Git history, IDE configs, and test outputs from Docker daemon contexts.
- Calibrated container JVM heap memory to `-XX:MaxRAMPercentage=65` in `Dockerfile.jvm` and `Dockerfile.fast`.

### Phase 3: Database Isolation & Infrastructure Boundaries (M-2, M-3, L-6)
- Configured SCRAM-SHA-256 password authentication for local domain socket connections in `pg_hba.conf`.
- Provisioned dedicated least-privilege service roles (`accounts_user`, `expense_core_user`, `notifications_user`) in `init-databases.sql`.
- Added scheduled `CredentialCleanupTask` in Accounts with repository purge method and unit tests.

### Phase 4: Zero-Trust Inter-Service Security & Documentation (M-4, M-5, L-2)
- Documented in-transit mutual TLS (mTLS) and workload authorization standards in `docs/architecture/overview.md`.
- Documented Keycloak production configuration standards (`start --optimized`) in `docs/operations/quickstart.md`.

## Acceptance and verification evidence

- `make lint`: passed (all contracts, GraphQL, registries, and Spotless verified).
- `make python-typecheck`: passed (31 source files clean).
- `python tools/ops/check_security_hygiene.py`: passed (1041 tracked files clean).
- `python tools/ops/validate_sbom_baseline.py`: passed.
- `./gradlew test`: passed across all modules (Accounts, Expense Core, Notifications, BFF, and libraries).
