#!/bin/sh
set -eu

: "${RABBITMQ_VHOST:?RABBITMQ_VHOST is required}"
: "${RABBITMQ_ACCOUNTS_USER:?RABBITMQ_ACCOUNTS_USER is required}"
: "${RABBITMQ_ACCOUNTS_PASSWORD:?RABBITMQ_ACCOUNTS_PASSWORD is required}"
: "${RABBITMQ_EXPENSE_CORE_USER:?RABBITMQ_EXPENSE_CORE_USER is required}"
: "${RABBITMQ_EXPENSE_CORE_PASSWORD:?RABBITMQ_EXPENSE_CORE_PASSWORD is required}"
: "${RABBITMQ_NOTIFICATIONS_USER:?RABBITMQ_NOTIFICATIONS_USER is required}"
: "${RABBITMQ_NOTIFICATIONS_PASSWORD:?RABBITMQ_NOTIFICATIONS_PASSWORD is required}"

/usr/local/bin/docker-entrypoint.sh rabbitmq-server &
server_pid=$!
trap 'kill "$server_pid" 2>/dev/null || true' INT TERM

until rabbitmq-diagnostics -q ping >/dev/null 2>&1; do
  kill -0 "$server_pid" 2>/dev/null || exit 1
  sleep 1
done

rabbitmqctl add_vhost "$RABBITMQ_VHOST" >/dev/null 2>&1 || true

configure_user() {
  user="$1"
  password="$2"
  configure="$3"
  write="$4"
  read="$5"
  rabbitmqctl add_user "$user" "$password" >/dev/null 2>&1 || rabbitmqctl change_password "$user" "$password"
  rabbitmqctl set_permissions -p "$RABBITMQ_VHOST" "$user" "$configure" "$write" "$read"
}

configure_user "$RABBITMQ_ACCOUNTS_USER" "$RABBITMQ_ACCOUNTS_PASSWORD" '^$' '^squarewise\.events$' '^$'
configure_user "$RABBITMQ_EXPENSE_CORE_USER" "$RABBITMQ_EXPENSE_CORE_PASSWORD" '^$' '^squarewise\.events$' '^$'
configure_user "$RABBITMQ_NOTIFICATIONS_USER" "$RABBITMQ_NOTIFICATIONS_PASSWORD" '^squarewise\..*$' '^squarewise\..*$' '^squarewise\..*$'

wait "$server_pid"
