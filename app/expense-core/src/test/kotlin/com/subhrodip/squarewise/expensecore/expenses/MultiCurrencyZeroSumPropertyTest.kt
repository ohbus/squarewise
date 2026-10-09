package com.subhrodip.squarewise.expensecore.expenses

import com.subhrodip.squarewise.expensecore.expenses.api.ExpenseController
import com.subhrodip.squarewise.expensecore.expenses.api.request.AllocationInputDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.AllocationItemDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.CreateExpenseRequest
import com.subhrodip.squarewise.expensecore.expenses.api.request.MoneyDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.PayerDto
import com.subhrodip.squarewise.expensecore.expenses.domain.AllocationCalculator
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.JpaExpenseStore
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreateInviteRequest
import com.subhrodip.squarewise.expensecore.groups.persistence.store.JpaGroupStore
import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import com.subhrodip.squarewise.expensecore.settlements.persistence.JpaSettlementStore
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.security.Principal
import java.util.UUID
import kotlin.random.Random

/**
 * Property-based invariant testing suite verifying that arbitrary sequences
 * of multi-currency transactions, updates, deletions, and settlements maintain
 * zero-sum ledger balance per currency without rounding leakage or currency pollution.
 */
@SpringBootTest
@Transactional
class MultiCurrencyZeroSumPropertyTest @Autowired constructor(
    private val groupStore: JpaGroupStore,
    private val expenseStore: JpaExpenseStore,
    private val expenseController: ExpenseController,
    private val settlementStore: JpaSettlementStore,
    private val jdbc: JdbcTemplate
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Test
    fun `arbitrary sequences of multi-currency operations strictly conserve zero-sum invariants`() {
        val random = Random(0x434F5245) // Seed "CORE"
        val currencies = listOf("EUR", "USD", "GBP", "CHF", "JPY")
        val modes = listOf("EQUAL", "EXACT", "PERCENT_BASIS_POINTS", "WEIGHTED_SHARES")

        val ownerSubject = "prop-owner"
        val group = groupStore.create(ownerSubject, CreateGroupRequest("Zero-Sum Property Group", "TRIP", "EUR"))

        // Create 4 participants
        val participantSubjects = listOf("alice", "bob", "charlie", "dave")
        participantSubjects.forEach { sub ->
            val invite = groupStore.invite(group.groupId, ownerSubject, CreateInviteRequest(24))
            groupStore.claim(invite.token, sub)
        }
        val members = groupStore.listMembers(group.groupId, ownerSubject)
        val memberIds = members.map { it.membershipId }
        val ownerPrincipal = Principal { ownerSubject }

        val createdExpenseIds = mutableListOf<UUID>()
        val recordedSettlementIds = mutableListOf<UUID>()

        // Execute 50 randomized operations
        repeat(50) { opIndex ->
            val currency = currencies[random.nextInt(currencies.size)]
            val opType = if (createdExpenseIds.isEmpty()) 0 else random.nextInt(4)

            when (opType) {
                0 -> { // Create expense
                    val expenseId = UUID.randomUUID()
                    val totalAmount = random.nextLong(100, 50_000)
                    val payer = memberIds[random.nextInt(memberIds.size)]
                    val mode = modes[random.nextInt(modes.size)]
                    val participantsSubset = memberIds.shuffled(random).take(random.nextInt(1, memberIds.size + 1))

                    val allocationItems = when (mode) {
                        "EQUAL" -> participantsSubset.map { AllocationItemDto(it.toString(), "1") }
                        "EXACT" -> {
                            val exactMap = AllocationCalculator.equal(totalAmount, participantsSubset.map { it.toString() })
                            exactMap.map { (pid, amount) -> AllocationItemDto(pid, amount.toString()) }
                        }
                        "PERCENT_BASIS_POINTS" -> {
                            val bpsMap = mutableMapOf<String, Long>()
                            var remainingBps = 10_000L
                            participantsSubset.dropLast(1).forEach { p ->
                                val share = random.nextLong(0, remainingBps + 1)
                                bpsMap[p.toString()] = share
                                remainingBps -= share
                            }
                            bpsMap[participantsSubset.last().toString()] = remainingBps
                            bpsMap.map { (pid, bps) -> AllocationItemDto(pid, bps.toString()) }
                        }
                        "WEIGHTED_SHARES" -> {
                            participantsSubset.map { AllocationItemDto(it.toString(), random.nextInt(1, 10).toString()) }
                        }
                        else -> emptyList()
                    }

                    expenseController.createExpense(
                        groupId = group.groupId,
                        idempotencyKey = "prop-key-$opIndex",
                        request = CreateExpenseRequest(
                            expenseId = expenseId,
                            description = "Random expense $opIndex",
                            amount = MoneyDto(currency, totalAmount.toString()),
                            category = "general",
                            payers = listOf(PayerDto(payer.toString(), MoneyDto(currency, totalAmount.toString()))),
                            allocation = AllocationInputDto(mode, allocationItems)
                        ),
                        principal = ownerPrincipal
                    )
                    createdExpenseIds.add(expenseId)
                }
                1 -> { // Record settlement
                    val from = memberIds[random.nextInt(memberIds.size)]
                    var to = memberIds[random.nextInt(memberIds.size)]
                    while (to == from) {
                        to = memberIds[random.nextInt(memberIds.size)]
                    }
                    val amount = random.nextLong(10, 5_000)
                    val settlementId = UUID.randomUUID()
                    settlementStore.record(
                        group.groupId,
                        Settlement(
                            id = settlementId,
                            fromParticipantId = from,
                            toParticipantId = to,
                            amountMinor = amount,
                            currency = currency
                        )
                    )
                    recordedSettlementIds.add(settlementId)
                }
                2 -> { // Reverse existing settlement
                    if (recordedSettlementIds.isNotEmpty()) {
                        val toReverse = recordedSettlementIds.removeAt(random.nextInt(recordedSettlementIds.size))
                        settlementStore.reverse(group.groupId, toReverse, "random-reversal-$opIndex")
                    }
                }
                3 -> { // Delete existing expense
                    if (createdExpenseIds.isNotEmpty()) {
                        val toDelete = createdExpenseIds.removeAt(random.nextInt(createdExpenseIds.size))
                        expenseStore.findById(toDelete)?.let { record ->
                            expenseController.deleteExpense(
                                groupId = group.groupId,
                                expenseId = toDelete,
                                version = record.version,
                                principal = ownerPrincipal
                            )
                        }
                    }
                }
            }
        }

        entityManager.flush()

        // Verify universal zero-sum property across all currencies
        val nonZeroStreams = jdbc.queryForList(
            """
            SELECT group_id, currency, SUM(amount_minor) AS net_amount_minor,
                   COUNT(*) AS posting_count
            FROM balance_postings
            WHERE group_id = ?
            GROUP BY group_id, currency
            HAVING SUM(amount_minor) <> 0
            """.trimIndent(),
            group.groupId
        )
        assertTrue(
            nonZeroStreams.isEmpty(),
            "Invariant violation: non-zero ledger posting stream found: $nonZeroStreams"
        )

        // Verify balance queries reflect the zero-sum ledger
        val balances = expenseStore.balances(group.groupId)
        val balancesByCurrency = balances.groupBy { it.amount.currency }
        balancesByCurrency.forEach { (curr, items) ->
            val sum = items.sumOf { it.amount.minor.toLong() }
            assertEquals(0L, sum, "Balance stream for $curr did not net to zero: $items")
        }
    }
}
