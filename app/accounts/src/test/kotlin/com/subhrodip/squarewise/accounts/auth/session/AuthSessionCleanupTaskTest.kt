package com.subhrodip.squarewise.accounts.auth.session

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

class AuthSessionCleanupTaskTest {
    private val repository = mock(AuthSessionRepository::class.java)

    @Test
    fun `executeCleanup purges expired or revoked sessions in bounded batches`() {
        val task = AuthSessionCleanupTask(repository, retentionDays = 14, batchSize = 250)
        `when`(repository.purgeExpiredOrRevokedOlderThan(any(Instant::class.java) ?: Instant.EPOCH, eq(250))).thenReturn(15)

        val count = task.executeCleanup()

        assertEquals(15, count)
        verify(repository).purgeExpiredOrRevokedOlderThan(any(Instant::class.java) ?: Instant.EPOCH, eq(250))
    }

    @Test
    fun `executeCleanup drains backlog across multiple batches`() {
        val task = AuthSessionCleanupTask(repository, retentionDays = 14, batchSize = 100, maxBatches = 5)
        `when`(repository.purgeExpiredOrRevokedOlderThan(any(Instant::class.java) ?: Instant.EPOCH, eq(100)))
            .thenReturn(100)
            .thenReturn(100)
            .thenReturn(42)

        val count = task.executeCleanup()

        assertEquals(242, count)
    }

    @Test
    fun `executeCleanup with transaction manager wraps batch in transaction`() {
        val tm = mock(PlatformTransactionManager::class.java)
        val status = mock(TransactionStatus::class.java)
        `when`(tm.getTransaction(any())).thenReturn(status)

        val task = AuthSessionCleanupTask(repository, retentionDays = 14, batchSize = 100, transactionManager = tm)
        `when`(repository.purgeExpiredOrRevokedOlderThan(any(Instant::class.java) ?: Instant.EPOCH, eq(100))).thenReturn(25)

        val count = task.executeCleanup()

        assertEquals(25, count)
        verify(tm).commit(status)
    }

    @Test
    fun `executeCleanup returns zero when no expired sessions exist`() {
        val task = AuthSessionCleanupTask(repository)
        `when`(repository.purgeExpiredOrRevokedOlderThan(any(Instant::class.java) ?: Instant.EPOCH, eq(500))).thenReturn(0)

        val count = task.executeCleanup()

        assertEquals(0, count)
    }

    @Test
    fun `rejects invalid retention and batch size parameters`() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthSessionCleanupTask(repository, retentionDays = 0, batchSize = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthSessionCleanupTask(repository, retentionDays = 14, batchSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthSessionCleanupTask(repository, retentionDays = 14, batchSize = 100, maxBatches = 0)
        }
    }
}
