# OPS-07: CI dependency service containers

Run isolated PostgreSQL and RabbitMQ containers for each verification and E2E
job. GitHub performs health checks before steps start; credentials and
connection variables are stable and documented. Jobs remain independently
parallel and do not share mutable service state.

The local Compose stack uses matching health checks for PostgreSQL, RabbitMQ,
and Mailpit so dependent development services can wait on readiness.
