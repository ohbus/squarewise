SHELL := /bin/sh

ifeq ($(OS),Windows_NT)
GRADLE ?= gradlew.bat
else
GRADLE ?= ./gradlew
endif
COMPOSE ?= docker compose
UV_RUN := uv run --frozen --no-build
LOCAL_COMPOSE := infra/local/docker-compose.yml
ACCOUNTS_COMPOSE := infra/local/docker-compose.accounts.yml
EXPENSE_CORE_COMPOSE := infra/local/docker-compose.expense-core.yml
NOTIFICATIONS_COMPOSE := infra/local/docker-compose.notifications.yml
BFF_COMPOSE := infra/local/docker-compose.bff.yml
DEV_COMPOSE := infra/local/docker-compose.dev.yml
PROD_COMPOSE := infra/deploy/docker-compose.prod.yml
export SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET ?= AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=
export SQUAREWISE_SECURITY_AUTH_EMAIL_ENVELOPE_KEY ?= ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=

.DEFAULT_GOAL := help
.PHONY: help doctor bootstrap sync validate contracts lint python-typecheck test test-unit test-integration coverage build package error-hygiene check ci ci-e2e acceptance acceptance-live bruno-run workflow-validate observability-validate release-gate security-hygiene architecture-validate sbom-validate rollout-validate load-probe load-k6-validate load-k6 load-k6-error-storm load-k6-rate-limit e2e e2e-auth-email e2e-auth-notification-limit e2e-notification-general-limit e2e-auth-notification-outage e2e-auth-login-replicas e2e-auth-refresh-concurrency e2e-rest-edge e2e-auth-cache e2e-auth-surfaces e2e-auth-bff-replicas e2e-auth-no-accounts e2e-auth-query-latency smoke docs-diagrams docs-diagrams-config compose-config devcontainer-config redis-status redis-logs redis-clear-rate-limit generate-secrets deps-config deps-up deps-status deps-logs deps-down accounts-deps-config accounts-deps-up accounts-deps-status accounts-deps-logs accounts-deps-down expense-core-deps-config expense-core-deps-up expense-core-deps-status expense-core-deps-logs expense-core-deps-down notifications-deps-config notifications-deps-up notifications-deps-status notifications-deps-logs notifications-deps-down bff-deps-config bff-deps-up bff-deps-status bff-deps-logs bff-deps-down full-config full-up full-status full-logs full-down compose-dev-up compose-dev-down compose-dev-logs compose-up compose-down dev-setup seed seed-large seed-reset docker-build-all docker-build-% prod-config clean clean-gradle status

help: ## Show available commands
	@awk 'BEGIN {FS = ":.*##"; printf "Squarewise commands\n\n"} /^[a-zA-Z0-9_.-]+:.*##/ {printf "  %-24s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

doctor: ## Check required local tools and versions
	@if [ -z "$$DEVCONTAINER" ]; then \
		command -v docker >/dev/null || (echo "Docker is required on host"; exit 1); \
		docker compose version; \
	fi
	@command -v uv >/dev/null || (echo "uv is required for isolated Python tooling"; exit 1)
	@command -v java >/dev/null || (echo "Java 25+ is required"; exit 1)
	@java -version
	@$(GRADLE) --version

sync: ## Install and update the Python virtual environment from uv.lock
	@uv sync --frozen --no-build

bootstrap: doctor ## Resolve the Gradle wrapper and verify the scaffold
	@$(GRADLE) help

validate: contracts compose-config ## Run dependency-light repository checks
	@$(GRADLE) test

contracts: ## Validate contract JSON, GraphQL declarations, and task links
	@$(UV_RUN) python tools/contracts/validate.py
	@$(UV_RUN) python tools/contracts/validate_public_surface.py
	@$(UV_RUN) python tools/errors/validate_six_digit_catalog.py
	@$(UV_RUN) python tools/contracts/validate_openapi_parity.py
	@$(UV_RUN) python tools/contracts/detect_breaking_error_changes.py

python-typecheck: ## Run the strict repository Python type checker
	@$(UV_RUN) mypy tests tools

error-hygiene: ## Enforce governed error-path static policy
	@$(UV_RUN) python tools/qa/check_error_hygiene.py

docs-diagrams-config: ## Validate the Mermaid renderer Compose file
	@$(COMPOSE) -f infra/docs/docker-compose.yml config --quiet

docs-diagrams: docs-diagrams-config ## Render Mermaid source diagrams to ignored SVG outputs
	@$(COMPOSE) -f infra/docs/docker-compose.yml run --build --rm mermaid

redis-status: ## Show the local Redis dependency status
	@$(COMPOSE) -f $(DEV_COMPOSE) ps redis

redis-logs: ## Show recent local Redis logs
	@$(COMPOSE) -f $(DEV_COMPOSE) logs --tail=200 redis

redis-clear-rate-limit: ## Delete only the squarewise:rl:v1:* local limiter namespace
	@$(COMPOSE) -f $(DEV_COMPOSE) exec -T redis redis-cli -a "$${REDIS_PASSWORD:-squarewise-redis-local-only}" --no-auth-warning --scan --pattern 'squarewise:rl:v1:*' | while IFS= read -r key; do [ -z "$$key" ] || $(COMPOSE) -f $(DEV_COMPOSE) exec -T redis redis-cli -a "$${REDIS_PASSWORD:-squarewise-redis-local-only}" --no-auth-warning DEL "$$key" >/dev/null; done

generate-secrets: ## Generate cryptographically secure keys and passwords for CI and deployment
	@$(UV_RUN) python tools/ops/generate_secrets.py

lint: contracts ## Run contracts, compatible Spotless formatting baseline, and Gradle checks
	@$(GRADLE) spotlessCheck check


test: test-unit test-integration ## Run the complete JVM test suite

test-unit: ## Run fast unit tests
	@$(GRADLE) test --tests '*Test'

test-integration: ## Run Spring context and future Testcontainers tests
	@$(GRADLE) test

coverage: ## Run tests and produce JaCoCo XML/HTML reports
	@$(GRADLE) test jacocoTestReport

build: ## Compile all Kotlin and Java sources
	@$(GRADLE) compileKotlin compileJava

package: ## Build executable jars for every application
	@$(GRADLE) bootJar
	@$(UV_RUN) python -c "from pathlib import Path; [print(path) for path in Path('app').glob('*/build/libs/*.jar')]"

check: validate python-typecheck error-hygiene coverage package ## Validate, type-check, test, report coverage, and package

ci: contracts compose-config ## Run the hosted CI verification stages locally with parallel Gradle workers
	@$(GRADLE) test check jacocoTestReport bootJar --parallel --no-daemon

ci-e2e: ci e2e ## Run local CI verification plus the contract/deployment E2E smoke suite

acceptance: ## Run the public-interface acceptance harness and write a JSON report
	@bash tests/acceptance/run.sh

acceptance-live: ## Run the acceptance test harness requiring live running services
	@$(UV_RUN) python tests/acceptance/runner.py --require-services

bruno-run: ## Run the ordered Bruno collection; override BRUNO_ENV, BRUNO_TOKEN, negative tokens, and BRUNO_REPORT
	@command -v npx >/dev/null || (echo "Node.js/npm is required for Bruno CLI"; exit 1)
	@report="$${BRUNO_REPORT:-/tmp/squarewise-bruno-$$(date +%s).json}"; mkdir -p "$$(dirname "$$report")"; \
		(cd tools/bruno && npx --yes @usebruno/cli@4.1.0 run accounts bff expense-core/groups expense-core/expenses expense-core/settlements expense-core/sync expense-core/recurring notifications expense-core/lifecycle quality -r --env "$${BRUNO_ENV:-local}" --env-var "token=$${BRUNO_TOKEN:-test-user}" --env-var "wrongAudienceToken=$${BRUNO_WRONG_AUDIENCE_TOKEN:-$${SQUAREWISE_BRUNO_WRONG_AUDIENCE_TOKEN}}" --env-var "wrongIssuerToken=$${BRUNO_WRONG_ISSUER_TOKEN:-$${SQUAREWISE_BRUNO_WRONG_ISSUER_TOKEN}}" --env-var "expiredToken=$${BRUNO_EXPIRED_TOKEN:-$${SQUAREWISE_BRUNO_EXPIRED_TOKEN}}" --reporter-json "$$report" --reporter-skip-headers --reporter-skip-body); \
		echo "Bruno report: $$report"

workflow-validate: ## Parse all GitHub Actions workflow YAML files
	@$(UV_RUN) yamllint -d '{extends: relaxed, rules: {truthy: disable, line-length: disable}}' .github/workflows

observability-validate: ## Validate Prometheus rules and Grafana dashboard assets
	@$(UV_RUN) python -c "import yaml; from pathlib import Path; [yaml.safe_load(path.read_text(encoding='utf-8')) for path in (Path('infra/observability/prometheus.yml'), Path('infra/observability/rules/squarewise.yml'))]; print('valid observability YAML')"
	@$(UV_RUN) python -m json.tool infra/observability/grafana/dashboards/squarewise-overview.json >/dev/null
	@$(UV_RUN) python -c 'import json; d=json.load(open("infra/observability/grafana/dashboards/squarewise-overview.json")); assert d["panels"] and all(p["targets"] for p in d["panels"]); print("valid Grafana dashboard")'

release-gate: observability-validate ## Validate repository-owned production release prerequisites
	@$(UV_RUN) python tools/ops/validate_release_gate.py

security-hygiene: ## Scan tracked configuration and source for obvious secret material
	@$(UV_RUN) python tools/ops/check_security_hygiene.py

architecture-validate: ## Enforce application service dependency boundaries
	@$(UV_RUN) python tools/ops/check_architecture.py

sbom-validate: ## Validate centralized dependency-version baseline for SBOM generation
	@$(UV_RUN) python tools/ops/validate_sbom_baseline.py

rollout-validate: ## Validate the additive error-contract canary manifest and evidence ledger shape
	@$(UV_RUN) python tools/ops/validate_error_rollout.py

load-probe: ## Run an HTTP load probe; set URL, CONCURRENCY, and DURATION
	@test -n "$(URL)" || (echo "Set URL, e.g. make load-probe URL=http://localhost:28080/actuator/health"; exit 2)
	@$(UV_RUN) python tools/ops/http_load_probe.py "$(URL)" --concurrency "$${CONCURRENCY:-4}" --duration "$${DURATION:-10}"

load-k6-validate: ## Validate modular k6 scripts and endpoint tags
	@$(UV_RUN) python -c 'import pathlib; files=[*pathlib.Path("tests/load/k6").glob("*.js"), pathlib.Path("tests/performance/k6/error_storm_test.js")]; assert len(files) >= 7; assert all("options" in f.read_text() and "thresholds" in f.read_text() for f in files); print(f"valid k6 scripts: {len(files)}")'

load-k6: load-k6-validate ## Run one k6 script in Docker; set SCRIPT=tests/load/k6/accounts.js
	@test -n "$(SCRIPT)" || (echo "Set SCRIPT, e.g. make load-k6 SCRIPT=tests/load/k6/accounts.js"; exit 2)
	@docker run --rm -i --network squarewise-local-net -v "$(CURDIR):/work:ro" -e BASE_URL -e ACCOUNTS_URL -e EXPENSE_CORE_URL -e NOTIFICATIONS_URL -e BEARER_TOKEN grafana/k6 run "/work/$(SCRIPT)"

load-k6-error-storm: load-k6-validate ## Run the ERRC-26 baseline or storm scenario; set LOAD_MODE and BASELINE_VALID_P99_MS for storm mode
	@test -n "$(BEARER_TOKEN)" || (echo "Set BEARER_TOKEN to a signed test persona token"; exit 2)
	@docker run --rm -i --network squarewise-local-net -v "$(CURDIR):/work:ro" -e BASE_URL -e BEARER_TOKEN -e DURATION -e LOAD_MODE -e ERROR_RATE -e VALID_RATE -e BASELINE_VALID_P99_MS grafana/k6 run "/work/tests/performance/k6/error_storm_test.js"

load-k6-rate-limit: load-k6-validate ## Run the isolated GraphQL admission k6 scenario
	@k6 run tests/load/k6/rate-limit-graphql.js

load-mutation-check: load-k6-validate ## Run fixture-backed mutation load and verify financial reconciliation
	@DURATION="$${DURATION:-5s}" k6 run tests/load/k6/mutation-expense.js
	@$(UV_RUN) python tools/ops/reconcile_mutation_fixture.py --token "$${BEARER_TOKEN:-test-user}"

cqrs-replica-smoke: ## Verify local PostgreSQL streaming replica and route telemetry
	@sh tests/performance/cqrs-replica-smoke.sh

expense-search-plan: ## Capture the bounded Expense Core search plan on the local replica
	@sh tests/performance/expense-search-plan.sh

replica-disconnect-recovery: ## Drill safe local reader disconnect and recovery
	@sh tests/performance/replica-disconnect-recovery.sh

e2e: ## Run the contract and deployment E2E smoke checks
	@tests/e2e/contract-smoke.sh

e2e-rest-edge: ## Run live REST validation, authorization, boundary, and idempotency checks
	@$(UV_RUN) python tests/e2e/test_rest_edge_cases.py $(E2E_REST_EDGE_ARGS)

e2e-auth-email: ## Run deployed passwordless auth-email delivery and session-revocation checks
	@$(UV_RUN) python tests/e2e/test_auth_email_delivery.py $(E2E_AUTH_EMAIL_ARGS)

e2e-auth-notification-limit: ## Run deployed auth-email delivery admission suppression and recovery
	@$(UV_RUN) python tests/e2e/test_auth_notification_rate_limit.py

e2e-notification-general-limit: ## Run local general notification delivery admission through RabbitMQ management
	@SQUAREWISE_NOTIFICATIONS_DELIVERY_MAX_PERMITS=1 SQUAREWISE_NOTIFICATIONS_DELIVERY_WINDOW_SECONDS=5 $(UV_RUN) python tests/e2e/test_notification_general_rate_limit.py

e2e-auth-notification-outage: ## Run auth-email delivery Redis outage and recovery checks
	@$(UV_RUN) python tests/e2e/test_auth_notification_redis_outage.py

e2e-auth-login-replicas: ## Run shared login admission checks against two Accounts replicas
	@$(UV_RUN) python tests/e2e/test_auth_login_replicas.py

e2e-auth-refresh-concurrency: ## Run concurrent refresh rotation and family-revocation checks
	@$(UV_RUN) python tests/e2e/test_auth_refresh_concurrency.py

e2e-auth-cache: ## Run live Redis eviction, outage, and restart authentication checks
	@$(UV_RUN) python tests/e2e/test_auth_cache_resilience.py

e2e-auth-surfaces: ## Run live GraphQL HTTP and WebSocket rate-limit checks
	@$(UV_RUN) python tests/e2e/test_auth_rate_limit_surfaces.py

e2e-auth-bff-replicas: ## Verify BFF GraphQL admission across two processes sharing Redis
	@$(UV_RUN) python tests/e2e/test_auth_bff_replicas.py

e2e-auth-no-accounts: ## Prove authenticated resource reads do not call Accounts
	@$(UV_RUN) python tests/e2e/test_auth_no_accounts_lookup.py

e2e-auth-query-latency: ## Measure authenticated query latency and SQL isolation
	@$(UV_RUN) python tests/e2e/test_auth_query_latency.py

e2e-live: ## Run the comprehensive multi-service product journey E2E test against live stack
	@$(UV_RUN) python tests/e2e/test_product_journey.py $(E2E_PRODUCT_ARGS)

e2e-offline: ## Run offline client simulation, sync cursor, and replay resilience tests
	@$(UV_RUN) python tests/e2e/test_offline_resilience.py $(E2E_OFFLINE_ARGS)

e2e-concurrency: ## Run concurrent member edit conflict resolution and GraphQL subscription invalidation tests
	@$(UV_RUN) python tests/e2e/test_concurrency_subscriptions.py $(E2E_CONCURRENCY_ARGS)

e2e-chaos: ## Run message broker outage chaos and transactional outbox recovery tests
	@$(UV_RUN) python tests/e2e/test_chaos_recovery.py

e2e-all: e2e-auth-cache e2e-auth-surfaces e2e-auth-no-accounts e2e-auth-email e2e-live e2e-offline e2e-concurrency e2e-chaos ## Run the entire comprehensive E2E test suite against the live stack
	@echo "All E2E test suites passed successfully!"

smoke: validate e2e ## Run safe local smoke checks without starting containers

compose-config: deps-config accounts-deps-config expense-core-deps-config notifications-deps-config bff-deps-config full-config devcontainer-config ## Validate every local Compose topology

devcontainer-config: ## Validate .devcontainer/docker-compose.devcontainer.yml
	@$(COMPOSE) -f .devcontainer/docker-compose.devcontainer.yml config --quiet

deps-config: ## Validate dependency-only infra/local/docker-compose.yml
	@$(COMPOSE) -f $(LOCAL_COMPOSE) config --quiet

deps-up: ## Start PostgreSQL, RabbitMQ, and Mailpit from infra/local/docker-compose.yml
	@$(COMPOSE) -f $(LOCAL_COMPOSE) up -d --wait

deps-status: ## Show dependency-only services from infra/local/docker-compose.yml
	@$(COMPOSE) -f $(LOCAL_COMPOSE) ps

deps-logs: ## Follow dependency-only logs from infra/local/docker-compose.yml
	@$(COMPOSE) -f $(LOCAL_COMPOSE) logs -f

deps-down: ## Stop dependency-only services from infra/local/docker-compose.yml
	@$(COMPOSE) -f $(LOCAL_COMPOSE) down

accounts-deps-config: ## Validate native Accounts prerequisites in docker-compose.accounts.yml
	@$(COMPOSE) -f $(ACCOUNTS_COMPOSE) config --quiet

accounts-deps-up: ## Start native Accounts prerequisites from docker-compose.accounts.yml
	@$(COMPOSE) -f $(ACCOUNTS_COMPOSE) up -d --wait

accounts-deps-status: ## Show native Accounts prerequisites from docker-compose.accounts.yml
	@$(COMPOSE) -f $(ACCOUNTS_COMPOSE) ps

accounts-deps-logs: ## Follow native Accounts prerequisite logs from docker-compose.accounts.yml
	@$(COMPOSE) -f $(ACCOUNTS_COMPOSE) logs -f

accounts-deps-down: ## Stop native Accounts prerequisites from docker-compose.accounts.yml
	@$(COMPOSE) -f $(ACCOUNTS_COMPOSE) down

expense-core-deps-config: ## Validate native Expense Core prerequisites in docker-compose.expense-core.yml
	@$(COMPOSE) -f $(EXPENSE_CORE_COMPOSE) config --quiet

expense-core-deps-up: ## Start native Expense Core prerequisites from docker-compose.expense-core.yml
	@$(COMPOSE) -f $(EXPENSE_CORE_COMPOSE) up -d --wait

expense-core-deps-status: ## Show native Expense Core prerequisites from docker-compose.expense-core.yml
	@$(COMPOSE) -f $(EXPENSE_CORE_COMPOSE) ps

expense-core-deps-logs: ## Follow native Expense Core prerequisite logs from docker-compose.expense-core.yml
	@$(COMPOSE) -f $(EXPENSE_CORE_COMPOSE) logs -f

expense-core-deps-down: ## Stop native Expense Core prerequisites from docker-compose.expense-core.yml
	@$(COMPOSE) -f $(EXPENSE_CORE_COMPOSE) down

notifications-deps-config: ## Validate native Notifications prerequisites in docker-compose.notifications.yml
	@$(COMPOSE) -f $(NOTIFICATIONS_COMPOSE) config --quiet

notifications-deps-up: ## Start native Notifications prerequisites from docker-compose.notifications.yml
	@$(COMPOSE) -f $(NOTIFICATIONS_COMPOSE) up -d --wait

notifications-deps-status: ## Show native Notifications prerequisites from docker-compose.notifications.yml
	@$(COMPOSE) -f $(NOTIFICATIONS_COMPOSE) ps

notifications-deps-logs: ## Follow native Notifications prerequisite logs from docker-compose.notifications.yml
	@$(COMPOSE) -f $(NOTIFICATIONS_COMPOSE) logs -f

notifications-deps-down: ## Stop native Notifications prerequisites from docker-compose.notifications.yml
	@$(COMPOSE) -f $(NOTIFICATIONS_COMPOSE) down

bff-deps-config: ## Validate native BFF upstreams in docker-compose.bff.yml
	@$(COMPOSE) -f $(BFF_COMPOSE) config --quiet

bff-deps-up: ## Start Accounts and Expense Core upstreams for a native BFF
	@$(COMPOSE) -f $(BFF_COMPOSE) up -d --build --wait

bff-deps-status: ## Show native BFF upstreams from docker-compose.bff.yml
	@$(COMPOSE) -f $(BFF_COMPOSE) ps

bff-deps-logs: ## Follow native BFF upstream logs from docker-compose.bff.yml
	@$(COMPOSE) -f $(BFF_COMPOSE) logs -f

bff-deps-down: ## Stop native BFF upstreams from docker-compose.bff.yml
	@$(COMPOSE) -f $(BFF_COMPOSE) down

full-config: ## Validate the complete stack in infra/local/docker-compose.dev.yml
	@$(COMPOSE) -f $(DEV_COMPOSE) config --quiet

full-up: ## Build and run all applications and dependencies from docker-compose.dev.yml
	@$(COMPOSE) -f $(DEV_COMPOSE) up --build

full-status: ## Show all applications and dependencies from docker-compose.dev.yml
	@$(COMPOSE) -f $(DEV_COMPOSE) ps

full-logs: ## Follow all application and dependency logs from docker-compose.dev.yml
	@$(COMPOSE) -f $(DEV_COMPOSE) logs -f

full-down: ## Stop all applications and dependencies from docker-compose.dev.yml
	@$(COMPOSE) -f $(DEV_COMPOSE) down

compose-dev-up: full-up ## Alias for full-up

compose-dev-down: full-down ## Alias for full-down

compose-dev-logs: full-logs ## Alias for full-logs

compose-up: compose-dev-up ## Alias for the development stack
compose-down: compose-dev-down ## Alias for stopping the development stack

dev-setup: ## Prepare local development configuration from .env.example
	@test -f infra/local/.env || cp infra/local/.env.example infra/local/.env
	@echo "Local development environment initialized: infra/local/.env ready"

seed: ## Seed realistic development personas, groups, expenses, and schedules
	@$(UV_RUN) python tools/ops/seed_dev_data.py

seed-large: ## Seed extensive high-volume development dataset (50+ groups, hundreds of expenses)
	@$(UV_RUN) python tools/ops/seed_dev_data.py --large

seed-reset: ## Reset groups, expenses, and notifications and re-seed clean development data
	@$(UV_RUN) python tools/ops/seed_dev_data.py --reset

docker-build-all: $(addprefix docker-build-,$(SERVICES)) ## Build all production JVM images

docker-build-%: ## Build one production image, e.g. make docker-build-accounts
	@test -n "$*" || (echo "Provide a service name"; exit 2)
	@docker build --build-arg APP_PROJECT=$* -f infra/docker/Dockerfile.jvm -t squarewise-$*:local .

DOCKER_FAST_TARGETS = $(addprefix docker-fast-,$(SERVICES))

docker-fast-all: package $(DOCKER_FAST_TARGETS) ## Build all local runtime images quickly from host jars

$(DOCKER_FAST_TARGETS): docker-fast-%:
	@docker build --build-arg APP_PROJECT=$* -f infra/docker/Dockerfile.fast -t squarewise-$*:local .

prod-config: ## Validate production Compose using image variables from the environment
	@test -n "$(SQUAREWISE_ACCOUNTS_IMAGE)" || (echo "Set SQUAREWISE_ACCOUNTS_IMAGE"; exit 2)
	@test -n "$(SQUAREWISE_EXPENSE_CORE_IMAGE)" || (echo "Set SQUAREWISE_EXPENSE_CORE_IMAGE"; exit 2)
	@test -n "$(SQUAREWISE_NOTIFICATIONS_IMAGE)" || (echo "Set SQUAREWISE_NOTIFICATIONS_IMAGE"; exit 2)
	@test -n "$(SQUAREWISE_BFF_IMAGE)" || (echo "Set SQUAREWISE_BFF_IMAGE"; exit 2)
	@$(COMPOSE) -f $(PROD_COMPOSE) config --quiet

staging-config: ## Validate the production-like staging overlay
	@$(COMPOSE) -f $(PROD_COMPOSE) -f infra/deploy/docker-compose.staging.yml config --quiet

clean: ## Remove generated Gradle/build outputs
	@$(GRADLE) clean

clean-gradle: ## Stop Gradle daemons and remove generated outputs
	@$(GRADLE) --stop || true
	@$(GRADLE) clean

status: ## Show Git state and recent commits
	@git status --short
	@git log --oneline -5
