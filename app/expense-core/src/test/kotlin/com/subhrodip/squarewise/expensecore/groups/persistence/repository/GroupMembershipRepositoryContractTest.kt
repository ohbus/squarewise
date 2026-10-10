package com.subhrodip.squarewise.expensecore.groups.persistence.repository

import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity
import java.util.Optional
import java.util.UUID
import java.util.function.Function
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Example
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery

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

            override fun findAllBySubjectAndStatusOrderByMembershipId(subject: String, status: String): List<GroupMembershipEntity> {
                findAllCalledWith = Pair(subject, status)
                return emptyList()
            }

            override fun findActiveGroupsBySubject(subject: String, status: String): List<com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity> = emptyList()

            override fun findByGroupIdAndStatus(groupId: UUID, status: String): List<GroupMembershipEntity> {
                findByGroupCalledWith = Pair(groupId, status)
                return emptyList()
            }

            override fun findByMembershipIdAndGroupId(membershipId: UUID, groupId: UUID): GroupMembershipEntity? = null
            override fun findByMembershipIdAndGroupIdAndStatus(membershipId: UUID, groupId: UUID, status: String): GroupMembershipEntity? = null
            override fun findByGroupIdAndSubjectAndStatus(groupId: UUID, subject: String, status: String): GroupMembershipEntity? = null

            // JpaRepository unneeded stubs
            override fun <S : GroupMembershipEntity> save(entity: S): S = entity
            override fun <S : GroupMembershipEntity> saveAll(entities: Iterable<S>): List<S> = entities.toList()
            override fun findById(id: UUID): Optional<GroupMembershipEntity> = Optional.empty()
            override fun existsById(id: UUID): Boolean = false
            override fun findAll(): List<GroupMembershipEntity> = emptyList()
            override fun findAllById(ids: Iterable<UUID>): List<GroupMembershipEntity> = emptyList()
            override fun count(): Long = 0
            override fun deleteById(id: UUID) {}
            override fun delete(entity: GroupMembershipEntity) {}
            override fun deleteAllById(ids: Iterable<UUID>) {}
            override fun deleteAll(entities: Iterable<GroupMembershipEntity>) {}
            override fun deleteAll() {}
            override fun flush() {}
            override fun <S : GroupMembershipEntity> saveAndFlush(entity: S): S = entity
            override fun <S : GroupMembershipEntity> saveAllAndFlush(entities: Iterable<S>): List<S> = entities.toList()
            override fun deleteAllInBatch(entities: Iterable<GroupMembershipEntity>) {}
            override fun deleteAllByIdInBatch(ids: Iterable<UUID>) {}
            override fun deleteAllInBatch() {}
            override fun getOne(id: UUID): GroupMembershipEntity = throw UnsupportedOperationException()
            override fun getById(id: UUID): GroupMembershipEntity = throw UnsupportedOperationException()
            override fun getReferenceById(id: UUID): GroupMembershipEntity = throw UnsupportedOperationException()
            override fun <S : GroupMembershipEntity> findAll(example: Example<S>): List<S> = emptyList()
            override fun <S : GroupMembershipEntity> findAll(example: Example<S>, sort: Sort): List<S> = emptyList()
            override fun <S : GroupMembershipEntity> findAll(example: Example<S>, pageable: Pageable): Page<S> = PageImpl(emptyList())
            override fun <S : GroupMembershipEntity> count(example: Example<S>): Long = 0
            override fun <S : GroupMembershipEntity> exists(example: Example<S>): Boolean = false
            override fun <S : GroupMembershipEntity> findOne(example: Example<S>): Optional<S> = Optional.empty()
            override fun <S : GroupMembershipEntity, R : Any?> findBy(example: Example<S>, queryFunction: Function<FetchableFluentQuery<S>, R>): R = throw UnsupportedOperationException()
            override fun findAll(sort: Sort): List<GroupMembershipEntity> = emptyList()
            override fun findAll(pageable: Pageable): Page<GroupMembershipEntity> = PageImpl(emptyList())
        }

        assertTrue(stub.existsByGroupIdAndSubject(groupId, subject))
        assertEquals(emptyList<Any>(), stub.findAllBySubjectOrderByMembershipId(subject))
        assertEquals(emptyList<Any>(), stub.findByGroupId(groupId))

        assertEquals(Triple(groupId, subject, "ACTIVE"), existsCalledWith)
        assertEquals(Pair(subject, "ACTIVE"), findAllCalledWith)
        assertEquals(Pair(groupId, "ACTIVE"), findByGroupCalledWith)
    }
}
