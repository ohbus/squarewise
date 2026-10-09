package com.subhrodip.squarewise.expensecore.expenses

import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer

import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.expensecore.expenses.api.request.MoneyDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.PayerDto
import com.subhrodip.squarewise.expensecore.expenses.service.ExpenseValidator
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Verifies the shared expense input parser and DTO-to-domain mappings. */
class ExpenseValidatorTest {

    private val alice = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val bob = UUID.fromString("00000000-0000-0000-0000-000000000002")

    /** Positive minor units are parsed and custom field names are retained in errors. */
    @Test
    fun `parses positive amount and rejects malformed or non-positive values`() {
        assertEquals(123L, ExpenseValidator.parseAndValidateAmount("123"))
        assertEquals(7L, ExpenseValidator.parseAndValidateAmount("7", "expense.amount"))

        assertApplicationError("abc", "expense.amount", "valid integer")
        assertApplicationError("0", "expense.amount", "greater than zero")
        assertApplicationError("-1", "expense.amount", "greater than zero")
    }

    /** Payer validation sums valid same-currency amounts without changing their representation. */
    @Test
    fun `validates payer amounts and returns their sum`() {
        val payers = listOf(
            PayerDto(alice.toString(), MoneyDto("EUR", "125")),
            PayerDto(bob.toString(), MoneyDto("EUR", "75"))
        )

        assertEquals(200L, ExpenseValidator.validatePayers(payers, "EUR"))
        assertEquals(0L, ExpenseValidator.validatePayers(emptyList(), "EUR"))
    }

    /** Invalid payer amount text, sign, and currency each fail with the catalogued validation error. */
    @Test
    fun `rejects malformed non-positive and mismatched payer values`() {
        val malformed = PayerDto(alice.toString(), MoneyDto("EUR", "not-a-number"))
        val zero = PayerDto(alice.toString(), MoneyDto("EUR", "0"))
        val negative = PayerDto(alice.toString(), MoneyDto("EUR", "-1"))
        val wrongCurrency = PayerDto(alice.toString(), MoneyDto("USD", "10"))

        listOf(
            malformed to "valid integer",
            zero to "positive",
            negative to "positive",
            wrongCurrency to "match expense currency"
        ).forEach { (payer, expectedMessage) ->
            val error = assertThrows(SquarewiseException::class.java) {
                ExpenseValidator.validatePayers(listOf(payer), "EUR", "contributors")
            }
            assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
            assertEquals(true, error.message?.contains("contributors"))
            assertEquals(true, error.message?.contains(expectedMessage))
        }
    }

    /** Valid payer DTOs map to stable UUID/value domain records in input order. */
    @Test
    fun `maps payer DTOs to domain values`() {
        val payers = listOf(
            PayerDto(alice.toString(), MoneyDto("EUR", "125")),
            PayerDto(bob.toString(), MoneyDto("EUR", "75"))
        )

        assertEquals(
            listOf(
                ExpensePayer(alice, 125L),
                ExpensePayer(bob, 75L)
            ),
            ExpenseValidator.mapDomainPayers(payers)
        )
    }

    /** Allocation maps convert UUID keys and preserve the allocated minor-unit values. */
    @Test
    fun `maps allocation values to domain records`() {
        assertEquals(
            listOf(
                ExpenseAllocation(alice, 60L),
                ExpenseAllocation(bob, 40L)
            ),
            ExpenseValidator.mapDomainAllocations(linkedMapOf(alice.toString() to 60L, bob.toString() to 40L))
        )
    }

    private fun assertApplicationError(value: String, field: String, expectedMessage: String) {
        val error = assertThrows(SquarewiseException::class.java) {
            ExpenseValidator.parseAndValidateAmount(value, field)
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
        assertEquals(true, error.message?.contains(field))
        assertEquals(true, error.message?.contains(expectedMessage))
    }

    @Test
    fun `rejects duplicate payer participant IDs`() {
        val duplicatePayers = listOf(
            PayerDto(alice.toString(), MoneyDto("EUR", "50")),
            PayerDto(alice.toString(), MoneyDto("EUR", "50"))
        )
        val error = assertThrows(SquarewiseException::class.java) {
            ExpenseValidator.validatePayers(duplicatePayers, "EUR")
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
        assertEquals(true, error.message?.contains("participant IDs must be unique"))
    }

    @Test
    fun `rejects payer amounts that cause 64-bit integer overflow`() {
        val overflowingPayers = listOf(
            PayerDto(alice.toString(), MoneyDto("EUR", Long.MAX_VALUE.toString())),
            PayerDto(bob.toString(), MoneyDto("EUR", "1"))
        )
        val error = assertThrows(SquarewiseException::class.java) {
            ExpenseValidator.validatePayers(overflowingPayers, "EUR")
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
        assertEquals(true, error.message?.contains("exceeds maximum allowed value"))
    }
}
