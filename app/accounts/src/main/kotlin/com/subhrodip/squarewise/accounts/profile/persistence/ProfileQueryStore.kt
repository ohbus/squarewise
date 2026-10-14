package com.subhrodip.squarewise.accounts.profile.persistence

import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import com.subhrodip.squarewise.db.routing.DbReader
import java.util.UUID

/** Reader-side profile persistence port. */
@DbReader("accounts.profile.query")
interface ProfileQueryStore {
    /**
     * Retrieves an existing profile for an authenticated subject without mutating state.
     *
     * @param subject OIDC / provider-qualified subject identifier.
     * @return [ProfileResponse] or null if no profile exists for this subject.
     */
    fun get(subject: String): ProfileResponse?

    /** Finds a profile by account id. */
    fun findById(accountId: UUID): ProfileResponse?

    /** Finds profiles for a batch of account ids. */
    fun findByIds(accountIds: List<UUID>): List<ProfileResponse>
}
