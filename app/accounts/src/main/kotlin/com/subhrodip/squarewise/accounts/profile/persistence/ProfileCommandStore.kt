package com.subhrodip.squarewise.accounts.profile.persistence

import com.subhrodip.squarewise.accounts.profile.api.ProfilePatchRequest
import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import com.subhrodip.squarewise.db.routing.DbWriter
import java.util.UUID

/** Writer-side profile persistence port. */
@DbWriter("accounts.profile.command")
interface ProfileCommandStore {
    /**
     * Explicitly provisions a new profile for an enrolled account.
     *
     * @param accountId Stable account identifier UUID.
     * @param subject Provider-qualified subject claim.
     * @param displayName User display name.
     * @param timezone Preferred timezone (defaults to "UTC").
     * @param defaultCurrency Preferred ISO-4217 currency (defaults to "EUR").
     * @return [ProfileResponse] representing the created profile.
     */
    fun create(
        accountId: UUID,
        subject: String,
        displayName: String,
        timezone: String = "UTC",
        defaultCurrency: String = "EUR"
    ): ProfileResponse

    /** Updates an existing profile. */
    fun update(subject: String, patch: ProfilePatchRequest): ProfileResponse

    /** Marks a profile for deletion. */
    fun requestDeletion(subject: String)
}
