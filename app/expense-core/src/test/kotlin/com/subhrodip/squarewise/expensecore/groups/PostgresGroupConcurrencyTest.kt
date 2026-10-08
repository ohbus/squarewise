package com.subhrodip.squarewise.expensecore.groups

import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.api.GroupResponse
import com.subhrodip.squarewise.expensecore.groups.api.UpdateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.persistence.store.JpaGroupStore
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

/**
 * Verifies group rename serialization against a real PostgreSQL row lock.
 *
 * The test is opt-in because the normal unit/integration suite uses an embedded
 * database. Set [SQUAREWISE_POSTGRES_TESTS] to `true` and provide the Compose
 * datasource variables to run this evidence against PostgreSQL.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SQUAREWISE_POSTGRES_TESTS", matches = "true")
class PostgresGroupConcurrencyTest @Autowired constructor(
    private val store: JpaGroupStore,
    private val jdbc: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate
) {
    /**
     * Holds a PostgreSQL row lock, proves a competing rename waits, then verifies
     * both committed updates have distinct revisions and complete effect sets.
     */
    @Test
    fun `postgres serializes competing renames and preserves one effect set per commit`() {
        val group = store.create("postgres-owner", CreateGroupRequest("Before", "TRIP", "EUR"))
        val lockAcquired = CountDownLatch(1)
        val releaseLock = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        val lockHolder = executor.submit {
            transactionTemplate.executeWithoutResult {
                store.update(group.groupId, "postgres-owner", UpdateGroupRequest("First"))
                lockAcquired.countDown()
                check(releaseLock.await(10, TimeUnit.SECONDS)) { "lock release was not signalled" }
            }
        }
        check(lockAcquired.await(10, TimeUnit.SECONDS)) { "PostgreSQL row lock was not acquired" }

        val competingRename = executor.submit(Callable {
            store.update(group.groupId, "postgres-owner", UpdateGroupRequest("After"))
        })
        assertTimeoutPreemptively(Duration.ofMillis(500)) {
            Thread.sleep(100)
            assertFalse(competingRename.isDone)
        }

        releaseLock.countDown()
        lockHolder.get(10, TimeUnit.SECONDS)
        val updated = competingRename.get(10, TimeUnit.SECONDS) as GroupResponse
        executor.shutdown()

        assertEquals(2L, updated.revision)
        assertEquals(2L, jdbc.queryForObject(
            "SELECT count(*) FROM group_audit WHERE group_id = ? AND revision IN (1, 2)",
            Long::class.java,
            group.groupId
        ))
        assertEquals(2L, jdbc.queryForObject(
            "SELECT count(*) FROM sync_changes WHERE group_id = ? AND revision IN (1, 2)",
            Long::class.java,
            group.groupId.toString()
        ))
        assertEquals(2L, jdbc.queryForObject(
            "SELECT count(*) FROM expense_outbox WHERE group_id = ? AND group_revision IN (1, 2)",
            Long::class.java,
            group.groupId
        ))
    }
}
