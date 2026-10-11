package com.subhrodip.squarewise.expensecore.recurring

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSpecification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** Verifies the durable recurring split value object supports empty and populated specifications. */
class RecurringExpenseSpecificationTest {
    @Test
    fun `defaults to an empty specification`() {
        assertEquals(RecurringExpenseSpecification(emptyList(), emptyList()), RecurringExpenseSpecification())
    }

    @Test
    fun `retains custom payers and allocations`() {
        val participant = UUID.randomUUID()
        val specification = RecurringExpenseSpecification(
            payers = listOf(ExpensePayer(participant, 100L)),
            allocations = listOf(ExpenseAllocation(participant, 100L))
        )

        assertEquals(participant, specification.payers.single().participantId)
        assertEquals(100L, specification.allocations.single().allocatedMinor)
    }
}
