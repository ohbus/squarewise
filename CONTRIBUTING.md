# Contributing to Squarewise

Thank you for your interest in contributing to Squarewise! We welcome contributions from the community.
Squarewise is a documentation-first, tracker-driven, modular Kotlin/Spring application. To ensure high code quality, clean architecture, and maintainable operations, all contributors follow the guidelines outlined below.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How to Contribute](#how-to-contribute)
  - [Reporting Bugs & Security Issues](#reporting-bugs--security-issues)
  - [Suggesting Enhancements](#suggesting-enhancements)
  - [Working Agreement & Task Registry](#working-agreement--task-registry)
- [Development Setup](#development-setup)
  - [Prerequisites](#prerequisites)
  - [Setting Up Your Local Environment](#setting-up-your-local-environment)
- [Development Workflow](#development-workflow)
  - [1. Branching Strategy](#1-branching-strategy)
  - [2. Pre-Implementation Planning](#2-pre-implementation-planning)
  - [3. Implementing Code](#3-implementing-code)
  - [4. Testing & Validation](#4-testing--validation)
  - [5. Committing Changes](#5-committing-changes)
  - [6. Submitting a Pull Request](#6-submitting-a-pull-request)
- [Architecture & Design Principles](#architecture--design-principles)
  - [SOLID File Separation](#solid-file-separation)
  - [API Endpoints & Contracts](#api-endpoints--contracts)
  - [KDoc Documentation Requirement](#kdoc-documentation-requirement)
- [Helpful Commands Cheat Sheet](#helpful-commands-cheat-sheet)

---

## Code of Conduct

Participation in Squarewise is governed by the [Contributor Covenant Code of
Conduct](CODE_OF_CONDUCT.md). Read it before contributing. Report conduct
incidents through its private enforcement channel rather than in a public issue.

---

## How to Contribute

### Reporting Bugs & Security Issues

- **Bugs**: Use the [bug report
  form](https://github.com/ohbus/squarewise/issues/new?template=bug_report.yml)
  and include the unexpected behavior, minimal reproduction, expected outcome,
  environment, and sanitized evidence.
- **Accessibility barriers**: Use the [accessibility report
  form](https://github.com/ohbus/squarewise/issues/new?template=accessibility.yml)
  and follow the [Accessibility Statement](ACCESSIBILITY.md).
- **Security questions and vulnerabilities**: Do **not** file a public issue,
  discussion, or pull request. Send every security question and suspected
  vulnerability privately to
  [security@subhrodip.com](mailto:security+squarewise@subhrodip.com) and follow the
  [Security Policy](SECURITY.md).

### Suggesting Enhancements
Before building a major new capability or changing service boundaries:
1. Review [`docs/product/mvp.md`](docs/product/mvp.md) and [`docs/architecture/`](docs/architecture/).
2. Use the [feature request
   form](https://github.com/ohbus/squarewise/issues/new?template=feature_request.yml)
   to outline its motivation, desired outcome, design trade-offs, and boundary
   implications.

### Working Agreement & Task Registry
Squarewise operates under a strict documentation-first workflow:
- The authoritative task registry lives in [`docs/tasks/registry.yaml`](docs/tasks/registry.yaml).
- The current work queue is visible in [`docs/tasks/board.md`](docs/tasks/board.md).
- Verified implementation evidence is logged in [`docs/tasks/progress.md`](docs/tasks/progress.md).
- Read [`docs/working-agreement.md`](docs/working-agreement.md) for full context on our execution protocol.

---

## Development Setup

### Prerequisites
- **JDK 25+**: Squarewise uses modern Kotlin and JVM 25 features.
- **Docker & Docker Compose v2**: For PostgreSQL 17, RabbitMQ 4.3, and Mailpit.
- **Python 3.11+**: Used for contract and schema validation tools.
- **Gradle**: Provided via `./gradlew` wrapper (no separate installation required).

Verify your environment using our self-check tool:
```sh
make doctor
```

### Setting Up Your Local Environment

1. **Clone the repository**:
   ```sh
   git clone https://github.com/ohbus/squarewise.git
   cd squarewise
   ```

2. **Verify prerequisites and build toolchain**:
   ```sh
   make bootstrap
   ```

3. **Start local infrastructure (choose one approach)**:
   - **Background dependencies only** (Postgres, RabbitMQ, Mailpit) when running apps natively via IDE/Gradle:
     ```sh
     make deps-up
     ```
   - **Full containerized stack** (all 4 apps + dependencies):
     ```sh
     make full-up
     ```

---

## Development Workflow

### 1. Branching Strategy
- Always branch off `master`.
- Use descriptive branch names with clear prefixes:
  - `feat/<feature-name>` for new features
  - `fix/<bug-fix-name>` for bug fixes
  - `refactor/<refactor-scope>` for refactoring
  - `docs/<doc-update>` for documentation updates

```sh
git checkout master
git pull origin master
git checkout -b feat/my-new-feature
```

### 2. Pre-Implementation Planning
Before writing code:
- Check existing technical libraries (`libs/ids`, `libs/errors`, `libs/db`, etc.) to maximize code reuse.
- Ensure any API modifications match contracts in `contracts/`.
- Review relevant architecture specifications in `docs/architecture/`.

### 3. Implementing Code
- Keep code cognitively light and easy to read.
- Write minimal, modular, cohesive code.
- Avoid introducing speculative abstractions (YAGNI).
- Strictly adhere to the [Design Principles](#architecture--design-principles).

### 4. Testing & Validation
All changes must be thoroughly validated before committing:

```sh
# Run fast unit tests
make test-unit

# Run full test suite with code coverage reports
make coverage

# Validate OpenAPI, GraphQL, and JSON schemas
make contracts

# Run linting (Spotless & Gradle checks)
make lint

# Validate full suite (contracts, tests, coverage, jar packaging)
make check
```

If the development stack is running, you can also run the live multi-service acceptance tests:
```sh
make acceptance-live
```

### 5. Committing Changes
- Write concise, descriptive commit messages following the Conventional Commits specification:
  - `feat(...)`: new functionality
  - `fix(...)`: bug fix
  - `refactor(...)`: refactoring without functional changes
  - `test(...)`: adding or modifying tests
  - `docs(...)`: documentation changes
- Never commit `.env` files, credentials, build artifacts, or IDE-specific project files.
- Ensure `git diff --check` produces no trailing whitespace or formatting warnings.

```sh
git add <files>
git commit -m "feat(expense-core): implement recurring expense calculator"
```

### 6. Submitting a Pull Request
1. Push your branch to GitHub:
   ```sh
   git push origin feat/my-new-feature
   ```
2. Open a Pull Request targeting `master`.
3. Complete the automatically loaded PR template with:
   - **Objective**: What problem does this PR solve?
   - **Changes**: Bulleted summary of changes made.
   - **Verification**: Exact test/validation commands executed and their output.
   - **Documentation**: Confirmation of updated docs/KDocs.
4. Ensure all CI checks pass.

Pull requests must not contain undisclosed vulnerability details. Send security
questions and reports to [security@subhrodip.com](mailto:security+squarewise@subhrodip.com)
under the [Security Policy](SECURITY.md).

---

## Architecture & Design Principles

All contributions must follow [`docs/quality/programming-principles.md`](docs/quality/programming-principles.md) and [`docs/quality/coding-guidelines.md`](docs/quality/coding-guidelines.md).

### SOLID File Separation
In enterprise code, each class or interface must reside in its own dedicated source file:
- **Never** combine JPA `@Entity` definitions, Spring Data `@Repository` interfaces, and business `@Service` or store adapter implementations in a single file.
- Follow enterprise packaging (`entities/`, `repositories/`, `services/`, etc.).

### API Endpoints & Contracts
- **Centralized API Constants**: Hardcoded URL strings and standard headers (`X-Request-Id`, `Idempotency-Key`) are prohibited in controllers, gateways, and tests. Always reference `com.subhrodip.squarewise.ids.ApiEndpoints` (e.g. `ApiEndpoints.ExpenseCore.V1.PATH_GROUPS`).
- **Contracts First**: Any change to REST or GraphQL contracts must be reflected in `contracts/` and verified with `make contracts`.

### KDoc Documentation Requirement
- Ensure all public classes, interfaces, methods, models, and endpoints include structured KDoc comments (`/** ... */`) detailing intent, parameters, return values, invariants, and failure modes.

---

## Helpful Commands Cheat Sheet

| Task | Command |
|---|---|
| View all available commands | `make help` |
| Check environment tools | `make doctor` |
| Start backing services | `make deps-up` |
| Stop backing services | `make deps-down` |
| Start complete container stack | `make full-up` |
| Stop complete container stack | `make full-down` |
| Validate OpenAPI & GraphQL schemas | `make contracts` |
| Run fast unit tests | `make test-unit` |
| Run all tests with JaCoCo coverage | `make coverage` |
| Run linting & formatting checks | `make lint` |
| Comprehensive check (CI mirror) | `make check` |
| Run live multi-service acceptance test | `make acceptance-live` |
