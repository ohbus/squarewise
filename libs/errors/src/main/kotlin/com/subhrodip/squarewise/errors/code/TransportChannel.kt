package com.subhrodip.squarewise.errors.code

/** Transport or execution channel on which a definition may be published. */
enum class TransportChannel {
    /** HTTP REST response. */
    REST,

    /** GraphQL response error. */
    GRAPHQL,

    /** Authenticated WebSocket lifecycle or subscription event. */
    WEBSOCKET,

    /** Broker message or outbox processing. */
    MESSAGING,

    /** Scheduled background execution. */
    SCHEDULER,

    /** Application startup or shutdown. */
    STARTUP,
}
