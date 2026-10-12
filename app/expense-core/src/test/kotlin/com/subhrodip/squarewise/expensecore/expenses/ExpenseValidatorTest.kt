package com.subhrodip.squarewise.expensecore.expenses

import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer

import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.expensecore.expenses.api.request.MoneyDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.PayerDto
import com.subhrodip.squarewise.expensecore.expenses.service.ExpenseValidator
import java.util.UUID
import java.time.Instant
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `shared record validation rejects overflowing recurring totals`() {
        val error = assertThrows(SquarewiseException::class.java) {
            ExpenseValidator.validateRecord(
                ExpenseRecord(
                    expenseId = UUID.randomUUID(),
                    groupId = UUID.randomUUID(),
                    description = "overflow",
                    category = "recurring",
                    currency = "EUR",
                    amountMinor = Long.MAX_VALUE,
                    version = 1,
                    allocationMode = "EXACT",
                    createdAt = Instant.EPOCH,
                    payers = listOf(ExpensePayer(alice, Long.MAX_VALUE), ExpensePayer(bob, 1)),
                    allocations = listOf(ExpenseAllocation(alice, Long.MAX_VALUE))
                )
            )
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
    }

    @Test
    fun `shared record validation rejects oversized participant lists`() {
        val participants = (1..101).map { UUID.randomUUID() }
        val error = assertThrows(SquarewiseException::class.java) {
            ExpenseValidator.validateRecord(
                ExpenseRecord(
                    expenseId = UUID.randomUUID(),
                    groupId = UUID.randomUUID(),
                    description = "too many participants",
                    category = "expense",
                    currency = "EUR",
                    amountMinor = 101,
                    version = 1,
                    allocationMode = "EQUAL",
                    createdAt = Instant.EPOCH,
                    payers = participants.map { ExpensePayer(it, 1) },
                    allocations = participants.map { ExpenseAllocation(it, 1) }
                )
            )
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
    }

    /** Verifies every ledger invariant fails before a malformed record can reach persistence. */
    @Test
    fun `shared record validation rejects each invalid ledger condition`() {
        val valid = ExpenseRecord(
            expenseId = UUID.randomUUID(),
            groupId = UUID.randomUUID(),
            description = "valid",
            category = "expense",
            currency = "EUR",
            amountMinor = 100,
            version = 1,
            allocationMode = "EXACT",
            createdAt = Instant.EPOCH,
            payers = listOf(ExpensePayer(alice, 100)),
            allocations = listOf(ExpenseAllocation(alice, 100))
        )
        val invalidRecords = listOf(
            "expense amount must be positive" to valid.copy(amountMinor = 0),
            "expense must contain at least one payer" to valid.copy(payers = emptyList()),
            "expense must contain at least one allocation" to valid.copy(allocations = emptyList()),
            "payer participant IDs must be unique" to valid.copy(
                payers = listOf(ExpensePayer(alice, 50), ExpensePayer(alice, 50))
            ),
            "allocation participant IDs must be unique" to valid.copy(
                allocations = listOf(ExpenseAllocation(alice, 50), ExpenseAllocation(alice, 50))
            ),
            "participant count exceeds the maximum" to valid.copy(
                allocations = (1..101).map { ExpenseAllocation(UUID.randomUUID(), 1) }
            ),
            "payer amounts must be positive" to valid.copy(payers = listOf(ExpensePayer(alice, -1))),
            "allocation amounts must not be negative" to valid.copy(allocations = listOf(ExpenseAllocation(alice, -1))),
            "payer amounts must equal expense amount" to valid.copy(payers = listOf(ExpensePayer(alice, 99))),
            "allocation amounts must equal expense amount" to valid.copy(allocations = listOf(ExpenseAllocation(alice, 99)))
        )

        ExpenseValidator.validateRecord(valid)
        ExpenseValidator.validateRecord(
            valid.copy(
                payers = listOf(ExpensePayer(alice, 100)),
                allocations = listOf(ExpenseAllocation(alice, 0), ExpenseAllocation(bob, 100))
            )
        )

        invalidRecords.forEach { (message, record) ->
            val error = assertThrows(SquarewiseException::class.java) {
                ExpenseValidator.validateRecord(record)
            }
            assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
            assertTrue(error.message?.contains(message) == true, "expected message: $message")
        }

        val allocationOverflow = valid.copy(
            amountMinor = Long.MAX_VALUE,
            payers = listOf(ExpensePayer(alice, Long.MAX_VALUE)),
            allocations = listOf(ExpenseAllocation(alice, Long.MAX_VALUE), ExpenseAllocation(bob, 1))
        )
        val overflowError = assertThrows(SquarewiseException::class.java) {
            ExpenseValidator.validateRecord(allocationOverflow)
        }
        assertTrue(overflowError.message?.contains("financial amount exceeds supported range") == true)

        val oversizedPayers = valid.copy(
            amountMinor = 101,
            payers = (1..101).map { ExpensePayer(UUID.randomUUID(), 1) },
            allocations = listOf(ExpenseAllocation(alice, 101))
        )
        val oversizedPayerError = assertThrows(SquarewiseException::class.java) {
            ExpenseValidator.validateRecord(oversizedPayers)
        }
        assertTrue(oversizedPayerError.message?.contains("participant count exceeds the maximum") == true)
    }

    @Test
    fun `shared record validation accepts multiple payers and zero allocations`() {
        ExpenseValidator.validateRecord(
            ExpenseRecord(
                expenseId = UUID.randomUUID(),
                groupId = UUID.randomUUID(),
                description = "multi-payer",
                category = "expense",
                currency = "EUR",
                amountMinor = 100,
                version = 1,
                allocationMode = "EXACT",
                createdAt = Instant.EPOCH,
                payers = listOf(ExpensePayer(alice, 40), ExpensePayer(bob, 60)),
                allocations = listOf(
                    ExpenseAllocation(alice, 0),
                    ExpenseAllocation(bob, 100)
                )
            )
        )
    }
}
