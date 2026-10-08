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
 * JPA entity representing a participant's allocated share of an expense.
 *
 * Enforces uniqueness on (expense_id, participant_id) to prevent duplicate allocation entries.
 */
@Entity
@Table(
    name = "expense_allocations",
    uniqueConstraints = [UniqueConstraint(name = "uk_expense_allocations_expense_participant", columnNames = ["expense_id", "participant_id"])]
)
class ExpenseAllocationEntity(
    @Id
    @Column(name = "allocation_id", nullable = false)
    var allocationId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    var expense: ExpenseEntity,

    @Column(name = "participant_id", nullable = false)
    var participantId: UUID,

    @Column(name = "allocated_minor", nullable = false)
    var allocatedMinor: Long
)
