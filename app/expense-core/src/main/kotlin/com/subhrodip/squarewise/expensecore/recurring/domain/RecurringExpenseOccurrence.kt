package com.subhrodip.squarewise.expensecore.recurring.domain

import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Entity tracking a concrete occurrence generated from a [RecurringExpenseSchedule].
 *
 * Each occurrence links a schedule date to an optional concrete expense ID,
 * enforcing that a single schedule cannot generate duplicate expenses for the same occurrence date.
 *
 * Invariants:
 * - A unique constraint spans `(schedule_id, occurrence_date)`.
 */
@Entity
@Table(
    name = "recurring_expense_occurrences",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_recurring_occurrences_schedule_date",
            columnNames = ["schedule_id", "occurrence_date"]
        )
    ]
)
class RecurringExpenseOccurrence(
    @Id
    @Column(name = "occurrence_id", nullable = false)
    var occurrenceId: UUID,

    @Column(name = "schedule_id", nullable = false)
    var scheduleId: UUID,

    @Column(name = "occurrence_date", nullable = false)
    var occurrenceDate: LocalDate,

    @Column(name = "expense_id")
    var expenseId: UUID? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
