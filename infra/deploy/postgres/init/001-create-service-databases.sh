#!/bin/sh
set -eu

: "${POSTGRES_ACCOUNTS_USER:?POSTGRES_ACCOUNTS_USER is required}"
: "${POSTGRES_ACCOUNTS_PASSWORD:?POSTGRES_ACCOUNTS_PASSWORD is required}"
: "${POSTGRES_EXPENSE_CORE_USER:?POSTGRES_EXPENSE_CORE_USER is required}"
: "${POSTGRES_EXPENSE_CORE_PASSWORD:?POSTGRES_EXPENSE_CORE_PASSWORD is required}"
: "${POSTGRES_NOTIFICATIONS_USER:?POSTGRES_NOTIFICATIONS_USER is required}"
: "${POSTGRES_NOTIFICATIONS_PASSWORD:?POSTGRES_NOTIFICATIONS_PASSWORD is required}"

create_role() {
  psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=role_name="$1" --set=role_password="$2" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role_name', :'role_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role_name');
\gexec
SQL
}

create_database() {
  psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=database_name="$1" --set=database_owner="$2" <<'SQL'
SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'database_owner')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'database_name');
\gexec
SQL
  psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=database_name="$1" --set=database_owner="$2" <<'SQL'
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'database_name');
\gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'database_name', :'database_owner');
\gexec
SQL
}

create_role "$POSTGRES_ACCOUNTS_USER" "$POSTGRES_ACCOUNTS_PASSWORD"
create_role "$POSTGRES_EXPENSE_CORE_USER" "$POSTGRES_EXPENSE_CORE_PASSWORD"
create_role "$POSTGRES_NOTIFICATIONS_USER" "$POSTGRES_NOTIFICATIONS_PASSWORD"

create_database "${POSTGRES_DB:-squarewise}_accounts" "$POSTGRES_ACCOUNTS_USER"
create_database "${POSTGRES_DB:-squarewise}_expense_core" "$POSTGRES_EXPENSE_CORE_USER"
create_database "${POSTGRES_DB:-squarewise}_notifications" "$POSTGRES_NOTIFICATIONS_USER"
