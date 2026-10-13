package com.subhrodip.squarewise.accounts.profile.persistence

import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.accounts.profile.api.ProfilePatchRequest
import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import com.subhrodip.squarewise.accounts.profile.cache.ProfileCache
import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.UUID
import org.mockito.Mockito.times
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Unit tests verifying [JpaProfileStore] behavior with and without cache outside active transactions.
 */
class JpaProfileStoreTest {

    private val repository = mock(ProfileRepository::class.java)
    private val cache = mock(ProfileCache::class.java)
    private val cachedStore = JpaProfileStore(repository, cache)
    private val uncachedStore = JpaProfileStore(repository, null)

    @Test
    fun `get returns null when profile does not exist`() {
        val subject = "oidc|missing"
        `when`(repository.findBySubject(subject)).thenReturn(null)
        `when`(cache.getBySubject(subject, { repository.findBySubject(subject)?.let { ProfileResponse(it.accountId, it.displayName, it.timezone, it.defaultCurrency) } })).thenAnswer { invocation ->
            val loader = invocation.getArgument<() -> ProfileResponse?>(1)
            loader()
        }

        assertNull(cachedStore.get(subject))
        assertNull(uncachedStore.get(subject))
    }

    @Test
    fun `findById returns null when account does not exist`() {
        val accountId = UUID.randomUUID()
        `when`(repository.findById(accountId)).thenReturn(Optional.empty())
        `when`(cache.getByAccountId(accountId, { repository.findById(accountId).orElse(null)?.let { ProfileResponse(it.accountId, it.displayName, it.timezone, it.defaultCurrency) } })).thenAnswer { invocation ->
            val loader = invocation.getArgument<() -> ProfileResponse?>(1)
            loader()
        }

        assertNull(cachedStore.findById(accountId))
        assertNull(uncachedStore.findById(accountId))
    }

    @Test
    fun `findByIds resolves batch when all entries are cached`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val resp1 = ProfileResponse(id1, "Alice", "UTC", "USD")
        val resp2 = ProfileResponse(id2, "Bob", "UTC", "EUR")

        `when`(cache.peekByAccountId(id1)).thenReturn(resp1)
        `when`(cache.peekByAccountId(id2)).thenReturn(resp2)

        val result = cachedStore.findByIds(listOf(id1, id2))
        assertEquals(listOf(resp1, resp2), result)
        verify(repository, never()).findAllById(anyList())
    }

    @Test
    fun `update preserves existing fields when patch fields are null`() {
        val subject = "oidc|user-1"
        val accountId = UUID.randomUUID()
        val entity = ProfileEntity(
            accountId = accountId,
            subject = subject,
            displayName = "Original Name",
            timezone = "UTC",
            defaultCurrency = "EUR"
        )
        `when`(repository.findBySubject(subject)).thenReturn(entity)
        `when`(repository.save(entity)).thenReturn(entity)

        val patch = ProfilePatchRequest(displayName = null, timezone = null, defaultCurrency = null)
        val updated = cachedStore.update(subject, patch)

        assertEquals("Original Name", updated.displayName)
        assertEquals("UTC", updated.timezone)
        assertEquals("EUR", updated.defaultCurrency)
        // Outside an active transaction, invalidation is direct
        verify(cache).invalidate(subject, accountId)
    }

    @Test
    fun `update and requestDeletion throw when profile does not exist`() {
        val subject = "oidc|nonexistent"
        `when`(repository.findBySubject(subject)).thenReturn(null)

        val exUpdate = assertThrows(AccountsDomainException::class.java) {
            cachedStore.update(subject, ProfilePatchRequest(displayName = "New"))
        }
        assertSame(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND, exUpdate.definition)

        val exDelete = assertThrows(AccountsDomainException::class.java) {
            cachedStore.requestDeletion(subject)
        }
        assertSame(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND, exDelete.definition)
    }

    @Test
    fun `requestDeletion sets deletionRequested flag and invalidates cache directly`() {
        val subject = "oidc|user-2"
        val accountId = UUID.randomUUID()
        val entity = ProfileEntity(
            accountId = accountId,
            subject = subject,
            displayName = "User Two",
            timezone = "UTC",
            defaultCurrency = "USD",
            deletionRequested = false
        )
        `when`(repository.findBySubject(subject)).thenReturn(entity)
        `when`(repository.save(entity)).thenReturn(entity)

        cachedStore.requestDeletion(subject)

        assertTrue(entity.deletionRequested)
        verify(repository).save(entity)
        verify(cache).invalidate(subject, accountId)
    }

    @Test
    fun `update and requestDeletion invalidate cache in afterCommit callback when synchronization is active`() {
        val subject = "oidc|user-sync"
        val accountId = UUID.randomUUID()
        val entity = ProfileEntity(
            accountId = accountId,
            subject = subject,
            displayName = "User Sync",
            timezone = "UTC",
            defaultCurrency = "USD"
        )
        `when`(repository.findBySubject(subject)).thenReturn(entity)
        `when`(repository.save(entity)).thenReturn(entity)

        TransactionSynchronizationManager.initSynchronization()
        try {
            cachedStore.update(subject, ProfilePatchRequest(displayName = "Updated"))
            val syncs = TransactionSynchronizationManager.getSynchronizations()
            assertEquals(1, syncs.size)
            syncs.first().afterCommit()
            verify(cache).invalidate(subject, accountId)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `store instantiated with default constructor operates safely without cache`() {
        val defaultStore = JpaProfileStore(repository)
        val subject = "oidc|default"
        val accountId = UUID.randomUUID()
        val entity = ProfileEntity(
            accountId = accountId,
            subject = subject,
            displayName = "Default",
            timezone = "UTC",
            defaultCurrency = "USD"
        )
        `when`(repository.findBySubject(subject)).thenReturn(entity)
        `when`(repository.save(entity)).thenReturn(entity)

        defaultStore.update(subject, ProfilePatchRequest(displayName = "New Name"))
        defaultStore.requestDeletion(subject)
        verify(repository, times(2)).save(entity)
    }
}
