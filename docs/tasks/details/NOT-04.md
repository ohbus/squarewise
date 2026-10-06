# NOT-04: Transactional notification consumption

Consume broker events so persistent deduplication and inbox effects commit in
one local transaction. Duplicate deliveries must not repeat effects, and failed
transactions must remain retryable.

The application-level consumer boundary is transactional and tested. A concrete
RabbitMQ listener and manual acknowledgement adapter remain pending until the
Spring AMQP dependency is centrally catalogued.
