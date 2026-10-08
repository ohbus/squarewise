package com.subhrodip.squarewise.expensecore.settlements.service

import com.subhrodip.squarewise.expensecore.settlements.domain.SuggestedSettlement
import com.subhrodip.squarewise.expensecore.expenses.api.response.GroupBalanceItem
import com.subhrodip.squarewise.expensecore.expenses.domain.FinancialArithmetic
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.ExpenseStore

import org.springframework.stereotype.Service
import java.util.PriorityQueue
import java.util.UUID

@Service
class SettlementSuggestionEngine(private val expenseStore: ExpenseStore) {

    fun suggestSettlements(groupId: UUID): List<SuggestedSettlement> {
        val balances = expenseStore.balances(groupId)
        return calculateSuggestions(balances)
    }

    fun calculateSuggestions(balances: List<GroupBalanceItem>): List<SuggestedSettlement> {
        val byCurrency = balances.groupBy { it.amount.currency }.toSortedMap()
        val results = mutableListOf<SuggestedSettlement>()

        for ((currency, items) in byCurrency) {
            results.addAll(calculateSuggestionsForCurrency(currency, items))
        }

        return results
    }

    private fun calculateSuggestionsForCurrency(
        currency: String,
        items: List<GroupBalanceItem>
    ): List<SuggestedSettlement> {
        val netByParticipant = mutableMapOf<UUID, Long>()
        for (item in items) {
            val participantId = UUID.fromString(item.participantId)
            val minor = item.amount.minor.toLong()
            netByParticipant[participantId] = FinancialArithmetic.add(netByParticipant[participantId] ?: 0L, minor)
        }

        val debtorComparator = compareBy<Debtor> { it.balance }.thenBy { it.participantId }
        val creditorComparator = compareByDescending<Creditor> { it.balance }.thenBy { it.participantId }

        val debtors = PriorityQueue(debtorComparator)
        val creditors = PriorityQueue(creditorComparator)

        for ((participantId, balance) in netByParticipant) {
            if (balance < 0) {
                debtors.add(Debtor(participantId, balance))
            } else if (balance > 0) {
                creditors.add(Creditor(participantId, balance))
            }
        }

        return matchDebtorsAndCreditors(currency, debtors, creditors)
    }

    private fun matchDebtorsAndCreditors(
        currency: String,
        debtors: PriorityQueue<Debtor>,
        creditors: PriorityQueue<Creditor>
    ): List<SuggestedSettlement> {
        val results = mutableListOf<SuggestedSettlement>()
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val debtor = debtors.poll()
            val creditor = creditors.poll()

            val debtorDebt = FinancialArithmetic.negate(debtor.balance)
            val creditorCredit = creditor.balance
            val transferAmount = minOf(debtorDebt, creditorCredit)

            if (transferAmount > 0) {
                results.add(
                    SuggestedSettlement(
                        fromParticipantId = debtor.participantId,
                        toParticipantId = creditor.participantId,
                        amountMinor = transferAmount,
                        currency = currency
                    )
                )
            }

            val remainingDebtorBalance = debtor.balance + transferAmount
            val remainingCreditorBalance = creditor.balance - transferAmount

            if (remainingDebtorBalance < 0) {
                debtors.add(Debtor(debtor.participantId, remainingDebtorBalance))
            }
            if (remainingCreditorBalance > 0) {
                creditors.add(Creditor(creditor.participantId, remainingCreditorBalance))
            }
        }
        return results
    }

    private data class Debtor(val participantId: UUID, val balance: Long)
    private data class Creditor(val participantId: UUID, val balance: Long)
}
