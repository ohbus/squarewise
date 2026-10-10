package com.subhrodip.squarewise.expensecore.sync.api

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.expensecore.sync.persistence.SynchronizationStore
import com.subhrodip.squarewise.expensecore.sync.api.toResponse
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository

import java.security.Principal
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints

@RestController
@RequestMapping(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_SYNC)
class SyncController(
    private val store: SynchronizationStore,
    private val memberships: GroupMembershipRepository,
) {
    @GetMapping(ApiEndpoints.ExpenseCore.V1.SYNC_SNAPSHOT_RELATIVE_SUBPATH, ApiEndpoints.ExpenseCore.V1.SYNC_CHANGES_RELATIVE_SUBPATH)
    fun page(
        @PathVariable groupId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        principal: Principal
    ): SyncPageResponse {
        if (principal.name.isBlank()) throw ExpenseDomainException(PlatformErrors.AUTHENTICATION_REQUIRED, "Authenticated subject is required")
        if (!memberships.existsByGroupIdAndSubjectAndStatus(groupId, principal.name, "ACTIVE")) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Group $groupId not found")
        }
        if (limit !in 1..100) throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "limit must be between 1 and 100")
        return store.snapshot(groupId.toString(), cursor, limit).toResponse()
    }
}
