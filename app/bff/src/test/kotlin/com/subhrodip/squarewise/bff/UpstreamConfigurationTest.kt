package com.subhrodip.squarewise.bff

import com.subhrodip.squarewise.bff.transport.ExpenseCoreGateway
import com.subhrodip.squarewise.bff.transport.AccountsGateway
import com.subhrodip.squarewise.bff.messaging.model.BffEventEnvelope
import com.subhrodip.squarewise.bff.messaging.model.ConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.DuplicateConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.ProcessedConsumptionResult
import com.subhrodip.squarewise.bff.messaging.service.BffEventConsumer
import com.subhrodip.squarewise.bff.messaging.persistence.BffEventDeduplicator
import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout

import com.subhrodip.squarewise.bff.config.UpstreamConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class UpstreamConfigurationTest {
    @Test
    fun `rejects missing deployed downstream addresses`() {
        assertThrows<IllegalArgumentException> { UpstreamConfiguration("", "http://expense-core") }
        assertThrows<IllegalArgumentException> { UpstreamConfiguration("http://accounts", "") }
    }

    @Test
    fun `accepts explicit deployed downstream addresses`() {
        assertDoesNotThrow { UpstreamConfiguration("http://accounts", "http://expense-core") }
    }
}
