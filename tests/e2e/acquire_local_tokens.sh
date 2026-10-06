#!/usr/bin/env bash
# acquire_local_tokens.sh - Acquire ephemeral signed local OIDC tokens from Keycloak for E2E tests
set -euo pipefail

# Ensure keycloak container is reachable
TOKEN_ENDPOINT="http://idp-keycloak:8080/realms/squarewise/protocol/openid-connect/token"
RESOLVE_FLAG=("--connect-to" "idp-keycloak:8080:127.0.0.1:${KEYCLOAK_HOST_PORT:-28090}")

fetch_token() {
  local client_id="$1"
  local client_secret="$2"
  local realm="${3:-squarewise}"
  local endpoint="http://idp-keycloak:8080/realms/${realm}/protocol/openid-connect/token"

  local response
  response="$(curl --fail-with-body --silent --show-error \
    "${RESOLVE_FLAG[@]}" \
    --data "grant_type=client_credentials&client_id=${client_id}&client_secret=${client_secret}" \
    "${endpoint}")"
  python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])' <<< "$response"
}

TOKEN="$(fetch_token squarewise-ci squarewise-ci-local-only)"
test -n "$TOKEN" || { echo "Keycloak returned no primary access token" >&2; exit 2; }

BOB_TOKEN="$(fetch_token squarewise-ci-e2e-bob squarewise-ci-e2e-bob-local-only)"
test -n "$BOB_TOKEN" || { echo "Keycloak returned no E2E second-persona token" >&2; exit 2; }

NONMEMBER_TOKEN="$(fetch_token squarewise-ci-e2e-nonmember squarewise-ci-e2e-nonmember-local-only)"
test -n "$NONMEMBER_TOKEN" || { echo "Keycloak returned no E2E non-member token" >&2; exit 2; }

# The local OIDC personas are provider-authenticated, but Accounts deliberately
# does not auto-provision profiles on a read. Enroll the three CI fixture
# subjects explicitly in the local Accounts database so all E2E suites exercise
# the real authenticated profile path without weakening production behavior.
provision_local_profile() {
  local token="$1"
  local display_name="$2"
  local email="$3"
  local subject issuer account_id identity_id
  readarray -t claims < <(python3 - "$token" <<'PY'
import base64
import json
import sys

payload = sys.argv[1].split('.')[1]
payload += '=' * (-len(payload) % 4)
claims = json.loads(base64.urlsafe_b64decode(payload))
print(claims["sub"])
print(claims["iss"])
PY
  )
  subject="${claims[0]}"
  issuer="${claims[1]}"
  account_id="$(python3 - "$subject" <<'PY'
import sys
import uuid

print(uuid.uuid5(uuid.UUID("7e5c0c7f-8e0e-4d8d-9f9d-7e2c6f8d3d4b"), f"squarewise-ci:{sys.argv[1]}"))
PY
  )"
  identity_id="$(python3 - "$issuer" "$subject" <<'PY'
import sys
import uuid

print(uuid.uuid5(uuid.UUID("7e5c0c7f-8e0e-4d8d-9f9d-7e2c6f8d3d4b"), f"identity:{sys.argv[1]}:{sys.argv[2]}"))
PY
  )"

  docker compose -f infra/local/docker-compose.dev.yml exec -T postgres \
    psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-squarewise}" -d squarewise_accounts \
    -v account_id="$account_id" -v identity_id="$identity_id" -v subject="$subject" -v issuer="$issuer" \
    -v display_name="$display_name" -v email="$email" <<'SQL'
INSERT INTO account_profiles (account_id, subject, display_name, timezone, default_currency)
VALUES (:'account_id'::uuid, :'subject', :'display_name', 'UTC', 'EUR')
ON CONFLICT (subject) DO UPDATE
SET display_name = EXCLUDED.display_name,
    timezone = EXCLUDED.timezone,
    default_currency = EXCLUDED.default_currency;

INSERT INTO account_identities (
    identity_id, account_id, issuer, provider_subject, email, email_verified, status
)
VALUES (
    :'identity_id'::uuid, :'account_id'::uuid, :'issuer', :'subject', :'email', TRUE, 'ACTIVE'
)
ON CONFLICT (issuer, provider_subject) DO UPDATE
SET account_id = EXCLUDED.account_id,
    email = EXCLUDED.email,
    email_verified = TRUE,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP;
SQL
}

provision_local_profile "$TOKEN" "Alice" "alice@squarewise.local"
provision_local_profile "$BOB_TOKEN" "Bob" "bob@squarewise.local"
provision_local_profile "$NONMEMBER_TOKEN" "Nonmember" "nonmember@squarewise.local"

WRONG_AUDIENCE_TOKEN="$(fetch_token squarewise-ci-wrong-audience squarewise-ci-wrong-audience-local-only)"
test -n "$WRONG_AUDIENCE_TOKEN" || { echo "Keycloak returned no negative-audience token" >&2; exit 2; }

WRONG_ISSUER_TOKEN="$(fetch_token squarewise-ci-wrong-issuer squarewise-ci-wrong-issuer-local-only squarewise-other)"
test -n "$WRONG_ISSUER_TOKEN" || { echo "Keycloak returned no negative-issuer token" >&2; exit 2; }

# Optional: mint expired token if requested by setting INCLUDE_EXPIRED=true
EXPIRED_TOKEN=""
if [ "${INCLUDE_EXPIRED:-false}" = "true" ]; then
  docker compose -f infra/local/docker-compose.dev.yml exec -T idp-keycloak \
    /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 \
    --realm master --user local-admin --password local-admin-only
  docker compose -f infra/local/docker-compose.dev.yml exec -T idp-keycloak \
    /opt/keycloak/bin/kcadm.sh update realms/squarewise -s accessTokenLifespan=1
  EXPIRED_TOKEN="$(fetch_token squarewise-ci squarewise-ci-local-only)"
  docker compose -f infra/local/docker-compose.dev.yml exec -T idp-keycloak \
    /opt/keycloak/bin/kcadm.sh update realms/squarewise -s accessTokenLifespan=300
  # Spring's default bounded clock skew is 60 seconds; wait beyond it.
  sleep 65
fi

# Export or output to GITHUB_OUTPUT / env file
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "token=${TOKEN}" >> "$GITHUB_OUTPUT"
  echo "e2e_bob_token=${BOB_TOKEN}" >> "$GITHUB_OUTPUT"
  echo "e2e_nonmember_token=${NONMEMBER_TOKEN}" >> "$GITHUB_OUTPUT"
  echo "wrong_audience_token=${WRONG_AUDIENCE_TOKEN}" >> "$GITHUB_OUTPUT"
  echo "wrong_issuer_token=${WRONG_ISSUER_TOKEN}" >> "$GITHUB_OUTPUT"
  if [ -n "${EXPIRED_TOKEN}" ]; then
    echo "expired_token=${EXPIRED_TOKEN}" >> "$GITHUB_OUTPUT"
  fi
fi

if [ -n "${GITHUB_ENV:-}" ]; then
  echo "BEARER_TOKEN=${TOKEN}" >> "$GITHUB_ENV"
  echo "SQUAREWISE_E2E_TOKEN_A=${TOKEN}" >> "$GITHUB_ENV"
  echo "SQUAREWISE_E2E_TOKEN_B=${BOB_TOKEN}" >> "$GITHUB_ENV"
  echo "SQUAREWISE_E2E_TOKEN_NONMEMBER=${NONMEMBER_TOKEN}" >> "$GITHUB_ENV"
  echo "WRONG_ISSUER_TOKEN=${WRONG_ISSUER_TOKEN}" >> "$GITHUB_ENV"
  echo "SQUAREWISE_BRUNO_TOKEN=${TOKEN}" >> "$GITHUB_ENV"
  echo "SQUAREWISE_BRUNO_WRONG_AUDIENCE_TOKEN=${WRONG_AUDIENCE_TOKEN}" >> "$GITHUB_ENV"
  echo "SQUAREWISE_BRUNO_WRONG_ISSUER_TOKEN=${WRONG_ISSUER_TOKEN}" >> "$GITHUB_ENV"
  if [ -n "${EXPIRED_TOKEN}" ]; then
    echo "EXPIRED_TOKEN=${EXPIRED_TOKEN}" >> "$GITHUB_ENV"
    echo "SQUAREWISE_BRUNO_EXPIRED_TOKEN=${EXPIRED_TOKEN}" >> "$GITHUB_ENV"
  fi
fi

echo "Successfully acquired local OIDC tokens."
