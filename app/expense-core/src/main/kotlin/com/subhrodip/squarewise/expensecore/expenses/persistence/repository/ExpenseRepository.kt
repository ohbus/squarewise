package com.subhrodip.squarewise.expensecore.expenses.persistence.repository

import org.springframework.data.jpa.repository.Query
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseEntity

import java.util.UUID
import com.subhrodip.squarewise.expensecore.search.model.SearchExpenseProjection
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for [ExpenseEntity].
 *
 * Provides query methods for retrieving non-deleted expenses by group and category.
 */
@Repository
interface ExpenseRepository : JpaRepository<ExpenseEntity, UUID> {
    /** Bounded scalar search projection for the reader-eligible search path. */
    @Query(
        value = """
            SELECT CAST(e.expense_id AS VARCHAR) AS expenseId, e.description AS description,
                   e.category AS category, e.currency AS currency,
                   e.amount_minor AS amountMinor
              FROM expenses e
             WHERE e.group_id = :groupId AND e.deleted = FALSE
               AND (:text = '' OR LOWER(e.description) LIKE CONCAT('%', LOWER(:text), '%'))
               AND (:currency IS NULL OR e.currency = :currency)
               AND (:category IS NULL OR e.category = :category)
               AND (:afterExpenseId IS NULL OR CAST(e.expense_id AS VARCHAR) > :afterExpenseId)
             ORDER BY e.expense_id ASC
        """,
        nativeQuery = true
    )
    fun searchProjection(
        groupId: UUID,
        text: String,
        currency: String?,
        category: String?,
        afterExpenseId: String?,
        pageable: Pageable
    ): List<SearchExpenseProjection>

    /**
     * Retrieves active (non-deleted) expenses in a group ordered by creation date descending.
     */
    fun findByGroupIdAndDeletedFalseOrderByCreatedAtDesc(groupId: UUID, pageable: Pageable): List<ExpenseEntity>

    /**
     * Retrieves active (non-deleted) expenses in a group filtered by category ordered by creation date descending.
     */
    fun findByGroupIdAndCategoryAndDeletedFalseOrderByCreatedAtDesc(
        groupId: UUID,
        category: String,
        pageable: Pageable
    ): List<ExpenseEntity>

    /**
     * Finds an expense by ID and group ID.
     */
    fun findByExpenseIdAndGroupId(expenseId: UUID, groupId: UUID): ExpenseEntity?
}
