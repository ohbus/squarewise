#!/usr/bin/env sh
set -eu

COMPOSE_ARGS="-f ${COMPOSE_BASE:-infra/local/docker-compose.yml} -f ${COMPOSE_DEV:-infra/local/docker-compose.dev.yml} -f ${COMPOSE_REPLICA:-infra/local/docker-compose.replica.yml}"
PRIMARY_SERVICE=${PRIMARY_SERVICE:-postgres}
REPLICA_SERVICE=${REPLICA_SERVICE:-postgres-replica}
EXPENSE_CORE_URL=${EXPENSE_CORE_URL:-http://localhost:28082}
AUTH_HEADER=${AUTH_HEADER:-}

compose() { # shellcheck disable=SC2086
  docker compose $COMPOSE_ARGS "$@"
}

compose ps --status running "$PRIMARY_SERVICE" "$REPLICA_SERVICE" >/dev/null
replication_state=$(compose exec -T "$PRIMARY_SERVICE" psql -U squarewise -d postgres -tAc \
  "SELECT COALESCE(bool_and(state = 'streaming'), false) FROM pg_stat_replication;" | tr -d '[:space:]')
recovery_state=$(compose exec -T "$REPLICA_SERVICE" psql -U squarewise -d postgres -tAc \
  "SELECT pg_is_in_recovery();" | tr -d '[:space:]')
replay_lsn=$(compose exec -T "$REPLICA_SERVICE" psql -U squarewise -d postgres -tAc \
  "SELECT COALESCE(pg_last_wal_replay_lsn()::text, 'NULL');" | tr -d '[:space:]')
if [ -n "$AUTH_HEADER" ]; then
  metrics=$(curl -fsS -H "$AUTH_HEADER" "$EXPENSE_CORE_URL/actuator/prometheus")
else
  metrics=$(curl -fsS "$EXPENSE_CORE_URL/actuator/health")
fi

printf 'replica_streaming=%s\n' "$replication_state"
printf 'replica_in_recovery=%s\n' "$recovery_state"
printf 'replica_replay_lsn=%s\n' "$replay_lsn"
if [ -n "$AUTH_HEADER" ]; then
  telemetry_present=$(printf '%s\n' "$metrics" | grep -q 'squarewise_db_' && echo true || echo false)
else
  telemetry_present=not_checked
fi
printf 'route_telemetry_present=%s\n' "$telemetry_present"

[ "$replication_state" = true ] || [ "$replication_state" = t ]
[ "$recovery_state" = true ] || [ "$recovery_state" = t ]
[ "$replay_lsn" != NULL ]
if [ -n "$AUTH_HEADER" ]; then
  [ "$telemetry_present" = true ]
fi
