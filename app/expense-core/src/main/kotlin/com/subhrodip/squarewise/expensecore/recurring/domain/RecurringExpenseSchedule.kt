package com.subhrodip.squarewise.expensecore.recurring.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Entity representing a recurring expense schedule configuration.
 *
 * Defines the recurrence rules, schedule cadence, amounts, and tracking state
 * for automated or periodic expense generation within a group.
 *
 * Invariants:
 * - [amountMinor] must be positive in domain logic.
 * - [currency] represents an ISO-4217 3-letter currency code.
 * - [nextOccurrenceDate] tracks the next scheduled generation date.
 */
@Entity
@Table(name = "recurring_expense_schedules")
class RecurringExpenseSchedule(
    @Id
    @Column(name = "schedule_id", nullable = false)
    var scheduleId: UUID,

    @Column(name = "group_id", nullable = false)
    var groupId: UUID,

    @Column(name = "description", nullable = false, length = 240)
    var description: String,

    @Column(name = "amount_minor", nullable = false)
    var amountMinor: Long,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 16)
    var frequency: RecurrenceFrequency,

    @Column(name = "day_of_month")
    var dayOfMonth: Int? = null,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,

    @Column(name = "end_date")
    var endDate: LocalDate? = null,

    @Column(name = "next_occurrence_date", nullable = false)
    var nextOccurrenceDate: LocalDate,

    @Column(name = "paused", nullable = false)
    var paused: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 1
) {
    @Column(name = "custom_specification", columnDefinition = "TEXT")
    var customSpecification: String? = null
}
