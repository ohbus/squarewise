#!/usr/bin/env sh
set -eu
BASE_URL=${BASE_URL:-http://localhost:28080}; HEALTH_PATH=${HEALTH_PATH:-/actuator/health/readiness}
REQUESTS=${REQUESTS:-20}; CONCURRENCY=${CONCURRENCY:-4}; AUTH_HEADER=${AUTH_HEADER:-}
case "$REQUESTS" in *[!0-9]*|'') echo "REQUESTS must be numeric" >&2; exit 2;; esac
case "$CONCURRENCY" in *[!0-9]*|0) echo "CONCURRENCY must be positive" >&2; exit 2;; esac
run_one() { if [ -n "$AUTH_HEADER" ]; then curl -fsS -o /dev/null -H "$AUTH_HEADER" "$BASE_URL$HEALTH_PATH"; else curl -fsS -o /dev/null "$BASE_URL$HEALTH_PATH"; fi; }
start=$(date +%s); failures=0; i=1; pids=
while [ "$i" -le "$REQUESTS" ]; do
  batch=0
  while [ "$batch" -lt "$CONCURRENCY" ] && [ "$i" -le "$REQUESTS" ]; do
    (run_one) & pids="$pids $!"; i=$((i + 1)); batch=$((batch + 1))
  done
  for pid in $pids; do wait "$pid" || failures=$((failures + 1)); done
  pids=
done
elapsed=$(( $(date +%s) - start )); [ "$elapsed" -gt 0 ] || elapsed=1
completed=$((REQUESTS - failures))
echo "base_url=$BASE_URL requests=$REQUESTS concurrency=$CONCURRENCY completed=$completed failures=$failures elapsed_seconds=$elapsed requests_per_second=$((completed / elapsed))"
[ "$failures" -eq 0 ]
