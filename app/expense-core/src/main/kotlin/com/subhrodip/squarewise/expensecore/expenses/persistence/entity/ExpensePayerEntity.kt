package com.subhrodip.squarewise.expensecore.expenses.persistence.entity

import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseEntity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/**
 * JPA entity representing a participant payment towards an expense.
 *
 * Enforces uniqueness on (expense_id, participant_id) to prevent duplicate payer entries.
 */
@Entity
@Table(
    name = "expense_payers",
    uniqueConstraints = [UniqueConstraint(name = "uk_expense_payers_expense_participant", columnNames = ["expense_id", "participant_id"])]
)
class ExpensePayerEntity(
    @Id
    @Column(name = "payer_id", nullable = false)
    var payerId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    var expense: ExpenseEntity,

    @Column(name = "participant_id", nullable = false)
    var participantId: UUID,

    @Column(name = "amount_minor", nullable = false)
    var amountMinor: Long
)
