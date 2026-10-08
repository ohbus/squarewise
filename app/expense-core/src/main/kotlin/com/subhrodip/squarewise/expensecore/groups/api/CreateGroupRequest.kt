package com.subhrodip.squarewise.expensecore.groups.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** Input for creating a group with its initial currency. */
data class CreateGroupRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:NotBlank @field:Pattern(regexp = "^(HOUSEHOLD|COUPLE|TRIP)$") val kind: String,
    @field:Pattern(regexp = "^[A-Z]{3}$") val currency: String
)
