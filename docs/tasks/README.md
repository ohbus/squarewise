# Task tracking

Squarewise is migrating live execution management to GitHub Issues and the
`Squarewise Delivery` project. The migration deliberately separates mutable
delivery state from durable engineering evidence.

## Authority by concern

| Concern | Authority after cutover |
| --- | --- |
| Status, assignee, priority, iteration, milestone, blocker, target date | GitHub issue and project |
| Objective, owned paths, acceptance criteria, validation commands | Task detail under `details/` |
| Stable task identifier and specification link | `registry.yaml` |
| Commands run, results, limitations, commit hashes | `progress.md` |
| External API behavior | `contracts/` |

The top-level `execution_tracking` object in `registry.yaml` records the cutover
state and active issue mapping. While `cutover_status` is not `complete`, the
registry and board remain the fallback for mutable execution state. After the
coordinator records the Project URL and marks the cutover complete, agents must
read live state from the mapped GitHub issue and Project.

## GitHub workflow

The Project uses six statuses: Backlog, Ready, In progress, In review, Waiting,
and Done. Triage is a label and saved view, not a seventh status. Rejected work
uses GitHub's `not planned` close reason.

The delivery board uses Status columns and groups horizontally by Workstream.
In progress and In review each have an informational limit of three items.
Waiting items require a blocker explanation and a next-review date.

Large outcomes use parent epic issues, executable work uses child issues, and
GitHub blocking relationships represent active dependencies. Pull requests link
their issue with `Closes` only when the complete acceptance criteria are met;
partial increments use `Refs`.

## Migration boundary

The 251 tasks completed before DOC-28 remain repository history and are not
recreated as closed GitHub issues. The active issue map contains the ten tasks
that were incomplete at migration start plus DOC-28 itself. No two-way status
synchronization service is used.
