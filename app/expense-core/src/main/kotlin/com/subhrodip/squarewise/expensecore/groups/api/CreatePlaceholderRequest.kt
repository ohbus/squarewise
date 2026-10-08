package com.subhrodip.squarewise.expensecore.groups.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Input for adding a named placeholder member to a group. */
data class CreatePlaceholderRequest(@field:NotBlank @field:Size(max = 120) val name: String)
