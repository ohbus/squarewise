package com.subhrodip.squarewise.expensecore.groups.api

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/** Public invitation token and expiry returned after invitation creation. */
data class InviteResponse(
    val token: String,
    val expiresAt: Instant,
    @get:JsonProperty("placeholderId") val placeholderId: UUID? = null
)
