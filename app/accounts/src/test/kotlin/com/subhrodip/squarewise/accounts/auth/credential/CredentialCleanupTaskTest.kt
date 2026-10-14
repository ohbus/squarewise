package com.subhrodip.squarewise.accounts.auth.credential

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

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

        assertThrows<IllegalArgumentException> {
            CredentialCleanupTask(proxyRepo, expiredRetentionDays = 0, consumedRetentionHours = 24)
        }
        assertThrows<IllegalArgumentException> {
            CredentialCleanupTask(proxyRepo, expiredRetentionDays = 7, consumedRetentionHours = 0)
        }
        assertThrows<IllegalArgumentException> {
            CredentialCleanupTask(proxyRepo, expiredRetentionDays = 7, consumedRetentionHours = 24, batchSize = 0)
        }
        assertThrows<IllegalArgumentException> {
            CredentialCleanupTask(proxyRepo, expiredRetentionDays = 7, consumedRetentionHours = 24, maxBatches = 0)
        }
    }

    @Test
    fun `default constructor arguments instantiate successfully`() {
        val proxyRepo = Proxy.newProxyInstance(
            LoginCredentialRepository::class.java.classLoader,
            arrayOf(LoginCredentialRepository::class.java)
        ) { _, method: Method, _ ->
            if (method.name == "purgeExpiredOrConsumed") 0 else null
        } as LoginCredentialRepository
        val task = CredentialCleanupTask(proxyRepo)
        assertEquals(0, task.executeCleanup())
    }

    @Test
    fun `executeCleanup runs inside transaction template when transactionManager is provided`() {
        val proxyRepo = Proxy.newProxyInstance(
            LoginCredentialRepository::class.java.classLoader,
            arrayOf(LoginCredentialRepository::class.java)
        ) { _, method: Method, _ ->
            if (method.name == "purgeExpiredOrConsumed") 5 else null
        } as LoginCredentialRepository

        val mockTm = mock(PlatformTransactionManager::class.java)
        val mockStatus = mock(TransactionStatus::class.java)
        `when`(mockTm.getTransaction(any())).thenReturn(mockStatus)

        val task = CredentialCleanupTask(proxyRepo, transactionManager = mockTm)
        val count = task.executeCleanup()
        assertEquals(5, count)
        verify(mockTm).commit(mockStatus)
    }

    @Test
    fun `executeCleanup terminates at maxBatches when backlog is full`() {
        var callCount = 0
        val proxyRepo = Proxy.newProxyInstance(
            LoginCredentialRepository::class.java.classLoader,
            arrayOf(LoginCredentialRepository::class.java)
        ) { _, method: Method, args: Array<out Any?>? ->
            if (method.name == "purgeExpiredOrConsumed") {
                callCount++
                args?.get(2) as Int
            } else null
        } as LoginCredentialRepository

        val task = CredentialCleanupTask(proxyRepo, batchSize = 10, maxBatches = 2)
        val count = task.executeCleanup()
        assertEquals(20, count)
        assertEquals(2, callCount)
    }
}
