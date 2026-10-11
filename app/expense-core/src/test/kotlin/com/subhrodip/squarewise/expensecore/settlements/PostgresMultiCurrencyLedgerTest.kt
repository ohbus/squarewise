package com.subhrodip.squarewise.expensecore.settlements

import com.subhrodip.squarewise.expensecore.expenses.api.ExpenseController
import com.subhrodip.squarewise.expensecore.expenses.api.request.AllocationInputDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.AllocationItemDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.CreateExpenseRequest
import com.subhrodip.squarewise.expensecore.expenses.api.request.MoneyDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.PayerDto
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.JpaExpenseStore
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreateInviteRequest
import com.subhrodip.squarewise.expensecore.groups.persistence.store.JpaGroupStore
import com.subhrodip.squarewise.expensecore.settlements.api.RecordSettlementRequest
import com.subhrodip.squarewise.expensecore.settlements.api.ReverseSettlementRequest
import com.subhrodip.squarewise.expensecore.settlements.api.SettlementController
import com.subhrodip.squarewise.expensecore.settlements.service.SettlementSuggestionEngine
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.security.Principal
import java.util.UUID

/**
 * End-to-end integration and zero-sum verification test for multi-currency groups
 * executing against PostgreSQL (opt-in via SQUAREWISE_POSTGRES_TESTS=true).
 *
 * Verifies:
 * 1. Isolated currency streams for EUR, USD, and GBP in a single group.
 * 2. Mixed split modes (EQUAL, EXACT, PERCENT_BASIS_POINTS, WEIGHTED_SHARES).
 * 3. Currency-separated settlement suggestions.
 * 4. Multi-currency settlement recording and reversal.
 * 5. Full compliance with zero-sum ledger reconciliation queries.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "SQUAREWISE_POSTGRES_TESTS", matches = "true")
@Transactional
class PostgresMultiCurrencyLedgerTest @Autowired constructor(
    private val groupStore: JpaGroupStore,
    private val expenseStore: JpaExpenseStore,
    private val expenseController: ExpenseController,
    private val settlementController: SettlementController,
    private val suggestionEngine: SettlementSuggestionEngine,
    private val jdbc: JdbcTemplate
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Test
    fun `executes multi-currency lifecycle and satisfies ledger zero-sum invariants`() {
        // 1. Setup group with 3 members
        val ownerSubject = "alice"
        val group = groupStore.create(ownerSubject, CreateGroupRequest("Multi-Currency Trip", "TRIP", "EUR"))
        val invite = groupStore.invite(group.groupId, ownerSubject, CreateInviteRequest(24))
        groupStore.claim(invite.token, "bob")
        val inviteCharlie = groupStore.invite(group.groupId, ownerSubject, CreateInviteRequest(24))
        groupStore.claim(inviteCharlie.token, "charlie")

        val members = groupStore.listMembers(group.groupId, ownerSubject)
        val alice = members.first { it.subject == "alice" }.membershipId
        val bob = members.first { it.subject == "bob" }.membershipId
        val charlie = members.first { it.subject == "charlie" }.membershipId
        val alicePrincipal = Principal { "alice" }

        // 2. Incur expenses in EUR, USD, GBP with mixed split modes
        // 2a. EUR EQUAL: Alice pays 3000 EUR, split equally among alice, bob, charlie
        expenseController.createExpense(
            group.groupId,
            idempotencyKey = "key-eur-1",
            request = CreateExpenseRequest(
                expenseId = UUID.randomUUID(),
                description = "EUR Hotel",
                amount = MoneyDto("EUR", "3000"),
                category = "lodging",
                payers = listOf(PayerDto(alice.toString(), MoneyDto("EUR", "3000"))),
                allocation = AllocationInputDto("EQUAL", listOf(
                    AllocationItemDto(alice.toString(), "1"),
                    AllocationItemDto(bob.toString(), "1"),
                    AllocationItemDto(charlie.toString(), "1")
                ))
            ),
            principal = alicePrincipal
        )

        // 2b. USD EXACT: Bob pays 1500 USD, split exact: alice 500, bob 500, charlie 500
        expenseController.createExpense(
            group.groupId,
            idempotencyKey = "key-usd-1",
            request = CreateExpenseRequest(
                expenseId = UUID.randomUUID(),
                description = "USD Dinner",
                amount = MoneyDto("USD", "1500"),
                category = "food",
                payers = listOf(PayerDto(bob.toString(), MoneyDto("USD", "1500"))),
                allocation = AllocationInputDto("EXACT", listOf(
                    AllocationItemDto(alice.toString(), "500"),
                    AllocationItemDto(bob.toString(), "500"),
                    AllocationItemDto(charlie.toString(), "500")
                ))
            ),
            principal = alicePrincipal
        )

        // 2c. GBP PERCENT: Charlie pays 2000 GBP, split 50% alice (5000 bps), 50% bob (5000 bps)
        expenseController.createExpense(
            group.groupId,
            idempotencyKey = "key-gbp-1",
            request = CreateExpenseRequest(
                expenseId = UUID.randomUUID(),
                description = "GBP Transport",
                amount = MoneyDto("GBP", "2000"),
                category = "transport",
                payers = listOf(PayerDto(charlie.toString(), MoneyDto("GBP", "2000"))),
                allocation = AllocationInputDto("PERCENT_BASIS_POINTS", listOf(
                    AllocationItemDto(alice.toString(), "5000"),
                    AllocationItemDto(bob.toString(), "5000")
                ))
            ),
            principal = alicePrincipal
        )

        // 2d. GBP WEIGHTED: Alice pays 3000 GBP, weights 1 to bob, 2 to charlie
        expenseController.createExpense(
            group.groupId,
            idempotencyKey = "key-gbp-2",
            request = CreateExpenseRequest(
                expenseId = UUID.randomUUID(),
                description = "GBP Museum",
                amount = MoneyDto("GBP", "3000"),
                category = "entertainment",
                payers = listOf(PayerDto(alice.toString(), MoneyDto("GBP", "3000"))),
                allocation = AllocationInputDto("WEIGHTED_SHARES", listOf(
                    AllocationItemDto(bob.toString(), "1"),
                    AllocationItemDto(charlie.toString(), "2")
                ))
            ),
            principal = alicePrincipal
        )

        entityManager.flush()

        // 3. Inspect isolated balances
        val balances = expenseStore.balances(group.groupId)
        // Group balances must contain separate entries per currency
        val eurBalances = balances.filter { it.amount.currency == "EUR" }
        val usdBalances = balances.filter { it.amount.currency == "USD" }
        val gbpBalances = balances.filter { it.amount.currency == "GBP" }

        assertEquals(3, eurBalances.size)
        assertEquals(3, usdBalances.size)
        assertEquals(3, gbpBalances.size)

        // Sum of balances within each currency stream must equal 0
        assertEquals(0L, eurBalances.sumOf { it.amount.minor.toLong() })
        assertEquals(0L, usdBalances.sumOf { it.amount.minor.toLong() })
        assertEquals(0L, gbpBalances.sumOf { it.amount.minor.toLong() })

        // 4. Suggestion engine generates independent suggestions per currency
        val suggestions = suggestionEngine.calculateSuggestions(balances)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.currency == "EUR" })
        assertTrue(suggestions.any { it.currency == "USD" })
        assertTrue(suggestions.any { it.currency == "GBP" })

        // 5. Record settlement in USD: Alice pays Bob 500 USD
        val usdSettlement = settlementController.record(
            groupId = group.groupId,
            idempotencyKey = "settle-usd-1",
            request = RecordSettlementRequest(
                fromParticipantId = alice,
                toParticipantId = bob,
                amountMinor = "500",
                currency = "USD"
            ),
            principal = alicePrincipal
        )
        assertNotNull(usdSettlement.id)
        assertEquals("USD", usdSettlement.currency)

        // 6. Record settlement in EUR: Bob pays Alice 1000 EUR
        val eurSettlement = settlementController.record(
            groupId = group.groupId,
            idempotencyKey = "settle-eur-1",
            request = RecordSettlementRequest(
                fromParticipantId = bob,
                toParticipantId = alice,
                amountMinor = "1000",
                currency = "EUR"
            ),
            principal = alicePrincipal
        )
        assertNotNull(eurSettlement.id)
        assertEquals("EUR", eurSettlement.currency)

        // 7. Record settlement in GBP: Bob pays Charlie 1000 GBP, then reverse it
        val gbpSettlement = settlementController.record(
            groupId = group.groupId,
            idempotencyKey = "settle-gbp-1",
            request = RecordSettlementRequest(
                fromParticipantId = bob,
                toParticipantId = charlie,
                amountMinor = "1000",
                currency = "GBP"
            ),
            principal = alicePrincipal
        )
        assertNotNull(gbpSettlement.id)
        assertEquals("GBP", gbpSettlement.currency)

        val reversedGbp = settlementController.reverse(
            groupId = group.groupId,
            settlementId = gbpSettlement.id,
            request = ReverseSettlementRequest(reason = "paid by cash instead"),
            principal = alicePrincipal
        )
        assertEquals("REVERSED", reversedGbp.status.name)

        entityManager.flush()

        // 8. Reconcile against authoritative ledger reconciliation queries
        // 8a. Zero-sum ledger check
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
        assertTrue(nonZeroStreams.isEmpty(), "Zero-sum ledger check failed: $nonZeroStreams")

        // 8b. Expense currency coverage check
        val mismatchedExpensePostings = jdbc.queryForList(
            """
            SELECT e.expense_id, e.group_id, e.currency
            FROM expenses e
            LEFT JOIN balance_postings p ON p.expense_id = e.expense_id
            WHERE e.group_id = ? AND e.deleted = FALSE
            GROUP BY e.expense_id, e.group_id, e.currency
            HAVING COUNT(p.posting_id) = 0
                OR COUNT(CASE WHEN p.currency <> e.currency THEN 1 END) > 0
            """.trimIndent(),
            group.groupId
        )
        assertTrue(mismatchedExpensePostings.isEmpty(), "Expense currency coverage failed: $mismatchedExpensePostings")

        // 8c. Settlement currency coverage check
        val mismatchedSettlementPostings = jdbc.queryForList(
            """
            SELECT s.settlement_id, s.group_id, s.currency AS settlement_currency,
                   p.currency AS posting_currency
            FROM settlements s
            JOIN balance_postings p ON p.settlement_id = s.settlement_id
            WHERE s.group_id = ? AND p.currency <> s.currency
            """.trimIndent(),
            group.groupId
        )
        assertTrue(mismatchedSettlementPostings.isEmpty(), "Settlement currency coverage failed: $mismatchedSettlementPostings")

        // 8d. Settlement posting count and net amount
        val invalidSettlementTotals = jdbc.queryForList(
            """
            WITH posting_totals AS (
              SELECT settlement_id,
                     COUNT(*) AS posting_count,
                     SUM(amount_minor) AS net_amount_minor
              FROM balance_postings
              WHERE settlement_id IS NOT NULL AND group_id = ?
              GROUP BY settlement_id
            )
            SELECT s.settlement_id, s.status, s.amount_minor,
                   COALESCE(p.posting_count, 0) AS posting_count,
                   COALESCE(p.net_amount_minor, 0) AS net_amount_minor
            FROM settlements s
            LEFT JOIN posting_totals p ON p.settlement_id = s.settlement_id
            WHERE s.group_id = ?
              AND ((s.status = 'RECORDED' AND COALESCE(p.posting_count, 0) <> 2)
                OR (s.status = 'REVERSED' AND COALESCE(p.posting_count, 0) <> 4)
                OR COALESCE(p.net_amount_minor, 0) <> 0)
            """.trimIndent(),
            group.groupId,
            group.groupId
        )
        assertTrue(invalidSettlementTotals.isEmpty(), "Settlement totals reconciliation failed: $invalidSettlementTotals")
    }
}
