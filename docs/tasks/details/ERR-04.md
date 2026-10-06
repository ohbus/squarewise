# ERR-04: Accounts error migration

Replace generic or message-based failures in profile lookup/update, currency
validation, deletion, export, and batch lookup with cataloged typed errors.
Tests must assert exact code and metadata plus safe redaction and unchanged
transactional behavior on rejected requests.
