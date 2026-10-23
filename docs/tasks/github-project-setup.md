# GitHub delivery setup

This runbook contains the hosted settings that cannot be represented by a
repository commit. It applies to `ohbus/squarewise` and the user-owned public
project `Squarewise Delivery`.

## Project

Create the Project with **Start from scratch → Board**, make it public, and link
the repository. Add these fields:

| Field | Type | Values |
| --- | --- | --- |
| Status | Single select | Backlog, Ready, In progress, In review, Done |
| Priority | Single select | P0, P1, P2, P3 |
| Workstream | Multi select | Accounts, Expense Core, Notifications, BFF, Platform, Operations, Security, Quality, Contracts, Docs |
| Size | Single select | XS, S, M, L |
| Iteration | Iteration | Two-week iterations created only when scheduled |
| Target date | Date | No default |

Do not create a Task ID field. Existing repository task IDs are deprecated
migration aliases, and new work uses its GitHub issue number. Select every
relevant Workstream value for cross-cutting work.

Configure views:

1. `Inbox`: table filtered to `label:needs-triage`.
2. `Backlog`: table filtered to `Status:Backlog,Ready`, grouped by Workstream,
   sorted by Priority, with parent/sub-issue hierarchy enabled.
3. `Delivery`: board with Status columns, grouped by Workstream; do not enforce
   column limits.
4. `Current iteration`: table filtered to the current iteration, grouped by
   assignee and sorted by Priority.
5. `Blocked`: table filtered to `label:blocked`.
6. `Roadmap`: roadmap using Iteration or Target date, grouped by milestone.
7. `Review queue`: table filtered to open pull requests.

Create saved table views for useful Workstream combinations when the delivery
board would otherwise be too dense.

Enable built-in workflows to add open issues and pull requests, set newly added
items to Backlog, set closed/merged items to Done, set reopened issues to Ready,
and archive Done items after fourteen days.

## Labels and milestone

Rename compatible default labels and create only this repository taxonomy:

- Type: `type:bug`, `type:feature`, `type:task`, `type:docs`, `type:epic`,
  `type:accessibility`.
- Intake/contribution: `needs-triage`, `needs-info`, `blocked`,
  `good first issue`, `help wanted`.
- Resolution: `duplicate`, `invalid`.

Do not create status, priority, or workstream labels; those values belong to the
Project. Create one milestone named `Production readiness` without a due date.

Apply `type:epic` to issues #20–#22 and `type:task` to issues #23–#33. Add
#20–#33 to the Project, then set Status and Workstream values there.

## Native hierarchy and dependencies

Convert the existing body links into native relationships:

- Parent #20: #23, #24, #25, #26.
- Parent #21: #28, #29, #30, #31, #32.
- Parent #22: #27, #33.
- #23 blocks #24, #25, and #26.
- #28 blocks #29; #29 blocks #30; #30 blocks #31; #31 blocks #32.

Assign the `Production readiness` milestone to #20, #21, and their current
children. Leave #22, #27, #32, and #33 without that milestone unless their
acceptance is explicitly added to the release gate.

## Repository and ruleset settings

Enable auto-merge, allow updating pull-request branches, and automatically
delete merged branches. Keep squash as the only merge method.

After the `Required CI` check has completed at least once on a pull request,
update the default-branch ruleset:

- Keep deletion and non-fast-forward protection.
- Require a pull request and one approval.
- Dismiss stale approvals after new commits.
- Require approval of the latest reviewable push.
- Require all review conversations to be resolved.
- Require the branch to be current before merge.
- Require `Pull request verification (reusable) / Required CI` from GitHub Actions.
- Change the owner's bypass mode from always to pull-request-only.

Do not enable a merge queue or require CODEOWNERS approval while the repository
has only one permanent maintainer.

## Cutover evidence

Record the Project URL in `registry.yaml.execution_tracking.project_url` and set
`cutover_status` to `complete` only after:

- all eleven mapped task issues and three epics are present in the Project;
- fields, views, automations, hierarchy, dependencies, labels, and milestone are
  configured;
- the stable required check has completed and the ruleset requires it; and
- a test issue demonstrates add, close-to-Done, reopen-to-Ready, and archival
  behavior.
