# Security Policy

Squarewise welcomes responsible security research. Protecting user identity and
financial data takes priority over public issue tracking or rapid disclosure.

## Supported versions

Squarewise has not published a stable production release. Security fixes are
currently developed against the latest commit on the default branch; older
commits, forks, and locally modified deployments are not maintained release
lines. The repository's production-readiness documents remain authoritative
about launch status and must not be read as a warranty that any deployment is
production ready.

| Version | Supported |
| --- | --- |
| Latest default branch | Yes, on a best-effort basis |
| Tagged or packaged releases | None published |
| Older commits and forks | No |

## Report a vulnerability or ask a security question

Send all security questions and suspected vulnerability reports privately to
[security@subhrodip.com](mailto:security@subhrodip.com).

Do not open a public issue, discussion, or pull request for a suspected
vulnerability. Do not include access tokens, credentials, personal data,
production data, or exploit details in public channels. If email is unsuitable
for the sensitivity of a report, send only a minimal introduction and request a
safer exchange method; no project-specific public encryption key is currently
advertised.

Include as much of the following as is safe and relevant:

- Affected component, endpoint, commit, or configuration
- Vulnerability type and potential impact
- Reproduction steps or a minimal proof of concept
- Preconditions, environment, and required privileges
- Whether the issue is already public or known to others
- Suggested remediation or disclosure constraints, if any
- A safe way to contact you and your preferred credit

## What to expect

Maintainers will acknowledge and triage reports as capacity permits, may request
clarifying evidence, and will coordinate remediation and disclosure with the
reporter. Response and fix times depend on severity, reproducibility, affected
components, and maintainer availability; this volunteer project does not promise
a fixed service-level agreement.

Please keep the report confidential until maintainers confirm that a fix or
mitigation is available and agree on disclosure timing. When appropriate, the
project may use a GitHub Security Advisory to collaborate privately, request a
CVE, and publish remediation details. Public credit is optional and will follow
the reporter's preference.

## Research guidelines and safe-harbor intent

When investigating Squarewise:

- Test only systems and accounts you own or have explicit permission to use.
- Prefer local fixtures and development environments over public deployments.
- Stop if testing could expose personal data, degrade service, destroy data, or
  affect another person.
- Use the minimum access and data necessary to demonstrate the issue.
- Do not use social engineering, physical attacks, denial of service, spam, or
  automated scanning that materially degrades availability.
- Delete data obtained during research after the report is resolved.

The project will not pursue action against good-faith research that follows this
policy, avoids privacy and availability harm, and allows a reasonable opportunity
to remediate before disclosure. This statement does not authorize testing of
third-party services or waive rights those parties may have.

For non-security bugs and feature requests, use the repository's [issue
templates](https://github.com/ohbus/squarewise/issues/new/choose). For community
conduct incidents, follow the private process in the [Code of
Conduct](CODE_OF_CONDUCT.md).
