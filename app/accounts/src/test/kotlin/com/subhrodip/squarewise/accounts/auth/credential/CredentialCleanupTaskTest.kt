package com.subhrodip.squarewise.accounts.auth.credential

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit test for [CredentialCleanupTask] utilizing dynamic reflection proxy. */
class CredentialCleanupTaskTest {

    @Test
    fun `executeCleanup invokes repository purge and returns count`() {
        var purgeInvoked = false
        var capturedExpiredCutoff: Instant? = null
        var capturedConsumedCutoff: Instant? = null

        val handler = InvocationHandler { _, method: Method, args: Array<out Any?>? ->
            when (method.name) {
                "purgeExpiredOrConsumed" -> {
                    purgeInvoked = true
                    capturedExpiredCutoff = args?.get(0) as? Instant
                    capturedConsumedCutoff = args?.get(1) as? Instant
                    42
                }
                "toString" -> "DynamicLoginCredentialRepositoryProxy"
                "hashCode" -> 1
                "equals" -> false
                else -> throw UnsupportedOperationException("Unexpected method invocation: ${method.name}")
            }
        }

        val proxyRepo = Proxy.newProxyInstance(
            LoginCredentialRepository::class.java.classLoader,
            arrayOf(LoginCredentialRepository::class.java),
            handler
        ) as LoginCredentialRepository

        val task = CredentialCleanupTask(proxyRepo, expiredRetentionDays = 7, consumedRetentionHours = 24)
        val count = task.executeCleanup()

        assertEquals(42, count)
        assertTrue(purgeInvoked)
        assertTrue(capturedExpiredCutoff != null)
        assertTrue(capturedConsumedCutoff != null)
    }

    @Test
    fun `executeCleanup returns zero without logging a purge`() {
        val handler = InvocationHandler { _, method: Method, _ ->
            when (method.name) {
                "purgeExpiredOrConsumed" -> 0
                "toString" -> "EmptyLoginCredentialRepositoryProxy"
                "hashCode" -> 2
                "equals" -> false
                else -> throw UnsupportedOperationException("Unexpected method invocation: ${method.name}")
            }
        }
        val proxyRepo = Proxy.newProxyInstance(
            LoginCredentialRepository::class.java.classLoader,
            arrayOf(LoginCredentialRepository::class.java),
            handler
        ) as LoginCredentialRepository

        assertEquals(
            0,
            CredentialCleanupTask(proxyRepo, expiredRetentionDays = 7, consumedRetentionHours = 24)
                .executeCleanup()
        )
    }

    @Test
    fun `executeCleanup drains backlog across multiple batches`() {
        var callCount = 0
        val handler = InvocationHandler { _, method: Method, args: Array<out Any?>? ->
            when (method.name) {
                "purgeExpiredOrConsumed" -> {
                    callCount++
                    val limit = args?.get(2) as Int
                    if (callCount < 3) limit else 17
                }
                "toString" -> "BatchDrainLoginCredentialRepositoryProxy"
                "hashCode" -> 3
                "equals" -> false
                else -> throw UnsupportedOperationException("Unexpected method invocation: ${method.name}")
            }
        }
        val proxyRepo = Proxy.newProxyInstance(
            LoginCredentialRepository::class.java.classLoader,
            arrayOf(LoginCredentialRepository::class.java),
            handler
        ) as LoginCredentialRepository

        val task = CredentialCleanupTask(proxyRepo, expiredRetentionDays = 7, consumedRetentionHours = 24, batchSize = 100, maxBatches = 5)
        val count = task.executeCleanup()

        assertEquals(217, count)
        assertEquals(3, callCount)
    }

    @Test
    fun `rejects invalid parameters`() {
        val proxyRepo = Proxy.newProxyInstance(
            LoginCredentialRepository::class.java.classLoader,
            arrayOf(LoginCredentialRepository::class.java)
        ) { _, _, _ -> null } as LoginCredentialRepository

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            CredentialCleanupTask(proxyRepo, expiredRetentionDays = 0, consumedRetentionHours = 24)
        }
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            CredentialCleanupTask(proxyRepo, expiredRetentionDays = 7, consumedRetentionHours = 0)
        }
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            CredentialCleanupTask(proxyRepo, expiredRetentionDays = 7, consumedRetentionHours = 24, batchSize = 0)
        }
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            CredentialCleanupTask(proxyRepo, expiredRetentionDays = 7, consumedRetentionHours = 24, maxBatches = 0)
        }
    }
}
