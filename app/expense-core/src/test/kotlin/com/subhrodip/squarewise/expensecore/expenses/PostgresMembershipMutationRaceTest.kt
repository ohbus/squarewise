package com.subhrodip.squarewise.expensecore.expenses

import java.time.Duration
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.JpaExpenseStore
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.store.JpaGroupStore

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

/**
 * Verifies membership removal and financial mutation serialize on the group row.
 * The test is opt-in and requires the Compose PostgreSQL datasource.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SQUAREWISE_POSTGRES_TESTS", matches = "true")
class PostgresMembershipMutationRaceTest @Autowired constructor(
    private val expenseStore: JpaExpenseStore,
    private val groupStore: JpaGroupStore,
    private val memberships: GroupMembershipRepository,
    private val jdbc: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate
) {
    /** Proves a mutation waiting behind committed removal produces no postings. */
    @Test
    fun `removed member cannot create expense after removal transaction commits`() {
        val owner = "postgres-owner"
        val member = "postgres-removed-member"
        val group = groupStore.create(owner, CreateGroupRequest("Membership race", "TRIP", "EUR"))
        val memberId = UUID.randomUUID()
        memberships.save(GroupMembershipEntity(memberId, group.groupId, member, null, false, "ACTIVE"))
        val expense = ExpenseRecord(
            expenseId = UUID.randomUUID(), groupId = group.groupId,
            description = "Must not commit", category = "other", currency = "EUR",
            amountMinor = 1000, version = 1, allocationMode = "EXACT",
            createdAt = Instant.now(), payers = listOf(ExpensePayer(memberId, 1000)),
            allocations = listOf(ExpenseAllocation(memberId, 1000))
        )
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val remover = executor.submit {
            transactionTemplate.execute {
                groupStore.removeMember(group.groupId, owner, memberId)
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "removal release was not signalled" }
            }
        }
        check(entered.await(10, TimeUnit.SECONDS)) { "removal did not acquire its transaction" }
        val mutation = executor.submit(Callable {
            runCatching {
                transactionTemplate.execute<Any?> {
                    expenseStore.create(group.groupId, expense, "race-key", member)
                    null
                }
            }
        })
        assertTimeoutPreemptively(Duration.ofMillis(500)) {
            Thread.sleep(100)
            assertFalse(mutation.isDone)
        }
        release.countDown()
        remover.get(10, TimeUnit.SECONDS)
        val mutationResult = mutation.get(10, TimeUnit.SECONDS) as Result<Any?>
        assertTrue(mutationResult.isFailure)
        executor.shutdown()

        assertEquals(0L, jdbc.queryForObject(
            "SELECT count(*) FROM balance_postings WHERE expense_id = ?",
            Long::class.java, expense.expenseId
        ))
    }
}
