package com.subhrodip.squarewise.expensecore.expenses

import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.expensecore.expenses.domain.AllocationCalculator
import com.subhrodip.squarewise.expensecore.expenses.domain.FinancialArithmetic
import com.subhrodip.squarewise.expensecore.expenses.api.request.AllocationItemDto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AllocationCalculatorTest {
    @Test
    fun `equal split assigns remainders by participant id`() {
        val result = AllocationCalculator.equal(100, listOf("c", "a", "b"))
        assertEquals(mapOf("a" to 34L, "b" to 33L, "c" to 33L), result)
        assertEquals(100L, result.values.sum())
    }

    @Test
    fun `percentage split preserves exact total`() {
        val result = AllocationCalculator.percentage(101, mapOf("a" to 3333, "b" to 3333, "c" to 3334))
        assertEquals(101L, result.values.sum())
        assertEquals(mapOf("a" to 34L, "b" to 33L, "c" to 34L), result)
    }

    @Test
    fun `invalid percentages are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.percentage(100, mapOf("a" to 5000, "b" to 4000))
        }
    }

    @Test
    fun `empty and duplicate participants are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { AllocationCalculator.equal(1, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { AllocationCalculator.equal(1, listOf("a", "a")) }
    }

    @Test
    fun `rejects multiplication and aggregation overflow`() {
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.percentage(Long.MAX_VALUE, mapOf("a" to 10_000L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.exact(Long.MAX_VALUE, mapOf("a" to Long.MAX_VALUE, "b" to 1L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FinancialArithmetic.add(Long.MAX_VALUE, 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FinancialArithmetic.negate(Long.MIN_VALUE)
        }
    }

    /** Negative totals, including the equal-split path, cannot create postings. */
    @Test
    fun `rejects negative totals and negative allocation values`() {
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.equal(-1, listOf("a"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.exact(10, mapOf("a" to -1L, "b" to 11L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.exact(-1, mapOf("a" to 1L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.percentage(10, mapOf("a" to -1L, "b" to 10_001L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.percentage(-1, mapOf("a" to 10_000L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.weightedShares(10, mapOf("a" to -1L, "b" to 2L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.weightedShares(-1, mapOf("a" to 1L))
        }
    }

    /** Exact allocation requires a non-empty map whose values sum exactly once. */
    @Test
    fun `exact allocation rejects empty and mismatched totals`() {
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.exact(0, emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.exact(10, mapOf("a" to 9L))
        }
        assertEquals(mapOf("a" to 0L, "b" to 0L), AllocationCalculator.exact(0, mapOf("a" to 0L, "b" to 0L)))
    }

    /** Percentage allocation accepts zero totals but still requires exactly 10,000 basis points. */
    @Test
    fun `percentage allocation handles zero totals and rejects empty input`() {
        assertEquals(
            mapOf("a" to 0L, "b" to 0L),
            AllocationCalculator.percentage(0, mapOf("a" to 5_000L, "b" to 5_000L))
        )
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.percentage(10, emptyMap())
        }
    }

    /** Equal remainder fractions are resolved by participant ID for deterministic retries. */
    @Test
    fun `percentage tie remainder uses lexicographic participant order`() {
        val result = AllocationCalculator.percentage(2, mapOf("b" to 5_000L, "a" to 5_000L))

        assertEquals(mapOf("a" to 1L, "b" to 1L), result)
        assertEquals(2L, result.values.sum())
        assertEquals(
            mapOf("a" to 50L, "b" to 50L),
            AllocationCalculator.percentage(100, mapOf("a" to 5_000L, "b" to 5_000L))
        )
    }

    /** Weighted shares reject zero denominators and preserve the total after remainder allocation. */
    @Test
    fun `weighted shares preserve total and reject zero denominator`() {
        val result = AllocationCalculator.weightedShares(100, mapOf("c" to 3L, "a" to 1L, "b" to 2L))

        assertEquals(mapOf("a" to 17L, "b" to 33L, "c" to 50L), result)
        assertEquals(100L, result.values.sum())
        assertEquals(
            mapOf("a" to 1L, "b" to 2L, "c" to 3L),
            AllocationCalculator.weightedShares(6, mapOf("a" to 1L, "b" to 2L, "c" to 3L))
        )
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.weightedShares(10, mapOf("a" to 0L, "b" to 0L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.weightedShares(10, emptyMap())
        }
    }

    /** The public mode dispatcher selects every supported algorithm and rejects unknown modes. */
    @Test
    fun `calculate dispatches supported modes and rejects unknown mode`() {
        val exactItems = listOf(AllocationItemDto("a", "4"), AllocationItemDto("b", "6"))
        assertEquals(mapOf("a" to 5L, "b" to 5L), AllocationCalculator.calculate("equal", 10, listOf(AllocationItemDto("b", ""), AllocationItemDto("a", ""))))
        assertEquals(mapOf("a" to 4L, "b" to 6L), AllocationCalculator.calculate("EXACT", 10, exactItems))
        assertEquals(mapOf("a" to 5L, "b" to 5L), AllocationCalculator.calculate("percent_basis_points", 10, listOf(AllocationItemDto("a", "5000"), AllocationItemDto("b", "5000"))))
        assertEquals(mapOf("a" to 3L, "b" to 7L), AllocationCalculator.calculate("weighted_shares", 10, listOf(AllocationItemDto("a", "3"), AllocationItemDto("b", "7"))))
        assertThrows(SquarewiseException::class.java) {
            AllocationCalculator.calculate("unknown", 10, exactItems)
        }
    }

    @Test
    fun `calculate distinguishes overflowing allocation values from malformed values`() {
        val overflow = assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.calculate("EXACT", 1, listOf(AllocationItemDto("a", "9223372036854775808")))
        }
        assertTrue(overflow.message?.contains("64-bit") == true)

        val malformed = assertThrows(IllegalArgumentException::class.java) {
            AllocationCalculator.calculate("EXACT", 1, listOf(AllocationItemDto("a", "not-a-number")))
        }
        assertEquals("allocation value must be a valid integer", malformed.message)
    }

    /**
     * Exercises the allocation invariants across small boundary totals and input permutations.
     * The algorithms must be deterministic, non-negative, and conserve every minor unit.
     */
    @Test
    fun `allocation modes are permutation invariant and conserve every boundary total`() {
        val participants = listOf("c", "a", "b")
        val totals = listOf(0L, 1L, 2L, 5L, 100L, 101L, 999L)
        val weights = mapOf("c" to 3L, "a" to 1L, "b" to 2L)
        val percentages = mapOf("c" to 3_333L, "a" to 3_333L, "b" to 3_334L)

        totals.forEach { total ->
            val equal = AllocationCalculator.equal(total, participants)
            val weighted = AllocationCalculator.weightedShares(total, weights)
            val percentage = AllocationCalculator.percentage(total, percentages)

            assertEquals(total, equal.values.sum())
            assertEquals(total, weighted.values.sum())
            assertEquals(total, percentage.values.sum())
            assertEquals(equal, AllocationCalculator.equal(total, participants.reversed()))
            assertEquals(weighted, AllocationCalculator.weightedShares(total, weights.entries.reversed().associate { it.key to it.value }))
            assertEquals(percentage, AllocationCalculator.percentage(total, percentages.entries.reversed().associate { it.key to it.value }))
            assertEquals(true, equal.values.all { it >= 0L })
            assertEquals(true, weighted.values.all { it >= 0L })
            assertEquals(true, percentage.values.all { it >= 0L })
        }
    }

    /**
     * Exercises allocation invariants over reproducible generated inputs.
     * A fixed seed makes failures replayable while covering more combinations
     * than a short hand-written table.
     */
    @Test
    fun `generated allocation cases conserve totals and remain deterministic`() {
        val random = Random(0x5A17E)

        repeat(200) {
            val participantIds = (0 until random.nextInt(1, 6)).map { "participant-$it" }
            val shuffledIds = participantIds.shuffled(random)
            val total = random.nextLong(0, 100_000)
            val weights = participantIds.associateWith { random.nextLong(1, 1_000) }
            val percentageValues = mutableMapOf<String, Long>()
            var remainingBasisPoints = 10_000L
            participantIds.dropLast(1).forEach { participant ->
                val basisPoints = random.nextLong(0, remainingBasisPoints + 1)
                percentageValues[participant] = basisPoints
                remainingBasisPoints -= basisPoints
            }
            percentageValues[participantIds.last()] = remainingBasisPoints

            val equal = AllocationCalculator.equal(total, shuffledIds)
            val weighted = AllocationCalculator.weightedShares(total, weights)
            val percentage = AllocationCalculator.percentage(total, percentageValues)

            listOf(equal, weighted, percentage).forEach { allocation ->
                assertEquals(total, allocation.values.sum())
                assertTrue(allocation.values.all { it >= 0L })
            }
            assertEquals(equal, AllocationCalculator.equal(total, participantIds.reversed()))
            assertEquals(weighted, AllocationCalculator.weightedShares(total, weights.entries.reversed().associate { it.key to it.value }))
            assertEquals(percentage, AllocationCalculator.percentage(total, percentageValues.entries.reversed().associate { it.key to it.value }))
            assertEquals(equal, AllocationCalculator.exact(total, equal))
        }
    }

    @Test
    fun `calculate rejects duplicate participant IDs across all allocation modes`() {
        val duplicateItems = listOf(
            AllocationItemDto("alice", "50"),
            AllocationItemDto("alice", "50")
        )

        listOf("EQUAL", "EXACT", "PERCENT_BASIS_POINTS", "WEIGHTED_SHARES").forEach { mode ->
            val ex = assertThrows(IllegalArgumentException::class.java) {
                AllocationCalculator.calculate(mode, 100L, duplicateItems)
            }
            assertTrue(ex.message?.contains("participant IDs must be unique") == true, "Mode $mode should reject duplicates")
        }
    }
}
