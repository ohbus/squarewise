#!/usr/bin/env bash
# .devcontainer/post-start.sh - Zero-friction environment setup and development data seeder
set -euo pipefail

echo "========================================================================"
echo "🚀 Squarewise Devcontainer: Initializing development environment..."
echo "========================================================================"

# 1. Ensure deterministic local configuration exists
if [ ! -f "infra/local/.env" ]; then
  cp infra/local/.env.example infra/local/.env
  echo "✓ Initialized infra/local/.env with local development defaults"
else
  echo "✓ infra/local/.env already present"
fi

# 2. Wait for backing services readiness over standard TCP/HTTP
echo "⏳ Awaiting PostgreSQL (postgres-db:5432)..."
POSTGRES_RETRIES=30
until pg_isready -h "${SQUAREWISE_POSTGRES_HOST:-postgres-db}" -p 5432 -U "${PGUSER:-squarewise}" -d squarewise_accounts >/dev/null 2>&1 || [ "$POSTGRES_RETRIES" -le 0 ]; do
  sleep 1
  POSTGRES_RETRIES=$((POSTGRES_RETRIES - 1))
done

if [ "$POSTGRES_RETRIES" -le 0 ]; then
  echo "⚠️ Warning: PostgreSQL readiness check timed out. Proceeding anyway..."
else
  echo "✓ PostgreSQL is accepting connections"
fi

echo "⏳ Awaiting Keycloak OIDC (idp-keycloak:8080)..."
KEYCLOAK_RETRIES=40
KEYCLOAK_ENDPOINT="${SQUAREWISE_KEYCLOAK_URL:-http://idp-keycloak:8080}/health/ready"
until curl -sf "$KEYCLOAK_ENDPOINT" | grep -q "200\|UP" >/dev/null 2>&1 || [ "$KEYCLOAK_RETRIES" -le 0 ]; do
  sleep 1
  KEYCLOAK_RETRIES=$((KEYCLOAK_RETRIES - 1))
done

if [ "$KEYCLOAK_RETRIES" -le 0 ]; then
  echo "⚠️ Warning: Keycloak readiness check timed out. Proceeding anyway..."
else
  echo "✓ Keycloak OIDC Provider is healthy"
fi

# 3. Check if development database is already populated
ACCOUNT_COUNT=$(psql -h "${SQUAREWISE_POSTGRES_HOST:-postgres-db}" -p 5432 -U "${PGUSER:-squarewise}" -d squarewise_accounts -tAc "SELECT COUNT(*) FROM account_profiles;" 2>/dev/null || echo "0")

if [ "${ACCOUNT_COUNT:-0}" -eq 0 ]; then
  echo "🌱 Seeding initial development personas, groups, and transactions..."
  if uv run python3 tools/ops/seed_dev_data.py; then
    echo "✓ Development database successfully seeded"
  else
    echo "⚠️ Warning: Seeding script encountered an issue. You can run 'make seed' anytime."
  fi
else
  echo "✓ Development database already contains active seed data (${ACCOUNT_COUNT} profiles)"
fi

# 4. Display Welcome Dashboard
cat << 'EOF'
========================================================================
🎉 SQUAREWISE DEVCONTAINER IS READY
========================================================================
All backing services and preloaded personas are active and ready.

Local Endpoints (Deterministic Ports):
  • GraphQL BFF Gateway & WS : http://localhost:28080/graphql
  • Accounts REST API        : http://localhost:28081/accounts/v1/
  • Expense Core REST API    : http://localhost:28082/expense-core/v1/
  • Notifications REST API   : http://localhost:28083/notifications/v1/
  • Mailpit Email Web UI     : http://localhost:28025/
  • Keycloak OIDC Provider   : http://localhost:28090/
  • RabbitMQ Management UI   : http://localhost:28673/ (squarewise / squarewise-local-only)
  • PostgreSQL Wire Protocol : localhost:25432 (squarewise / squarewise-local-only)

Preloaded Personas for Local Development:
  • Alice   : alice@squarewise.local   (Keycloak client: squarewise-ci)
  • Bob     : bob@squarewise.local     (Keycloak client: squarewise-ci-e2e-bob)
  • Charlie : charlie@squarewise.local (Keycloak client: squarewise-ci-e2e-nonmember)
  • Dave    : dave@squarewise.local
  • Eve     : eve@squarewise.local

Useful Commands:
  • make test-unit      : Run unit test suite
  • make check          : Run complete contract, schema, and linter validation
  • make seed           : Re-run dev data seeder anytime
  • make compose-dev-up : Start all four microservice JARs in Docker
========================================================================
EOF
