# DOC-27 - Establish OSS community health and reporting policies

## Objective

Bring Squarewise's contributor-facing repository metadata in line with GitHub's
recommended community standards. Provide clear conduct, licensing, security,
issue, pull-request, and accessibility guidance without overstating the
project's current product or release maturity.

## Dependencies

- `DOC-08` - documentation gate and link validation
- `DOC-24` - documentation linking and pre-implementation review conventions

## Owned paths

- `README.md`
- `CONTRIBUTING.md`
- `LICENSE`
- `SECURITY.md`
- `CODE_OF_CONDUCT.md`
- `ACCESSIBILITY.md`
- `.github/ISSUE_TEMPLATE/`
- `.github/pull_request_template.md`
- `docs/README.md`
- `docs/tasks/details/DOC-27.md`
- `docs/tasks/registry.yaml`
- `docs/tasks/board.md`
- `docs/tasks/progress.md`

## Acceptance criteria

1. The repository contains GitHub-recognized Code of Conduct, MIT license,
   security policy, accessibility statement, issue forms, chooser
   configuration, and pull-request template files in supported locations.
2. `security@subhrodip.com` is the only documented destination for security
   questions and vulnerability reports; public issues are explicitly
   prohibited for suspected vulnerabilities.
3. The security policy states the currently supported version model, expected
   report contents, acknowledgement expectations, coordinated-disclosure
   rules, and safe-harbor intent without promising an unverified response SLA.
4. The accessibility statement distinguishes repository/documentation access
   from the deferred product UI, records priorities and known limitations, and
   provides a public barrier-reporting route with a confidential alternative
   when sensitive information is involved.
5. Contributor documentation and templates link to the authoritative policies
   instead of duplicating them, and issue forms gather actionable reproduction,
   scope, environment, acceptance, and validation information.
6. Documentation, JSON, YAML, and local-link validation pass; the final diff is
   limited to the declared paths and contains no credentials or generated
   artifacts.

## Validation commands

```text
uv run --frozen --no-build python tools/contracts/validate.py
uv run --frozen --no-build yamllint -d "{extends: relaxed, rules: {truthy: disable, line-length: disable}}" .github/ISSUE_TEMPLATE
git diff --check
```

## Research and placement decisions

- GitHub's [community profile guidance][community-profile] checks for a README,
  license, Code of Conduct, contributing guide, security policy, and valid issue
  templates. GitHub separately recognizes an accessibility page as a community
  health file.
- Root-level community documents maximize clone-time visibility and are
  recognized by GitHub's community profile. `LICENSE` must remain repository
  local; GitHub's [default community file guidance][default-files] states that
  licenses are not inherited.
- Issue forms and their chooser configuration belong in
  `.github/ISSUE_TEMPLATE/` under GitHub's [issue-template
  rules][issue-templates]; the single default pull-request template belongs at
  `.github/pull_request_template.md`, a [supported pull-request template
  location][pull-request-template].
- The Code of Conduct uses Contributor Covenant 2.1 with project-specific
  enforcement contact information and retained attribution, following GitHub's
  [Code of Conduct guidance][code-of-conduct].
- `ACCESSIBILITY.md` is kept at the repository root so GitHub can surface its
  dedicated Accessibility tab and contributors can find it in a clone, as
  described by GitHub's [accessibility-page guidance][accessibility-page].
- `SECURITY.md` documents supported versions and private reporting instructions,
  the two fields GitHub specifically calls for in its [security-policy
  guidance][security-policy].

## Implementation notes and limitations

Implemented the six requested community-health concerns with one authoritative
document per policy and short cross-links from `README.md`, `CONTRIBUTING.md`,
and `docs/README.md`. The issue forms are tailored to the repository's
documentation-first task model and collect reproduction, environment, scope,
compatibility, security, and accessibility information without relying on
labels that may not exist in the hosted repository.

The accessibility statement does not claim conformance for the deferred
`app/web/` user interface. GitHub repository settings, including private
vulnerability reporting and label creation, are outside the local commit and
were not represented as locally verified. The templates become active only
after they reach the repository's default branch.

## Validation evidence

- `uv run --frozen --no-build python tools/contracts/validate.py` passed: all
  contract JSON, GraphQL declarations, and 220 registry tasks are valid.
- `uv run --frozen --no-build yamllint -d '{extends: relaxed, rules: {truthy:
  disable, line-length: disable}}' .github/ISSUE_TEMPLATE` passed with no
  findings.
- A YAML load and explicit structure assertion passed for `accessibility.yml`,
  `bug_report.yml`, `config.yml`, and `feature_request.yml`.
- A read-only local-link check passed for all eight touched Markdown entry
  points.
- The contributor-document contact audit found no competing security-reporting
  destination; non-project addresses are documented fixtures.
- `git diff --check` passed.

[community-profile]: https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/about-community-profiles-for-public-repositories
[default-files]: https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/creating-a-default-community-health-file
[issue-templates]: https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/configuring-issue-templates-for-your-repository
[pull-request-template]: https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/creating-a-pull-request-template-for-your-repository
[code-of-conduct]: https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/adding-a-code-of-conduct-to-your-project
[accessibility-page]: https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/adding-an-accessibility-page-to-your-repository
[security-policy]: https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/configure-vulnerability-reporting/add-security-policy
