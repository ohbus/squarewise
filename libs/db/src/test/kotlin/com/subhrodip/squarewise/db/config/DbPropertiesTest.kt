package com.subhrodip.squarewise.db.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies connection-pool configuration rejects unsafe incomplete settings. */
class DbPropertiesTest {
    private val validPool = PoolProperties(
        url = "jdbc:postgresql://writer/db",
        username = "app",
        maximumPoolSize = 10,
        connectionTimeoutMs = 2_000,
        maxLifetimeMs = 1_800_000,
    )

    @Test
    fun `configuration properties retain values supplied by binding`() {
        val pool = PoolProperties()
        pool.url = "jdbc:postgresql://reader/db"
        pool.username = "reader"
        pool.password = "secret"
        pool.maximumPoolSize = 12
        pool.connectionTimeoutMs = 3_000
        pool.maxLifetimeMs = 60_000

        val properties = DbProperties()
        properties.enabled = true
        properties.writer = pool
        properties.readers = mapOf("reader" to pool)
        properties.readerIsWriterDiagnostic = true
        properties.readerLagBudgetMs = 7_000
        properties.healthProbeIntervalMs = 4_000

        assertEquals("jdbc:postgresql://reader/db", properties.writer.url)
        assertEquals("reader", properties.writer.username)
        assertEquals("secret", properties.writer.password)
        assertEquals(12, properties.writer.maximumPoolSize)
        assertEquals(3_000, properties.writer.connectionTimeoutMs)
        assertEquals(60_000, properties.writer.maxLifetimeMs)
        assertEquals(pool, properties.readers["reader"])
        assertTrue(properties.enabled)
        assertTrue(properties.readerIsWriterDiagnostic)
        assertEquals(7_000, properties.readerLagBudgetMs)
        assertEquals(4_000, properties.healthProbeIntervalMs)
    }
    @Test
    fun `diagnostic reader alias is explicit and disabled by default`() {
        assertFalse(DbProperties().readerIsWriterDiagnostic)
        assertTrue(DbProperties(readerIsWriterDiagnostic = true).readerIsWriterDiagnostic)
    }

    @Test
    fun `writer endpoint is required`() {
        assertFailsWith<IllegalArgumentException> { PoolProperties().validate("writer") }
    }

    @Test
    fun `pool size is bounded`() {
        assertFailsWith<IllegalArgumentException> {
            PoolProperties(url = "jdbc:postgresql://writer/db", username = "app", maximumPoolSize = 201)
                .validate("writer")
        }
        assertFailsWith<IllegalArgumentException> { validPool.copy(maximumPoolSize = 0).validate("writer") }
    }

    /** Every required pool bound rejects values outside its documented safe range. */
    @Test
    fun `pool endpoint and timing bounds are enforced`() {
        assertFailsWith<IllegalArgumentException> { validPool.copy(username = "").validate("writer") }
        assertFailsWith<IllegalArgumentException> { validPool.copy(url = " ").validate("writer") }
        assertFailsWith<IllegalArgumentException> { validPool.copy(username = " ").validate("writer") }
        assertFailsWith<IllegalArgumentException> {
            validPool.copy(connectionTimeoutMs = 249).validate("writer")
        }
        assertFailsWith<IllegalArgumentException> {
            validPool.copy(connectionTimeoutMs = 120_001).validate("writer")
        }
        assertFailsWith<IllegalArgumentException> {
            validPool.copy(maxLifetimeMs = 29_999).validate("writer")
        }
        validPool.copy(maxLifetimeMs = 30_000).validate("writer")
        assertFailsWith<IllegalArgumentException> {
            validPool.copy(maxLifetimeMs = Long.MIN_VALUE).validate("writer")
        }
    }
}
