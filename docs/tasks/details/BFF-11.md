# BFF-11: Expose and verify the GraphQL HTTP transport route

Make the BFF GraphQL HTTP endpoint available at the documented route in the
actual WebFlux application context. Verify schema loading, HTTP POST queries,
GraphQL error envelopes, and WebTestClient access using both application-context
and random-port bindings. Keep WebSocket subscription configuration aligned with
the route and document the chosen `spring.graphql.path`/handler setup.

Depends on BFF-07 and owns BFF application configuration, GraphQL transport
tests, and relevant operations/API documentation. This task unblocks BFF-09.
