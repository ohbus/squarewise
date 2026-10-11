package com.subhrodip.squarewise.expensecore.settlements

import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import com.subhrodip.squarewise.expensecore.settlements.persistence.JpaSettlementStore
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.jdbc.core.JdbcTemplate
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.transaction.annotation.Transactional

/**
 * Runs the settlement reconciliation invariants against PostgreSQL rather than
 * an embedded database. Enable with `SQUAREWISE_POSTGRES_TESTS=true`.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "SQUAREWISE_POSTGRES_TESTS", matches = "true")
@Transactional
class PostgresSettlementReconciliationTest @Autowired constructor(
    private val store: JpaSettlementStore,
    private val groupRepository: GroupRepository,
    private val membershipRepository: GroupMembershipRepository,
    private val jdbc: JdbcTemplate
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Test
    fun `reconciles recorded and reversed settlement postings`() {
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(groupId, "Postgres settlement reconciliation", "HOUSEHOLD", "EUR"))
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        membershipRepository.save(GroupMembershipEntity(UUID.randomUUID(), groupId, "test-actor"))
        membershipRepository.save(GroupMembershipEntity(from, groupId, "from"))
        membershipRepository.save(GroupMembershipEntity(to, groupId, "to"))
        val recorded = Settlement(UUID.randomUUID(), from, to, 1_250, "EUR")
        val reversed = Settlement(UUID.randomUUID(), from, to, 2_500, "EUR")

        store.record(groupId, recorded, "test-actor")
        store.record(groupId, reversed, "test-actor")
        store.reverse(groupId, reversed.id, "reconciliation", "test-actor")
        entityManager.flush()

        assertEquals(2L, postingCount(recorded.id))
        assertEquals(4L, postingCount(reversed.id))
        assertEquals(0L, netAmount(recorded.id))
        assertEquals(0L, netAmount(reversed.id))
        assertEquals(1_250L, signedAmount(recorded.id, recorded.fromParticipantId))
        assertEquals(-1_250L, signedAmount(recorded.id, recorded.toParticipantId))
        assertEquals(2_500L, signedAmount(reversed.id, reversed.fromParticipantId))
        assertEquals(-2_500L, signedAmount(reversed.id, reversed.toParticipantId))
    }

    private fun postingCount(settlementId: UUID): Long = jdbc.queryForObject(
        "SELECT count(*) FROM balance_postings WHERE settlement_id = ?",
        Long::class.java,
        settlementId
    ) ?: 0L

    private fun netAmount(settlementId: UUID): Long = jdbc.queryForObject(
        "SELECT COALESCE(sum(amount_minor), 0) FROM balance_postings WHERE settlement_id = ?",
        Long::class.java,
        settlementId
    ) ?: 0L

    private fun signedAmount(settlementId: UUID, participantId: UUID): Long = jdbc.queryForObject(
        "SELECT amount_minor FROM balance_postings WHERE settlement_id = ? AND participant_id = ? ORDER BY created_at ASC LIMIT 1",
        Long::class.java,
        settlementId,
        participantId
    ) ?: 0L
}
