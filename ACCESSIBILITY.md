# Accessibility Statement

Squarewise aims to make its open-source project, documentation, contributor
workflows, and future user interfaces usable by as many people as possible,
including people who use assistive technologies.

## Current scope and status

The repository currently contains backend services, contracts, operational
tools, and Markdown documentation. The `app/web/` directory is reserved and no
end-user web interface has been implemented, so Squarewise does not claim
conformance for a product UI that does not yet exist.

The project has not completed an independent accessibility audit. Repository
content is primarily consumed through GitHub, local Markdown renderers, command
line tools, and IDEs; accessibility can therefore also depend on those tools.
Known gaps may include diagrams without complete text equivalents, complex data
tables, and developer workflows that have not been tested across a representative
assistive-technology matrix.

## Accessibility goals

For repository content and any future user interface, contributors should:

- Target WCAG 2.2 Level AA where the success criteria apply.
- Use semantic headings, meaningful link text, readable tables, and text
  alternatives for informative images and diagrams.
- Preserve complete keyboard operation, visible focus, logical focus order, and
  compatibility with common screen readers.
- Never rely on color, position, sound, or motion alone to convey meaning.
- Support zoom, text reflow, sufficient contrast, reduced motion, and clear
  validation and error identification.
- Add automated checks and manual keyboard/assistive-technology evidence when a
  user-facing interface is introduced or changed.

These are implementation and review goals, not a claim that every existing
artifact already conforms.

## Report an accessibility barrier

Use the [accessibility issue
form](https://github.com/ohbus/squarewise/issues/new?template=accessibility.yml)
for barriers that can be discussed publicly. Include the affected page or
workflow, what you expected, what happened, and, only if you are comfortable
sharing it, the browser, operating system, and assistive technology involved.
You do not need to disclose a disability or diagnosis.

For a private accessibility report, email
[accessibility@subhrodip.com](mailto:accessibility+pennywise@subhrodip.com).

If a barrier report contains a suspected vulnerability, personal data, or other
sensitive security information, do not open a public issue. Email
[security@subhrodip.com](mailto:security+pennywise@subhrodip.com) as required by the
[Security Policy](SECURITY.md).

Maintainers will triage reports as capacity permits, ask for clarification when
needed, and track actionable improvements in the repository. This volunteer
project does not promise a fixed response or remediation time.

## Contributing accessible changes

Before submitting a change, review the [contribution guide](CONTRIBUTING.md) and
describe accessibility impact and validation in the pull request. A change
should not knowingly regress an accessible interaction. If a limitation cannot
be resolved within the change, document it and open a follow-up issue with a
clear scope.

This statement will evolve as user-facing surfaces, supported environments, and
test evidence are added.
