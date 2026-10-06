# ERRC-01A: Audit Kotlin error throw, catch, and boundary behavior

Read every relevant production Kotlin error declaration, throw site, catch
block, REST advice, GraphQL resolver, message listener, scheduler, configuration
guard, and associated test. Report findings to the coordinator; make no files or
application changes.

The audit must identify current custom exceptions, deliberate generic throws,
third-party failures requiring translation, unsafe response detail paths,
over-broad or under-broad catches, interruption/cancellation handling, and a
complete migration family list with file references.

## Findings delivered

- Counted 128 production `ApplicationException(ErrorCode...)` constructions across
  Accounts (30), BFF (4), Expense Core (87), and Notifications (7), plus 181 explicit
  throws, 184 `require` calls, 53 catches, and nine REST handlers requiring migration.
- Found response-detail exposure through malformed-body, binding, conversion,
  optimistic-lock, broad `IllegalArgumentException`, and application-exception paths;
  existing tests currently encode some unsafe text.
- Identified four messaging `catch (Throwable)` sites that can swallow fatal JVM
  failures and inconsistent retry/poison/acknowledgement classification.
- Identified security-filter paths outside controller advice, BFF upstream identity
  loss, English-message matching for subscription behavior, scheduled-boundary gaps,
  and concrete semantic mapping inconsistencies.
- Delivered the bounded-context/platform family inventory and recommendation for one
  minimal governed base plus useful leaf types or typed outcomes, not a class per code.

This was a read-only audit. The coordinator incorporated the findings into commit
`33e5cea`; no production file was changed by this child task.
