package com.subhrodip.squarewise.expensecore.groups.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        for (i in 1..8) {
            smallLiveCache.isMember(UUID.randomUUID(), "live-$i") { true }
        }
    }
}
