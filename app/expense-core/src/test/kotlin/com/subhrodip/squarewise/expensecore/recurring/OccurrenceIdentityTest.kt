package com.subhrodip.squarewise.expensecore.recurring

import com.subhrodip.squarewise.expensecore.recurring.domain.OccurrenceIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class OccurrenceIdentityTest {
    @Test
    fun `same schedule and date produce stable id`() {
        val identity = OccurrenceIdentity(); val date = LocalDate.of(2026, 9, 17)
        assertEquals(identity.id("schedule-1", date), identity.id("schedule-1", date))
        assertNotEquals(identity.id("schedule-1", date), identity.id("schedule-1", date.plusDays(1)))
    }

    @Test
    fun `string and uuid schedule identities use the same namespace`() {
        val identity = OccurrenceIdentity()
        val scheduleId = UUID.randomUUID()
        val date = LocalDate.of(2026, 9, 17)

        assertEquals(identity.id(scheduleId.toString(), date), identity.id(scheduleId, date))
    }

    @Test
    fun `rejects blank schedule identity`() {
        val identity = OccurrenceIdentity()
        assertThrows(IllegalArgumentException::class.java) {
            identity.id(" ", LocalDate.of(2026, 9, 17))
        }
    }
}
