# DOC-28 - Establish GitHub-native delivery management

## Objective

Make GitHub Issues and the `Squarewise Delivery` project the authoritative
source for mutable execution state while retaining repository documents for
durable task specifications, acceptance criteria, validation, and evidence.

## Dependencies

- `DOC-27` - OSS community health and reporting policies
- `DOC-08` - documentation gate and registry validation

## Owned paths

- `.github/`
- `tools/ci/required_ci_gate.py`
- `tests/tools/test_required_ci_gate.py`
- `AGENTS.md`
- `CONTRIBUTING.md`
- `docs/working-agreement.md`
- `docs/operations/ci.md`
- `docs/tasks/README.md`
- `docs/tasks/github-project-setup.md`
- `docs/tasks/registry.yaml`
- `docs/tasks/board.md`
- `docs/tasks/progress.md`
- `docs/tasks/details/DOC-28.md`

## Acceptance criteria

1. GitHub Issues and the linked `Squarewise Delivery` project own live status,
   assignment, priority, iteration, milestone, blockers, and scheduling.
2. The project uses Backlog, Ready, In progress, In review, Waiting, and Done;
   its delivery board uses Status columns and a multi-select Workstream field.
3. Three active-work epics, the ten tasks incomplete at migration start, and
   DOC-28 are created as linked GitHub issues without importing the 251
   completed tasks.
4. Issue forms apply the repository label taxonomy and provide a maintainer task
   form for objective, dependencies, owned paths, acceptance, and validation.
5. Dependabot monitors Gradle, GitHub Actions, uv, and Docker manifests on one
   bounded weekly schedule.
6. One stable required CI result aggregates selected verification jobs, and the
   default-branch ruleset requires it together with reviewed, resolved PRs.
7. Repository guidance names one authority per concern and preserves durable
   task specifications and evidence without a bidirectional synchronization bot.

## Validation commands

```text
uv run --frozen --no-build python tools/contracts/validate.py
uv run --frozen --no-build python tools/contracts/validate_public_surface.py
uv run --frozen --no-build yamllint -d "{extends: relaxed, rules: {truthy: disable, line-length: disable}}" .github
make workflow-validate
make python-typecheck
python3 -m unittest tests.tools.test_required_ci_gate
git diff --check
```

## Implementation notes and limitations

- The existing registry remains the durable specification and historical
  evidence index during cutover. Mutable fields on migrated tasks are snapshots,
  not a second execution authority.
- GitHub administration capabilities exposed to this session do not include
  creating Projects, labels, milestones, or editing repository rulesets. Those
  hosted settings require an authenticated administrative client if the GitHub
  connector cannot perform them.
- No completed historical task is recreated as a closed issue.
- Hosted Project, label, milestone, native relationship, repository-setting,
  and ruleset steps are specified in `docs/tasks/github-project-setup.md`.
- GitHub epics #20-#22 and task issues #23-#33 are created. Body links carry
  hierarchy and blocking semantics until the owner converts them into native
  relationships.
- The public `Squarewise Delivery` project exists at
  `https://github.com/users/ohbus/projects/4`; its fields, views, workflows, and
  item migration remain part of the hosted setup.
- Local task IDs are deprecated migration aliases. New work uses GitHub issue
  numbers and issue bodies. The live Project supports a multi-select Workstream
  field, superseding the earlier label-only fallback.
- Native parent/sub-issue relationships are verified for all eleven migrated
  tasks: epics #20, #21, and #22 report 4, 5, and 2 children respectively.
  Redundant body checklists and `Parent epic` lines were removed.
- Local contract and public-surface validation, GitHub YAML lint, workflow
  validation, Python type checking, three aggregate-gate unit tests, and
  whitespace validation pass. Hosted `Required CI` evidence awaits this commit.

## Ownership handoff

DOC-28 coordinates the stable required-check addition with active tasks OPS-32
and AUTH-09, which already own the reusable workflow. The change is limited to
one aggregate job and its typed validator; their changed-scope and E2E selection
behavior remains owned by those tasks.
