package com.subhrodip.squarewise.security.ratelimit

/** Stable policy identifiers used by application boundaries and bounded metrics. */
object RateLimitPolicyIds {
    /** Passwordless login start/resend admission. */
    const val AUTH_LOGIN: String = "auth-login"

    /** Refresh-token rotation admission. */
    const val AUTH_REFRESH: String = "auth-refresh"

    /** Notification provider-dispatch admission. */
    const val NOTIFICATION_DELIVERY: String = "notification-delivery"

    /** GraphQL HTTP request admission. */
    const val GRAPHQL_HTTP: String = "graphql-http"

    /** GraphQL WebSocket handshake admission. */
    const val GRAPHQL_WEBSOCKET: String = "graphql-websocket"
}
