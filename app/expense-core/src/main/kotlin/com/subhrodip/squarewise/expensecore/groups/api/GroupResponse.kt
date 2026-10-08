package com.subhrodip.squarewise.expensecore.groups.api

import java.util.UUID

/** Public group representation returned by the Expense Core API. */
data class GroupResponse(val groupId: UUID, val name: String, val kind: String, val revision: Long, val status: String = "ACTIVE")
