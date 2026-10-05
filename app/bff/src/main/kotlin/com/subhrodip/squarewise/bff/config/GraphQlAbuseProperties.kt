package com.subhrodip.squarewise.bff.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration bounds for GraphQL query cost and realtime subscriptions. */
@ConfigurationProperties(prefix = "squarewise.bff.graphql")
data class GraphQlAbuseProperties(
    var maxDepth: Int = 8,
    var maxComplexity: Int = 100,
    var maxHttpRequests: Int = 120,
    var httpWindowSeconds: Long = 60,
    var maxWebSocketConnections: Int = 20,
    var webSocketWindowSeconds: Long = 60,
    var maxSubscriptionsPerUser: Int = 20,
    var subscriptionQueueCapacity: Int = 64,
    var subscriptionTtlSeconds: Long = 1800
)
