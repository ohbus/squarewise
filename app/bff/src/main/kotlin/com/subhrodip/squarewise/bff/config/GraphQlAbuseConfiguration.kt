package com.subhrodip.squarewise.bff.config

import graphql.execution.instrumentation.Instrumentation
import com.subhrodip.squarewise.bff.graphql.GraphQlComplexityLimitInstrumentation
import com.subhrodip.squarewise.bff.graphql.GraphQlDepthLimitInstrumentation
import com.subhrodip.squarewise.bff.graphql.GraphQlLimitErrorInstrumentation
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Installs GraphQL Java depth and complexity guards before resolver execution. */
@Configuration
@EnableConfigurationProperties(
    GraphQlAbuseProperties::class,
    BrowserOriginProperties::class,
    BrowserSessionProperties::class
)
class GraphQlAbuseConfiguration {
    /** Rejects deeply nested GraphQL operations. */
    @Bean
    fun graphQlDepthInstrumentation(properties: GraphQlAbuseProperties): Instrumentation =
        GraphQlDepthLimitInstrumentation(properties.maxDepth)

    /** Rejects operations whose calculated field cost exceeds the configured bound. */
    @Bean
    fun graphQlComplexityInstrumentation(properties: GraphQlAbuseProperties): Instrumentation =
        GraphQlComplexityLimitInstrumentation(properties.maxComplexity)

    /** Adds the stable application code to query-bound rejection envelopes. */
    @Bean
    fun graphQlLimitErrorInstrumentation(): Instrumentation = GraphQlLimitErrorInstrumentation()
}
