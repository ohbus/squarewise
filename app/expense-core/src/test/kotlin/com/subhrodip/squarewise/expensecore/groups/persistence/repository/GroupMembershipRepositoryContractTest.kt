package com.subhrodip.squarewise.expensecore.groups.persistence.repository

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** Verifies repository convenience methods consistently constrain queries to active members. */
class GroupMembershipRepositoryContractTest {
    @Test
    fun `active membership convenience methods delegate with active status`() {
        val groupId = UUID.randomUUID()
        val subject = "oidc|alice"
        var existsCalledWith: Triple<UUID, String, String>? = null
        var findAllCalledWith: Pair<String, String>? = null
        var findByGroupCalledWith: Pair<UUID, String>? = null

        val stub = object : GroupMembershipRepository {
            override fun existsByGroupIdAndSubjectAndStatus(groupId: UUID, subject: String, status: String): Boolean {
                existsCalledWith = Triple(groupId, subject, status)
                return true
            }

            override fun findAllBySubjectAndStatusOrderByMembershipId(subject: String, status: String): List<com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> {
                findAllCalledWith = Pair(subject, status)
                return emptyList()
            }

            override fun findByGroupIdAndStatus(groupId: UUID, status: String): List<com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> {
                findByGroupCalledWith = Pair(groupId, status)
                return emptyList()
            }

            override fun findByMembershipIdAndGroupId(membershipId: UUID, groupId: UUID): com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity? = null
            override fun findByGroupIdAndSubjectAndStatus(groupId: UUID, subject: String, status: String): com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity? = null

            // JpaRepository unneeded stubs
            override fun <S : com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> save(entity: S): S = entity
            override fun <S : com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> saveAll(entities: Iterable<S>): List<S> = entities.toList()
            override fun findById(id: UUID): java.util.Optional<com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> = java.util.Optional.empty()
            override fun existsById(id: UUID): Boolean = false
            override fun findAll(): List<com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> = emptyList()
            override fun findAllById(ids: Iterable<UUID>): List<com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> = emptyList()
            override fun count(): Long = 0
            override fun deleteById(id: UUID) {}
            override fun delete(entity: com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity) {}
            override fun deleteAllById(ids: Iterable<UUID>) {}
            override fun deleteAll(entities: Iterable<com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity>) {}
            override fun deleteAll() {}
            override fun flush() {}
            override fun <S : com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> saveAndFlush(entity: S): S = entity
            override fun <S : com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> saveAllAndFlush(entities: Iterable<S>): List<S> = entities.toList()
            override fun deleteAllInBatch(entities: Iterable<com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity>) {}
            override fun deleteAllByIdInBatch(ids: Iterable<UUID>) {}
            override fun deleteAllInBatch() {}
            override fun getOne(id: UUID): com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity = throw UnsupportedOperationException()
            override fun getById(id: UUID): com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity = throw UnsupportedOperationException()
            override fun getReferenceById(id: UUID): com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity = throw UnsupportedOperationException()
            override fun <S : com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> findAll(example: org.springframework.data.domain.Example<S>): List<S> = emptyList()
            override fun <S : com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> findAll(example: org.springframework.data.domain.Example<S>, sort: org.springframework.data.domain.Sort): List<S> = emptyList()
            override fun <S : com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> findAll(example: org.springframework.data.domain.Example<S>, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<S> = org.springframework.data.domain.PageImpl(emptyList())
            override fun <S : com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> count(example: org.springframework.data.domain.Example<S>): Long = 0
            override fun <S : com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> exists(example: org.springframework.data.domain.Example<S>): Boolean = false
            override fun <S : com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> findOne(example: org.springframework.data.domain.Example<S>): java.util.Optional<S> = java.util.Optional.empty()
            override fun <S : com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity, R : Any?> findBy(example: org.springframework.data.domain.Example<S>, queryFunction: java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R>): R = throw UnsupportedOperationException()
            override fun findAll(sort: org.springframework.data.domain.Sort): List<com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> = emptyList()
            override fun findAll(pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity> = org.springframework.data.domain.PageImpl(emptyList())
        }

        assertTrue(stub.existsByGroupIdAndSubject(groupId, subject))
        assertEquals(emptyList<Any>(), stub.findAllBySubjectOrderByMembershipId(subject))
        assertEquals(emptyList<Any>(), stub.findByGroupId(groupId))

        assertEquals(Triple(groupId, subject, "ACTIVE"), existsCalledWith)
        assertEquals(Pair(subject, "ACTIVE"), findAllCalledWith)
        assertEquals(Pair(groupId, "ACTIVE"), findByGroupCalledWith)
    }
}
