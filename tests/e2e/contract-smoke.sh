#!/usr/bin/env sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"
uv_command=uv
if ! command -v "$uv_command" >/dev/null 2>&1 && command -v uv.exe >/dev/null 2>&1; then
  uv_command=uv.exe
fi
"$uv_command" run --frozen --no-build python tools/contracts/validate.py
docker compose -f infra/local/docker-compose.yml config --quiet
SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET="${SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET:-AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=}" \
SQUAREWISE_SECURITY_AUTH_EMAIL_ENVELOPE_KEY="${SQUAREWISE_SECURITY_AUTH_EMAIL_ENVELOPE_KEY:-ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=}" \
docker compose -f infra/local/docker-compose.dev.yml config --quiet
SQUAREWISE_REDIS_HOST=redis.contract.invalid \
SQUAREWISE_REDIS_PASSWORD=contract-only \
SQUAREWISE_REDIS_SSL_ENABLED=false \
SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8= \
SQUAREWISE_ACCOUNTS_IMAGE=example/accounts:local \
SQUAREWISE_EXPENSE_CORE_IMAGE=example/expense-core:local \
SQUAREWISE_NOTIFICATIONS_IMAGE=example/notifications:local \
SQUAREWISE_BFF_IMAGE=example/bff:local \
docker compose -f infra/deploy/docker-compose.prod.yml config --quiet
printf '%s\n' "contract and deployment smoke checks passed"
