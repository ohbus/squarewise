package com.subhrodip.squarewise.expensecore.groups.api

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.util.UUID

/** Input for creating a bounded group invitation. */
data class CreateInviteRequest(
    @field:Min(1) @field:Max(168) val expiresInHours: Int,
    @get:JsonProperty("placeholderId") val placeholderId: UUID? = null
)
