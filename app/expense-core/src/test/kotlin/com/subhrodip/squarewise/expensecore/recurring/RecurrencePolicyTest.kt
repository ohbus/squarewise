package com.subhrodip.squarewise.expensecore.recurring

import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrenceFrequency
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrencePolicy
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrenceSchedule
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

class RecurrencePolicyTest {
    private val policy = RecurrencePolicy()
    @Test
    fun `clamps monthly schedule at month end`() {
        val schedule = RecurrenceSchedule("s", RecurrenceFrequency.MONTHLY, 31)

        assertEquals("s", schedule.scheduleId)
        assertEquals(LocalDate.of(2024, 2, 29), policy.nextAfter(LocalDate.of(2024, 1, 31), schedule))
    }

    @Test
    fun `monthly schedule without a configured day preserves the source day`() {
        assertEquals(
            LocalDate.of(2026, 10, 17),
            policy.nextAfter(LocalDate.of(2026, 9, 17), RecurrenceSchedule("s", RecurrenceFrequency.MONTHLY))
        )
    }

    @Test
    fun `advances weekly occurrence`() {
        assertEquals(LocalDate.of(2026, 9, 24), policy.nextAfter(LocalDate.of(2026, 9, 17), RecurrenceSchedule("s", RecurrenceFrequency.WEEKLY)))
    }

    @Test
    fun `calculates the next date for a persisted schedule`() {
        val schedule = RecurringExpenseSchedule(
            scheduleId = UUID.randomUUID(),
            groupId = UUID.randomUUID(),
            description = "Rent",
            amountMinor = 1000,
            currency = "EUR",
            frequency = RecurrenceFrequency.MONTHLY,
            dayOfMonth = 31,
            startDate = LocalDate.of(2026, 1, 31),
            nextOccurrenceDate = LocalDate.of(2026, 1, 31),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        assertEquals(
            LocalDate.of(2026, 2, 28),
            policy.nextAfter(LocalDate.of(2026, 1, 31), schedule),
        )
    }

    @Test
    fun `rejects invalid schedule identity`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceSchedule(" ", RecurrenceFrequency.WEEKLY)
        }
    }

    @Test
    fun `rejects invalid monthly day`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceSchedule("s", RecurrenceFrequency.MONTHLY, 32)
        }
    }

    @Test
    fun `rejects monthly day on weekly schedule`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceSchedule("s", RecurrenceFrequency.WEEKLY, 1)
        }
    }
}
