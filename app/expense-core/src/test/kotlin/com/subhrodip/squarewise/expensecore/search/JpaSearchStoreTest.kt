package com.subhrodip.squarewise.expensecore.search

import com.subhrodip.squarewise.expensecore.categories.ExpenseCategory
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.expensecore.search.api.SearchQuery
import com.subhrodip.squarewise.expensecore.search.model.SearchExpense
import com.subhrodip.squarewise.expensecore.search.model.ExpenseSearch
import com.subhrodip.squarewise.expensecore.search.persistence.JpaSearchStore
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.ExpenseRepository
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository

import com.subhrodip.squarewise.ids.generation.UuidGenerator
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/**
 * Integration test verifying [JpaSearchStore] queries against a real database.
 */
@SpringBootTest
@Transactional
class JpaSearchStoreTest @Autowired constructor(
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val searchStore: JpaSearchStore
) {

    @Test
    fun `finds active expenses and maps categories while ignoring deleted expenses`() {
        // Create and persist a group to satisfy foreign key constraint
        val groupId = UUID.randomUUID()
        val group = GroupEntity(
            groupId = groupId,
            name = "Test Group",
            kind = "HOUSEHOLD",
            currency = "EUR"
        )
        groupRepository.save(group)

        val expenseId1 = UuidGenerator.next()
        val expenseId2 = UuidGenerator.next()
        val deletedExpenseId = UuidGenerator.next()

        val active1 = ExpenseEntity(
            expenseId = expenseId1,
            groupId = groupId,
            description = "Museum tickets",
            category = "entertainment",
            currency = "EUR",
            amountMinor = 2400,
            version = 1,
            allocationMode = "EQUAL",
            createdAt = Instant.now().minusSeconds(100),
            deleted = false
        )
        val active2 = ExpenseEntity(
            expenseId = expenseId2,
            groupId = groupId,
            description = "Train tickets",
            category = "transport",
            currency = "EUR",
            amountMinor = 5000,
            version = 1,
            allocationMode = "EQUAL",
            createdAt = Instant.now(),
            deleted = false
        )
        val deleted = ExpenseEntity(
            expenseId = deletedExpenseId,
            groupId = groupId,
            description = "Cancelled tour",
            category = "other",
            currency = "EUR",
            amountMinor = 3000,
            version = 1,
            allocationMode = "EQUAL",
            createdAt = Instant.now(),
            deleted = true
        )

        expenseRepository.saveAll(listOf(active1, active2, deleted))

        val results = searchStore.findSearchExpenses(SearchQuery(groupId))
        assertEquals(2, results.size)
        // Stable UUID ordering is the keyset cursor order used by ExpenseSearch.
        val expected = listOf(active1, active2).sortedBy { it.expenseId.toString() }
        assertEquals(expected[0].expenseId.toString(), results[0].expenseId)
        assertEquals(expected[0].description, results[0].description)
        assertEquals(expected[0].amountMinor.toString(), results[0].amountMinor)
        assertEquals(expected[1].expenseId.toString(), results[1].expenseId)
        assertEquals(expected[1].description, results[1].description)
    }

    /** Verifies legacy categories fall back safely and opaque cursors reach the JPA query. */
    @Test
    fun `maps unknown category to other and decodes a non-null cursor`() {
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(groupId, "Legacy Group", "HOUSEHOLD", "EUR"))
        val expenseId = UuidGenerator.next()
        expenseRepository.save(
            ExpenseEntity(
                expenseId = expenseId,
                groupId = groupId,
                description = "Legacy expense",
                category = "legacy-category",
                currency = "EUR",
                amountMinor = 1250,
                version = 1,
                allocationMode = "EQUAL",
                createdAt = Instant.now(),
                deleted = false
            )
        )

        val results = searchStore.findSearchExpenses(SearchQuery(groupId))

        assertEquals(1, results.size)
        assertEquals(ExpenseCategory.OTHER, results.single().category)

        val cursor = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(expenseId.toString().toByteArray(StandardCharsets.UTF_8))
        assertEquals(
            emptyList<SearchExpense>(),
            searchStore.findSearchExpenses(SearchQuery(groupId, afterExpenseId = cursor))
        )
    }

    @Test
    fun `treats SQL wildcard characters as literal search text`() {
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(groupId, "Wildcard", "HOUSEHOLD", "EUR"))
        expenseRepository.saveAll(
            listOf(
                ExpenseEntity(
                    expenseId = UuidGenerator.next(),
                    groupId = groupId,
                    description = "Plain expense",
                    category = "other",
                    currency = "EUR",
                    amountMinor = 1250,
                    version = 1,
                    allocationMode = "EQUAL",
                    createdAt = Instant.now(),
                    deleted = false
                ),
                ExpenseEntity(
                    expenseId = UuidGenerator.next(),
                    groupId = groupId,
                    description = "100% meal",
                    category = "other",
                    currency = "EUR",
                    amountMinor = 1250,
                    version = 1,
                    allocationMode = "EQUAL",
                    createdAt = Instant.now(),
                    deleted = false
                )
            )
        )

        val results = searchStore.findSearchExpenses(SearchQuery(groupId, text = "%"))

        assertEquals(1, results.size)
        assertEquals("100% meal", results.single().description)
    }

    @Test
    fun `escapes underscore and backslash wildcards in search query text`() {
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(groupId, "Escapes", "HOUSEHOLD", "EUR"))
        expenseRepository.save(
            ExpenseEntity(
                expenseId = UuidGenerator.next(),
                groupId = groupId,
                description = "item_with_underscore",
                category = "other",
                currency = "EUR",
                amountMinor = 100,
                version = 1,
                allocationMode = "EQUAL",
                createdAt = Instant.now(),
                deleted = false
            )
        )

        val resultsUnderscore = searchStore.findSearchExpenses(SearchQuery(groupId, text = "_"))
        assertEquals(1, resultsUnderscore.size)

        val resultsBackslash = searchStore.findSearchExpenses(SearchQuery(groupId, text = "\\"))
        assertEquals(0, resultsBackslash.size)
    }

    @Test
    fun `rejects invalid cursor format`() {
        val groupId = UUID.randomUUID()
        val invalidCursor = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("not-a-valid-uuid".toByteArray(StandardCharsets.UTF_8))

        assertThrows(ExpenseDomainException::class.java) {
            searchStore.findSearchExpenses(SearchQuery(groupId, afterExpenseId = invalidCursor))
        }
    }

    /** Verifies the persistent adapter enforces both sides of its bounded query contract. */
    @Test
    fun `rejects search limits outside the export bounds`() {
        val groupId = UUID.randomUUID()

        assertThrows(IllegalArgumentException::class.java) {
            searchStore.findSearchExpenses(SearchQuery(groupId, limit = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            searchStore.findSearchExpenses(SearchQuery(groupId, limit = ExpenseSearch.MAX_EXPORT_ROWS + 1))
        }
    }
}
