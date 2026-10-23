# Task tracking

Squarewise is migrating live execution management to GitHub Issues and the
`Squarewise Delivery` project. The migration deliberately separates mutable
delivery state from durable engineering evidence.

## Authority by concern

| Concern | Authority after cutover |
| --- | --- |
| Status, assignee, priority, iteration, milestone, blocker, target date | GitHub issue and project |
| Objective, owned paths, acceptance criteria, validation commands | Task detail under `details/` |
| Migrated historical identifier and specification link | `registry.yaml` |
| Commands run, results, limitations, commit hashes | `progress.md` |
| External API behavior | `contracts/` |

The top-level `execution_tracking` object in `registry.yaml` records the cutover
state and deprecated-alias mapping for migrated issues. While `cutover_status`
is not `complete`, the registry and board remain the fallback for mutable
execution state. After the coordinator records the Project URL and marks the
cutover complete, agents must read live state from the GitHub issue and Project.

## GitHub workflow

The Project uses five statuses: Backlog, Ready, In progress, In review, and Done.
Triage is a label and saved view, not another status. Rejected work uses GitHub's
`not planned` close reason.

The delivery board uses Status columns and a multi-select Workstream field, so
cross-cutting items can appear in every relevant stream. Repository `area:*`
labels are not used because they would duplicate Project state. The Backlog
table provides the Jira-style overview of Backlog and Ready work with native
parent/sub-issue hierarchy. The board does not enforce WIP limits. Native
blocked-by relationships, or a `blocked` label for external blockers, keep
stalled work visible.

Large outcomes use parent epic issues, executable work uses child issues, and
GitHub blocking relationships represent active dependencies. Pull requests link
their issue with `Closes` only when the complete acceptance criteria are met;
partial increments use `Refs`.

## Migration boundary

The 251 tasks completed before DOC-28 remain repository history and are not
recreated as closed GitHub issues. Their local IDs and the IDs of work migrated
during DOC-28 are deprecated aliases retained only for traceability. New work
uses its GitHub issue number and issue body and does not allocate a local task
ID. No two-way status synchronization service is used.
