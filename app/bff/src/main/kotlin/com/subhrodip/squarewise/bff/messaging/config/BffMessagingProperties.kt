package com.subhrodip.squarewise.bff.messaging.config

import com.subhrodip.squarewise.ids.events.EventConstants
import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration properties controlling BFF replica event fanout. */
@ConfigurationProperties(prefix = "squarewise.bff.messaging")
data class BffMessagingProperties(
    var enabled: Boolean = false,
    var exchange: String = EventConstants.EVENTS_EXCHANGE,
    var routingKey: String = EventConstants.Routing.ALL_EVENTS,
    var deadLetterExchange: String = "squarewise.events.dlx",
    var deduplicatorCapacity: Int = 10_000
)
