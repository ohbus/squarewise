# NOT-03: Durable inbox

Persist notification inbox events and event-id deduplication through a
service-local JPA/Flyway adapter while preserving cursor ordering.

Inbox events and processed event IDs are now durable with stable subject/cursor
ordering. Pagination currently materializes a subject inbox before slicing;
broker-consumer atomic effect wiring remains a later integration.
