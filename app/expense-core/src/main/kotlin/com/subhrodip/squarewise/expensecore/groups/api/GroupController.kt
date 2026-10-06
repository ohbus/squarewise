package com.subhrodip.squarewise.expensecore.groups.api
import com.subhrodip.squarewise.expensecore.groups.persistence.store.GroupStore
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS)
class GroupController(private val groups: GroupStore) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateGroupRequest, principal: Principal): GroupResponse =
        groups.create(principal.name, request)

    @GetMapping
    fun list(principal: Principal): List<GroupResponse> = groups.list(principal.name)

    @GetMapping("/{groupId}")
    fun get(@PathVariable groupId: UUID, principal: Principal): GroupResponse =
        groups.list(principal.name).find { it.groupId == groupId }
            ?: throw ExpenseDomainException(ExpenseErrors.ERR_05, "Group $groupId not found")

    @PatchMapping("/{groupId}")
    fun update(
        @PathVariable groupId: UUID,
        @Valid @RequestBody request: UpdateGroupRequest,
        @RequestHeader(ApiEndpoints.Headers.ACCEPTANCE_FAULT, required = false) fault: String?,
        principal: Principal
    ): GroupResponse {
        if (fault == ApiEndpoints.Headers.ACCEPTANCE_FAULT_ROLLBACK) throw ExpenseDomainException(ExpenseErrors.ERR_06, "Acceptance rollback fault")
        return groups.update(groupId, principal.name, request)
    }

    @PostMapping("/{groupId}/archive")
    fun archive(@PathVariable groupId: UUID, principal: Principal): GroupResponse =
        groups.archive(groupId, principal.name)

    @PostMapping("/{groupId}/placeholders")
    @ResponseStatus(HttpStatus.CREATED)
    fun addPlaceholder(
        @PathVariable groupId: UUID,
        @Valid @RequestBody request: CreatePlaceholderRequest,
        principal: Principal
    ): GroupMemberResponse = groups.addPlaceholder(groupId, principal.name, request)

    @DeleteMapping("/{groupId}/members/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(
        @PathVariable groupId: UUID,
        @PathVariable membershipId: UUID,
        principal: Principal
    ) {
        groups.removeMember(groupId, principal.name, membershipId)
    }

    @GetMapping("/{groupId}/members")
    fun listMembers(
        @PathVariable groupId: UUID,
        @RequestHeader(ApiEndpoints.Headers.ACCEPTANCE_FAULT, required = false) fault: String?,
        principal: Principal
    ): List<GroupMemberResponse> {
        if (fault == ApiEndpoints.Bff.ACCEPTANCE_FAULT_FANOUT) throw ExpenseDomainException(ExpenseErrors.ERR_08, "Acceptance fanout fault")
        return groups.listMembers(groupId, principal.name)
    }

    @PostMapping("/{groupId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    fun invite(@PathVariable groupId: UUID, @Valid @RequestBody request: CreateInviteRequest, principal: Principal): InviteResponse =
        groups.invite(groupId, principal.name, request)

    @PostMapping("/{groupId}/invites/{token}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeInvite(
        @PathVariable groupId: UUID,
        @PathVariable token: String,
        principal: Principal
    ) {
        groups.revokeInvite(groupId, principal.name, token)
    }
}
