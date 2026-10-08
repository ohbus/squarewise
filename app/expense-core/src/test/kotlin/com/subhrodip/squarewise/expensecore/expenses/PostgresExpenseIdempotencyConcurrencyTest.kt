package com.subhrodip.squarewise.expensecore.expenses

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.JpaExpenseStore
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.persistence.store.JpaGroupStore

import java.time.Duration
import java.time.Instant
import java.util.UUID
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
 * Verifies same-key expense creation is serialized by the PostgreSQL group lock.
 *
 * The test is opt-in because normal tests use an embedded database. Set
 * [SQUAREWISE_POSTGRES_TESTS] to `true` with the Compose PostgreSQL datasource.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SQUAREWISE_POSTGRES_TESTS", matches = "true")
class PostgresExpenseIdempotencyConcurrencyTest @Autowired constructor(
    private val expenseStore: JpaExpenseStore,
    private val groupStore: JpaGroupStore,
    private val jdbc: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate
) {
    /** Proves concurrent identical requests create one durable financial effect set. */
    @Test
    fun `postgres serializes same-key expense creation and replays one result`() {
        val group = groupStore.create("postgres-owner", CreateGroupRequest("Idempotency", "TRIP", "EUR"))
        val expenseId = UUID.randomUUID()
        val participant = UUID.randomUUID()
        val record = ExpenseRecord(
            expenseId = expenseId,
            groupId = group.groupId,
            description = "Concurrent dinner",
            category = "food",
            currency = "EUR",
            amountMinor = 1200,
            version = 1,
            allocationMode = "EXACT",
            createdAt = Instant.now(),
            payers = listOf(ExpensePayer(participant, 1200)),
            allocations = listOf(ExpenseAllocation(participant, 1200))
        )
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val first = executor.submit {
            transactionTemplate.execute {
                expenseStore.create(group.groupId, record, "same-key")
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "lock release was not signalled" }
            }
        }
        check(entered.await(10, TimeUnit.SECONDS)) { "first transaction did not start" }
        val second = executor.submit(Callable {
            transactionTemplate.execute { expenseStore.create(group.groupId, record, "same-key") }
        })

        assertTimeoutPreemptively(Duration.ofMillis(500)) {
            Thread.sleep(100)
            assertFalse(second.isDone)
        }
        release.countDown()
        first.get(10, TimeUnit.SECONDS)
        second.get(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(1L, jdbc.queryForObject(
            "SELECT count(*) FROM expenses WHERE expense_id = ?",
            Long::class.java,
            record.expenseId
        ))
        assertEquals(1L, jdbc.queryForObject(
            "SELECT count(*) FROM expense_idempotency WHERE group_id = ? AND idempotency_key = ?",
            Long::class.java,
            group.groupId,
            "same-key"
        ))
        assertEquals(2L, jdbc.queryForObject(
            "SELECT count(*) FROM balance_postings WHERE expense_id = ?",
            Long::class.java,
            record.expenseId
        ))
    }
}
