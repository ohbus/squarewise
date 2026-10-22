#!/bin/sh
set -eu

: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_ACCOUNTS_USER:?POSTGRES_ACCOUNTS_USER is required}"
: "${POSTGRES_ACCOUNTS_PASSWORD:?POSTGRES_ACCOUNTS_PASSWORD is required}"
: "${POSTGRES_EXPENSE_CORE_USER:?POSTGRES_EXPENSE_CORE_USER is required}"
: "${POSTGRES_EXPENSE_CORE_PASSWORD:?POSTGRES_EXPENSE_CORE_PASSWORD is required}"
: "${POSTGRES_NOTIFICATIONS_USER:?POSTGRES_NOTIFICATIONS_USER is required}"
: "${POSTGRES_NOTIFICATIONS_PASSWORD:?POSTGRES_NOTIFICATIONS_PASSWORD is required}"

psql_admin() {
  PGPASSWORD="$POSTGRES_PASSWORD" psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" "$@"
}

ensure_role() {
  psql_admin --set=role_name="$1" --set=role_password="$2" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role_name', :'role_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role_name');
\gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'role_name', :'role_password');
\gexec
SQL
}

ensure_database_access() {
  database="$1"
  role="$2"
  psql_admin --set=database_name="$database" --set=database_owner="$role" <<'SQL'
SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'database_owner')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'database_name');
\gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'database_name', :'database_owner');
\gexec
SQL

  psql_admin --set=database_name="$database" --set=database_owner="$role" <<'SQL'
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'database_name', :'database_owner');
\gexec
SQL
  PGPASSWORD="$POSTGRES_PASSWORD" psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$database" \
    --set=database_owner="$role" --set=legacy_owner="$POSTGRES_USER" <<'SQL'
SELECT format(
  'ALTER %s %s OWNER TO %I',
  CASE c.relkind
    WHEN 'S' THEN 'SEQUENCE'
    WHEN 'v' THEN 'VIEW'
    WHEN 'm' THEN 'MATERIALIZED VIEW'
    WHEN 'f' THEN 'FOREIGN TABLE'
    ELSE 'TABLE'
  END,
  c.oid::regclass,
  :'database_owner'
)
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p', 'S', 'v', 'm', 'f')
  AND pg_get_userbyid(c.relowner) = :'legacy_owner'
  AND NOT (
    c.relkind = 'S'
    AND EXISTS (
      SELECT 1
      FROM pg_depend d
      WHERE d.objid = c.oid
        AND d.deptype IN ('a', 'i')
    )
  )
ORDER BY c.relkind = 'S', c.oid;
\gexec
SELECT format('ALTER SCHEMA public OWNER TO %I', :'database_owner');
\gexec
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'database_owner');
\gexec
SELECT format('GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO %I', :'database_owner');
\gexec
SELECT format('GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO %I', :'database_owner');
\gexec
SELECT format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO %I', :'database_owner');
\gexec
SELECT format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO %I', :'database_owner');
\gexec
SQL
}

base_database="${POSTGRES_DB:-squarewise}"
ensure_role "$POSTGRES_ACCOUNTS_USER" "$POSTGRES_ACCOUNTS_PASSWORD"
ensure_role "$POSTGRES_EXPENSE_CORE_USER" "$POSTGRES_EXPENSE_CORE_PASSWORD"
ensure_role "$POSTGRES_NOTIFICATIONS_USER" "$POSTGRES_NOTIFICATIONS_PASSWORD"

ensure_database_access "${base_database}_accounts" "$POSTGRES_ACCOUNTS_USER"
ensure_database_access "${base_database}_expense_core" "$POSTGRES_EXPENSE_CORE_USER"
ensure_database_access "${base_database}_notifications" "$POSTGRES_NOTIFICATIONS_USER"
