package com.subhrodip.squarewise.db.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import com.subhrodip.squarewise.db.health.DbReaderHealth
import com.subhrodip.squarewise.db.health.DbReaderHealthScheduler
import com.subhrodip.squarewise.db.web.DbCausalWatermarkFilter
import com.subhrodip.squarewise.observability.db.DbTelemetry
import javax.sql.DataSource
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.beans.factory.ObjectProvider
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.web.servlet.FilterRegistrationBean

/** Provides the writer/reader datasource topology when explicitly enabled. */
@AutoConfiguration
@EnableConfigurationProperties(DbProperties::class)
@EnableScheduling
@ConditionalOnProperty(prefix = "squarewise.db", name = ["enabled"], havingValue = "true")
class DbAutoConfiguration {
    /** Creates telemetry even when an application has no metrics registry, preserving diagnostics. */
    @Bean
    fun squarewiseDbTelemetry(registry: ObjectProvider<MeterRegistry>): DbTelemetry = DbTelemetry(registry.getIfAvailable())

    /** Installs the same bounded SQL statement telemetry for routed Hibernate sessions. */
    @Bean
    fun dbStatementInspectorCustomizer(telemetry: DbTelemetry): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { properties ->
            properties["hibernate.session_factory.statement_inspector"] = DbStatementInspector(telemetry)
        }
    /** Exposes the bounded scheduler interval to the scheduled probe expression. */
    @Bean(name = ["squarewiseDbHealthProbeIntervalMs"])
    fun squarewiseDbHealthProbeIntervalMs(properties: DbProperties): Long {
        require(properties.readerLagBudgetMs > 0) { "squarewise.db.reader-lag-budget-ms must be positive" }
        require(properties.healthProbeIntervalMs in 250..120_000) { "squarewise.db.health-probe-interval-ms is invalid" }
        return properties.healthProbeIntervalMs
    }
    /** Supplies the shared reader circuit state used by the routed datasource. */
    @Bean
    fun squarewiseReaderHealth(): DbReaderHealth = DbReaderHealth()

    /** Builds the dedicated writer pool selected by Flyway for migrations. */
    @Bean(name = ["flywayDataSource", "squarewiseWriterDataSource"])
    fun squarewiseWriterDataSource(properties: DbProperties): HikariDataSource {
        properties.writer.validate("writer")
        return buildDataSource(properties.writer, "writer")
    }

    /** Builds the routed datasource used by application JPA repositories. */
    @Bean
    @Primary
    fun squarewiseDataSource(
        properties: DbProperties,
        @Qualifier("flywayDataSource") writer: HikariDataSource,
        readerHealth: DbReaderHealth,
        telemetry: DbTelemetry
    ): DataSource {
        properties.writer.validate("writer")
        properties.readers.forEach { (name, pool) -> pool.validate("readers.$name") }
        val readers = if (properties.readerIsWriterDiagnostic) {
            properties.readers.keys.associateWith { writer }
        } else {
            properties.readers.mapValues { (name, pool) -> buildDataSource(pool, "reader.$name") }
        }
        return DbRoutingDataSource(writer, readers, readerHealth, telemetry)
    }

    /** Registers causal HTTP propagation only for stateful applications with routed DB access. */
    @Bean
    fun squarewiseCausalWatermarkFilter(dataSource: DataSource): FilterRegistrationBean<DbCausalWatermarkFilter> =
        FilterRegistrationBean(DbCausalWatermarkFilter(dataSource)).also { it.order = -100 }

    /** Creates a reader-only replay-lag scheduler; it never probes the writer. */
    @Bean
    fun squarewiseReaderHealthScheduler(
        properties: DbProperties,
        readerHealth: DbReaderHealth,
        dataSource: DataSource,
        telemetry: DbTelemetry
    ): DbReaderHealthScheduler {
        val readers = (dataSource as? DbRoutingDataSource)?.readerDataSources().orEmpty()
        return DbReaderHealthScheduler(readers, readerHealth, properties.readerLagBudgetMs, telemetry = telemetry)
    }

    private fun buildDataSource(properties: PoolProperties, poolName: String): HikariDataSource =
        DataSourceBuilder.create()
            .type(HikariDataSource::class.java)
            .url(properties.url)
            .username(properties.username)
            .password(properties.password)
            .build()
            .also {
                it.poolName = "squarewise-$poolName"
                it.maximumPoolSize = properties.maximumPoolSize
                it.connectionTimeout = properties.connectionTimeoutMs
                it.maxLifetime = properties.maxLifetimeMs
            }
}
