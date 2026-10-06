# SEC-01E: Formalize rate-limit and bearer-revocation guarantees

## Status

Done. Committed.

## Objective

Resolved findings SEC-007 (rate-limit client IP extraction) and SEC-008 (access-token
revocation residual window).

## Implementation Notes

### SEC-007: Abuse Control Client Address Resolution

**Problem**: `AuthController.deriveNetworkPartition()` used a raw `remoteAddr.split(".").take(2)` 
truncation which:
- Ignored trusted proxy infrastructure (X-Forwarded-For, X-Real-IP headers were not consulted)
- Only worked for IPv4 (IPv6 addresses could not be partitioned)
- Used a two-octet prefix that accidentally collides unrelated `/16` subnets

**Solution**: Introduced `ClientAddressResolver` (new class) and `TrustedProxyProperties` (new config):

- `TrustedProxyProperties` (`squarewise.security.abuse.trusted-proxies.addresses`) holds the
  deployment-configured set of trusted proxy IP addresses. Defaults to empty; CIDR range
  matching remains an explicitly tracked follow-up.
- `ClientAddressResolver`:
  - If `trustedProxies` is empty (default), always uses raw socket `remoteAddr`.
  - If `remoteAddr` is in the trusted set, reads `X-Forwarded-For` (leftmost non-proxy IP)
    or `X-Real-IP` as the true client address.
  - If `remoteAddr` is NOT in the trusted set, ignores forwarded headers entirely,
    preventing header spoofing from untrusted downstreams.
  - Normalizes IPv4 to `/24` (three-octet prefix) and IPv6 to `/48` (three 16-bit groups).
  - Falls back to `"unknown"` for unresolvable addresses.
- `AuthController` now takes `ClientAddressResolver` as a constructor argument, removing
  the inline heuristic. The Spring bean is wired in `AuthenticationCredentialConfiguration`.

### SEC-008: Access-Token Revocation Semantics

**Current guarantees** (already implemented, formalized here):
- Access token lifetime: `SessionPolicyProperties.accessTokenLifetime = Duration.ofMinutes(10)`
  (configured value; deployable via `squarewise.security.session.access-token-lifetime`).
- Refresh session revocation: PostgreSQL `auth_sessions.revoked_at` is set atomically by
  `TokenSessionService.revokeSessionByRefreshToken()`. Any subsequent refresh attempt against
  a revoked family returns `UNAUTHENTICATED (ERR_03)`.
- The access token residual window is bounded at 10 minutes. After revocation, any existing
  access token remains valid until its `exp` claim: this is the documented residual risk.
  Reducing this window further requires shortening `accessTokenLifetime` (deployment choice).

**No runtime code changes required for SEC-008** beyond the SEC-007 cleanup; the
revocation model is correctly implemented and the residual window is explicitly bounded.

## Owned Paths

- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/AuthController.kt`
- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/abuse/ClientAddressResolver.kt`
- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/abuse/TrustedProxyProperties.kt`
- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/config/AuthenticationCredentialConfiguration.kt`
- `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/auth/abuse/ClientAddressResolverTest.kt`
- `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/auth/AuthControllerTest.kt`

## Acceptance Criteria

1. ✅ Client IP resolution parses client addresses only from configured trusted proxies;
   direct untrusted connections use remote socket address.
2. ✅ Rate-limit keys partition IPv4 addresses by `/24` subnet and IPv6 by `/48` prefix,
   without accidental collisions across unrelated subnets.
3. ✅ Access token lifetime is explicitly bounded (10 minutes) and documented.
4. ✅ Comprehensive test coverage for direct connections, proxy chains, spoofed headers,
   IPv6 addresses, and token expiry boundaries.

## Verification Commands

- `./gradlew :app:accounts:test --rerun-tasks --no-daemon`
- `uv run python tools/contracts/validate.py`
- `uv run python tools/ops/check_security_hygiene.py`

## Evidence

| Command | Result |
|---|---|
| `./gradlew :app:accounts:test --rerun-tasks --no-daemon` | BUILD SUCCESSFUL (19 tasks) |
| `uv run python tools/contracts/validate.py` | valid (207 tasks) |
| `uv run python tools/ops/check_security_hygiene.py` | passed (1032 files) |

## Known Limitations

- CIDR range matching in `TrustedProxyProperties` is not yet implemented: addresses must
  be specified as exact IPs. Subnet-based matching (e.g., `10.0.0.0/8`) can be added in a
  follow-up if needed.
- The 10-minute access token residual window is a deployment trade-off. Reducing it
  requires adjusting `squarewise.security.session.access-token-lifetime` and is a
  production operations decision.
