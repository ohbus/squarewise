package com.subhrodip.squarewise.expensecore.groups.api

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/** Public membership representation, including placeholder state. */
data class GroupMemberResponse(
    @get:JsonProperty("membershipId") val membershipId: UUID,
    @get:JsonProperty("groupId") val groupId: UUID,
    @get:JsonProperty("subject") val subject: String? = null,
    @get:JsonProperty("displayName") val displayName: String? = null,
    @get:JsonProperty("isPlaceholder") val isPlaceholder: Boolean = false,
    @get:JsonProperty("status") val status: String = "ACTIVE"
)
