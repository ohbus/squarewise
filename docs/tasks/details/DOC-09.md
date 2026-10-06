# DOC-09: Mermaid visual documentation

## Objective

Provide maintainable high-level, low-level, and component diagrams that explain
the Squarewise boundaries and can be rendered reproducibly without installing a
Node toolchain on the host.

## Deliverables

- `docs/visuals/high-level-architecture.mmd`: system context and deployment flow.
- `docs/visuals/low-level-expense-flow.mmd`: request, transaction, outbox, and
  asynchronous delivery sequence.
- `docs/visuals/component-boundaries.mmd`: service and library component
  boundaries, ownership, and forbidden cross-service persistence access.
- `docs/visuals/README.md`: edit conventions, source links, and render commands.
- `infra/docs/Dockerfile` and `infra/docs/docker-compose.yml`: pinned Mermaid CLI
  renderer with mounted source/output directories.

## Acceptance criteria

- Every diagram is valid Mermaid source and links to the relevant architecture,
  contract, and task documents.
- `make docs-diagrams` renders SVG output into the ignored `build/docs/diagrams/`
  directory.
- Source diagrams remain the reviewed artifacts; generated SVG files are not
  committed.

## Verification

- `docker compose -f infra/docs/docker-compose.yml config --quiet`
- `make docs-diagrams`
- `python3 tools/contracts/validate.py`
