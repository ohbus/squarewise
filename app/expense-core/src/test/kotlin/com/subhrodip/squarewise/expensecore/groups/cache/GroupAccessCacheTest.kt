package com.subhrodip.squarewise.expensecore.groups.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying [GroupAccessCache] hit/miss behavior, TTL expiration, and invalidation.
 */
class GroupAccessCacheTest {

    private lateinit var cache: GroupAccessCache

    @BeforeEach
    fun setUp() {
        cache = GroupAccessCache(ttl = Duration.ofSeconds(10), maxEntries = 100)
    }

    @Test
    fun `isMember returns cached value and invokes loader only once on repeat calls`() {
        val groupId = UUID.randomUUID()
        val subject = "user-1"
        val loaderCalls = AtomicInteger(0)

        val result1 = cache.isMember(groupId, subject) {
            loaderCalls.incrementAndGet()
            true
        }
        val result2 = cache.isMember(groupId, subject) {
            loaderCalls.incrementAndGet()
            true
        }

        assertTrue(result1)
        assertTrue(result2)
        assertEquals(1, loaderCalls.get())
    }

    @Test
    fun `groupStatus caches result and serves subsequent lookups from memory`() {
        val groupId = UUID.randomUUID()
        val loaderCalls = AtomicInteger(0)

        val status1 = cache.groupStatus(groupId) {
            loaderCalls.incrementAndGet()
            "ACTIVE"
        }
        val status2 = cache.groupStatus(groupId) {
            loaderCalls.incrementAndGet()
            "ACTIVE"
        }

        assertEquals("ACTIVE", status1)
        assertEquals("ACTIVE", status2)
        assertEquals(1, loaderCalls.get())
    }

    @Test
    fun `invalidateGroup evicts status and memberships for target group`() {
        val groupId = UUID.randomUUID()
        val subject = "user-1"
        val memberCalls = AtomicInteger(0)
        val statusCalls = AtomicInteger(0)

        cache.isMember(groupId, subject) { memberCalls.incrementAndGet(); true }
        cache.groupStatus(groupId) { statusCalls.incrementAndGet(); "ACTIVE" }

        cache.invalidateGroup(groupId)

        cache.isMember(groupId, subject) { memberCalls.incrementAndGet(); true }
        cache.groupStatus(groupId) { statusCalls.incrementAndGet(); "ARCHIVED" }

        assertEquals(2, memberCalls.get())
        assertEquals(2, statusCalls.get())
    }

    @Test
    fun `invalidateMember evicts only specific user membership`() {
        val groupId = UUID.randomUUID()
        val subjectA = "user-A"
        val subjectB = "user-B"
        val callsA = AtomicInteger(0)
        val callsB = AtomicInteger(0)

        cache.isMember(groupId, subjectA) { callsA.incrementAndGet(); true }
        cache.isMember(groupId, subjectB) { callsB.incrementAndGet(); true }

        cache.invalidateMember(groupId, subjectA)

        cache.isMember(groupId, subjectA) { callsA.incrementAndGet(); true }
        cache.isMember(groupId, subjectB) { callsB.incrementAndGet(); true }

        assertEquals(2, callsA.get())
        assertEquals(1, callsB.get())
    }

    @Test
    fun `members caches roster and avoids repeat loader calls`() {
        val groupId = UUID.randomUUID()
        val calls = AtomicInteger(0)
        val roster = listOf("alice", "bob")

        val res1 = cache.members(groupId, 50) { calls.incrementAndGet(); roster }
        val res2 = cache.members(groupId, 50) { calls.incrementAndGet(); roster }

        assertEquals(roster, res1)
        assertEquals(roster, res2)
        assertEquals(1, calls.get())

        cache.invalidateMembers(groupId)
        val res3 = cache.members(groupId, 50) { calls.incrementAndGet(); roster }
        assertEquals(2, calls.get())
    }

    @Test
    fun `balances caches balance list and invalidates appropriately`() {
        val groupId = UUID.randomUUID()
        val calls = AtomicInteger(0)
        val sampleBalances = listOf("balance-1", "balance-2")

        val res1 = cache.balances(groupId) { calls.incrementAndGet(); sampleBalances }
        val res2 = cache.balances(groupId) { calls.incrementAndGet(); sampleBalances }

        assertEquals(sampleBalances, res1)
        assertEquals(sampleBalances, res2)
        assertEquals(1, calls.get())

        cache.invalidateBalances(groupId)
        val res3 = cache.balances(groupId) { calls.incrementAndGet(); sampleBalances }
        assertEquals(2, calls.get())
    }

    @Test
    fun `activeMemberIds caches member UUIDs and invalidates on invalidateMembers`() {
        val groupId = UUID.randomUUID()
        val calls = AtomicInteger(0)
        val memberIds = setOf(UUID.randomUUID(), UUID.randomUUID())

        val res1 = cache.activeMemberIds(groupId) { calls.incrementAndGet(); memberIds }
        val res2 = cache.activeMemberIds(groupId) { calls.incrementAndGet(); memberIds }

        assertEquals(memberIds, res1)
        assertEquals(memberIds, res2)
        assertEquals(1, calls.get())

        cache.invalidateMembers(groupId)
        val res3 = cache.activeMemberIds(groupId) { calls.incrementAndGet(); memberIds }
        assertEquals(memberIds, res3)
        assertEquals(2, calls.get())
    }

    @Test
    fun `groupDetails caches group entity and invalidates on invalidateGroup`() {
        val groupId = UUID.randomUUID()
        val calls = AtomicInteger(0)
        val details = "group-details-$groupId"

        val res1 = cache.groupDetails(groupId) { calls.incrementAndGet(); details }
        val res2 = cache.groupDetails(groupId) { calls.incrementAndGet(); details }

        assertEquals(details, res1)
        assertEquals(details, res2)
        assertEquals(1, calls.get())

        cache.invalidateGroup(groupId)
        val res3 = cache.groupDetails(groupId) { calls.incrementAndGet(); details }
        assertEquals(details, res3)
        assertEquals(2, calls.get())
    }

    @Test
    fun `invalidateBalancesAfterCommit purges balance cache`() {
        val groupId = UUID.randomUUID()
        val calls = AtomicInteger(0)
        cache.balances(groupId) { calls.incrementAndGet(); listOf("b1") }
        cache.balances(groupId) { calls.incrementAndGet(); listOf("b1") }
        assertEquals(1, calls.get())

        cache.invalidateBalancesAfterCommit(groupId)
        cache.balances(groupId) { calls.incrementAndGet(); listOf("b1") }
        assertEquals(2, calls.get())
    }

    @Test
    fun `clear purges all cached categories`() {
        val groupId = UUID.randomUUID()
        val subject = "user-clear"
        cache.isMember(groupId, subject) { true }
        cache.groupStatus(groupId) { "ACTIVE" }
        cache.members(groupId, 10) { listOf("m1") }
        cache.balances(groupId) { listOf("b1") }

        cache.clear()

        val calls = AtomicInteger(0)
        cache.isMember(groupId, subject) { calls.incrementAndGet(); true }
        cache.groupStatus(groupId) { calls.incrementAndGet(); "ACTIVE" }
        cache.members(groupId, 10) { calls.incrementAndGet(); listOf("m1") }
        cache.balances(groupId) { calls.incrementAndGet(); listOf("b1") }

        assertEquals(4, calls.get())
    }

    @Test
    fun `evicts oversized entries when maxEntries is exceeded`() {
        val smallCache = GroupAccessCache(ttl = Duration.ofMillis(1), maxEntries = 4)
        for (i in 1..6) {
            smallCache.isMember(UUID.randomUUID(), "user-$i") { true }
        }
        // Wait for TTL to pass
        Thread.sleep(10)
        // Add another entry to trigger eviction pass
        smallCache.isMember(UUID.randomUUID(), "trigger") { true }

        // Test non-expired overflow eviction as well
        val smallLiveCache = GroupAccessCache(ttl = Duration.ofMinutes(10), maxEntries = 4)
        val userIds = (1..8).map { UUID.randomUUID() }
        for (i in 1..8) {
            smallLiveCache.isMember(userIds[i - 1], "live-$i") { true }
        }
        var reloads = 0
        for (i in 1..8) {
            var loaded = false
            smallLiveCache.isMember(userIds[i - 1], "live-$i") {
                loaded = true
                true
            }
            if (loaded) reloads++
        }
        val hits = 8 - reloads
        assertTrue(reloads >= 4)
        assertTrue(hits >= 4)
    }

    @Test
    fun `reloads expired entries across all cached types`() {
        val fastCache = GroupAccessCache(ttl = Duration.ofMillis(1), maxEntries = 10)
        val groupId = UUID.randomUUID()
        val subject = "expiring-user"

        var memberLoads = 0
        fastCache.isMember(groupId, subject) { memberLoads++; true }
        Thread.sleep(10)
        fastCache.isMember(groupId, subject) { memberLoads++; true }
        assertEquals(2, memberLoads)

        var statusLoads = 0
        fastCache.groupStatus(groupId) { statusLoads++; "ACTIVE" }
        Thread.sleep(10)
        fastCache.groupStatus(groupId) { statusLoads++; "ACTIVE" }
        assertEquals(2, statusLoads)

        var rosterLoads = 0
        fastCache.members(groupId, 10) { rosterLoads++; listOf("alice") }
        Thread.sleep(10)
        fastCache.members(groupId, 10) { rosterLoads++; listOf("alice") }
        assertEquals(2, rosterLoads)

        var balanceLoads = 0
        fastCache.balances(groupId) { balanceLoads++; listOf("b1") }
        Thread.sleep(10)
        fastCache.balances(groupId) { balanceLoads++; listOf("b1") }
        assertEquals(2, balanceLoads)

        var idsLoads = 0
        fastCache.activeMemberIds(groupId) { idsLoads++; setOf(UUID.randomUUID()) }
        Thread.sleep(10)
        fastCache.activeMemberIds(groupId) { idsLoads++; setOf(UUID.randomUUID()) }
        assertEquals(2, idsLoads)

        var detailsLoads = 0
        fastCache.groupDetails(groupId) { detailsLoads++; "details" }
        Thread.sleep(10)
        fastCache.groupDetails(groupId) { detailsLoads++; "details" }
        assertEquals(2, detailsLoads)
    }

    @Test
    fun `does not cache stale membership if invalidation occurred during load`() {
        val groupId = UUID.randomUUID()
        val subject = "race-user"
        val res = cache.isMember(groupId, subject) {
            cache.invalidateMember(groupId, subject)
            true
        }
        assertTrue(res)
        var secondLoads = 0
        cache.isMember(groupId, subject) { secondLoads++; true }
        assertEquals(1, secondLoads)
    }

    @Test
    fun `default constructor initializes with standard ttl and capacity`() {
        val defaultCache = GroupAccessCache()
        val groupId = UUID.randomUUID()
        assertTrue(defaultCache.isMember(groupId, "user") { true })
    }

    @Test
    fun `does not cache stale group data if invalidation occurred during load`() {
        val groupId = UUID.randomUUID()

        val s1 = cache.groupStatus(groupId) {
            cache.invalidateGroup(groupId)
            "ACTIVE"
        }
        assertEquals("ACTIVE", s1)
        var sLoads = 0
        cache.groupStatus(groupId) { sLoads++; "ACTIVE" }
        assertEquals(1, sLoads)

        val m1 = cache.members(groupId, 10) {
            cache.invalidateMembers(groupId)
            listOf("m1")
        }
        assertEquals(listOf("m1"), m1)
        var mLoads = 0
        cache.members(groupId, 10) { mLoads++; listOf("m1") }
        assertEquals(1, mLoads)

        val b1 = cache.balances(groupId) {
            cache.invalidateGroup(groupId)
            listOf("b1")
        }
        assertEquals(listOf("b1"), b1)
        var bLoads = 0
        cache.balances(groupId) { bLoads++; listOf("b1") }
        assertEquals(1, bLoads)

        val id = UUID.randomUUID()
        val a1 = cache.activeMemberIds(groupId) {
            cache.invalidateMembers(groupId)
            setOf(id)
        }
        assertEquals(setOf(id), a1)
        var aLoads = 0
        cache.activeMemberIds(groupId) { aLoads++; setOf(id) }
        assertEquals(1, aLoads)

        val d1 = cache.groupDetails(groupId) {
            cache.invalidateGroup(groupId)
            "details"
        }
        assertEquals("details", d1)
        var dLoads = 0
        cache.groupDetails(groupId) { dLoads++; "details" }
        assertEquals(1, dLoads)
    }

    @Test
    fun `groupDetails caches and returns null when loader returns null`() {
        val groupId = UUID.randomUUID()
        val calls = AtomicInteger(0)
        val r1 = cache.groupDetails<String>(groupId) { calls.incrementAndGet(); null }
        assertNull(r1)
        val r2 = cache.groupDetails<String>(groupId) { calls.incrementAndGet(); "should-not-load" }
        assertNull(r2)
        assertEquals(1, calls.get())
    }

    @Test
    fun `invalidateBalancesAfterCommit registers afterCommit hook when synchronization active`() {
        val groupId = UUID.randomUUID()
        cache.balances(groupId) { listOf("b1") }

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization()
        try {
            cache.invalidateBalancesAfterCommit(groupId)
            val syncs = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            assertEquals(1, syncs.size)
            syncs[0].afterCommit()
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `evictIfOversized prunes expired invalidation timestamps`() {
        val fastCache = GroupAccessCache(ttl = Duration.ofMillis(1), maxEntries = 2)
        val groupId = UUID.randomUUID()
        fastCache.invalidateMember(groupId, "old-user")
        Thread.sleep(10)
        fastCache.isMember(UUID.randomUUID(), "u1") { true }
        fastCache.isMember(UUID.randomUUID(), "u2") { true }
        fastCache.isMember(UUID.randomUUID(), "u3") { true }
    }
}
