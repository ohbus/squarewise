package com.subhrodip.squarewise.db.config

import com.subhrodip.squarewise.observability.db.DbTelemetry
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider

/** Verifies the fallback Hibernate telemetry configuration independently of Spring discovery. */
class DbStatementTelemetryConfigurationTest {
    @Test
    fun `creates telemetry and statement inspector customizer`() {
        @Suppress("UNCHECKED_CAST")
        val provider = mock(ObjectProvider::class.java) as ObjectProvider<MeterRegistry>
        val configuration = DbStatementTelemetryConfiguration()
        val properties = mutableMapOf<String, Any>()

        assertEquals(DbTelemetry::class, configuration.defaultDbTelemetry(provider)::class)
        configuration.dbStatementInspectorCustomizer(DbTelemetry()).customize(properties)

        assertEquals(DbStatementInspector::class, properties["hibernate.session_factory.statement_inspector"]!!::class)
    }
}
