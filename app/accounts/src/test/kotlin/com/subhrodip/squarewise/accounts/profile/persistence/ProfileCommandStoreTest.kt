package com.subhrodip.squarewise.accounts.profile.persistence

import com.subhrodip.squarewise.accounts.profile.api.ProfilePatchRequest
import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** Verifies default argument dispatch for ProfileCommandStore. */
class ProfileCommandStoreTest {

    private class RecordingProfileCommandStore : ProfileCommandStore {
        var recordedTimezone: String? = null
        var recordedDefaultCurrency: String? = null

        override fun create(
            accountId: UUID,
            subject: String,
            displayName: String,
            timezone: String,
            defaultCurrency: String
        ): ProfileResponse {
            recordedTimezone = timezone
            recordedDefaultCurrency = defaultCurrency
            return ProfileResponse(accountId, displayName, timezone, defaultCurrency)
        }

        override fun update(subject: String, patch: ProfilePatchRequest): ProfileResponse =
            error("Not implemented for test")

        override fun requestDeletion(subject: String) = Unit
    }

    @Test
    fun `default arguments for timezone and defaultCurrency are applied`() {
        val store: ProfileCommandStore = RecordingProfileCommandStore()
        val accountId = UUID.randomUUID()
        val response = store.create(accountId, "sub-1", "User One")

        assertEquals("UTC", (store as RecordingProfileCommandStore).recordedTimezone)
        assertEquals("EUR", store.recordedDefaultCurrency)
        assertEquals("UTC", response.timezone)
        assertEquals("EUR", response.defaultCurrency)
    }
}
