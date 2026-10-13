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

        val customTimezone = store.create(accountId, "sub-1", "User One", timezone = "America/New_York")
        assertEquals("America/New_York", customTimezone.timezone)
        assertEquals("EUR", customTimezone.defaultCurrency)

        val customCurrency = store.create(accountId, "sub-1", "User One", defaultCurrency = "USD")
        assertEquals("UTC", customCurrency.timezone)
        assertEquals("USD", customCurrency.defaultCurrency)

        val customBoth = store.create(accountId, "sub-1", "User One", timezone = "America/New_York", defaultCurrency = "USD")
        assertEquals("America/New_York", customBoth.timezone)
        assertEquals("USD", customBoth.defaultCurrency)

        val defaultImplsClass = Class.forName("com.subhrodip.squarewise.accounts.profile.persistence.ProfileCommandStore\$DefaultImpls")
        val createDefaultMethod = defaultImplsClass.getMethod(
            "create\$default",
            ProfileCommandStore::class.java,
            UUID::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Any::class.java
        )
        val defaultResult = createDefaultMethod.invoke(null, store, accountId, "sub-1", "User One", null, null, 24, null) as ProfileResponse
        assertEquals("UTC", defaultResult.timezone)
        assertEquals("EUR", defaultResult.defaultCurrency)
    }
}
