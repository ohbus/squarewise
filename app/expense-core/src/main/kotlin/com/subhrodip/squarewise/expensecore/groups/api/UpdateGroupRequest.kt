package com.subhrodip.squarewise.expensecore.groups.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Input for renaming an existing group. */
data class UpdateGroupRequest(@field:NotBlank @field:Size(max = 120) val name: String)
