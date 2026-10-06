package com.subhrodip.squarewise.db.config

import com.subhrodip.squarewise.observability.db.DbTelemetry
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Installs bounded SQL statement telemetry into the application's Hibernate factory. */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@ConditionalOnProperty(prefix = "squarewise.db", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class DbStatementTelemetryConfiguration {
    /** Creates the shared database telemetry object when routed DB auto-configuration is absent. */
    @Bean
    fun defaultDbTelemetry(registry: ObjectProvider<MeterRegistry>): DbTelemetry =
        DbTelemetry(registry.getIfAvailable())

    /** Adds the statement inspector without changing generated SQL or query results. */
    @Bean
    fun dbStatementInspectorCustomizer(telemetry: DbTelemetry): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { properties ->
            properties["hibernate.session_factory.statement_inspector"] = DbStatementInspector(telemetry)
        }
}
