package com.subhrodip.squarewise.expensecore.groups.api

import com.subhrodip.squarewise.errors.catalog.BffErrors
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import java.security.Principal
import java.util.UUID
import com.subhrodip.squarewise.db.routing.DbReader
import com.subhrodip.squarewise.db.routing.DbWriter

@RestController
@RequestMapping(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS)
class GroupController(private val groups: GroupStore) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @DbWriter("group.create")
    fun create(@Valid @RequestBody request: CreateGroupRequest, principal: Principal): GroupResponse =
        groups.create(principal.name, request)

    @GetMapping
    @DbReader("groups.list")
    fun list(
        @RequestParam(defaultValue = "100") limit: Int = 100,
        principal: Principal
    ): List<GroupResponse> = groups.list(principal.name, limit)

    @GetMapping("/{groupId}")
    @DbReader("group.get")
    fun get(@PathVariable groupId: UUID, principal: Principal): GroupResponse =
        groups.get(groupId, principal.name)
            ?: throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Group $groupId not found")

    @PatchMapping("/{groupId}")
    @DbWriter("group.update")
    fun update(
        @PathVariable groupId: UUID,
        @Valid @RequestBody request: UpdateGroupRequest,
        @RequestHeader(ApiEndpoints.Headers.ACCEPTANCE_FAULT, required = false) fault: String?,
        principal: Principal
    ): GroupResponse {
        if (fault == ApiEndpoints.Headers.ACCEPTANCE_FAULT_ROLLBACK) throw ExpenseDomainException(ExpenseErrors.GROUP_NAME_CONFLICT, "Acceptance rollback fault")
        return groups.update(groupId, principal.name, request)
    }

    @PostMapping("/{groupId}/archive")
    @DbWriter("group.archive")
    fun archive(@PathVariable groupId: UUID, principal: Principal): GroupResponse =
        groups.archive(groupId, principal.name)

    @PostMapping("/{groupId}/placeholders")
    @ResponseStatus(HttpStatus.CREATED)
    @DbWriter("group.placeholder.create")
    fun addPlaceholder(
        @PathVariable groupId: UUID,
        @Valid @RequestBody request: CreatePlaceholderRequest,
        principal: Principal
    ): GroupMemberResponse = groups.addPlaceholder(groupId, principal.name, request)

    @DeleteMapping("/{groupId}/members/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DbWriter("group.member.remove")
    fun removeMember(
        @PathVariable groupId: UUID,
        @PathVariable membershipId: UUID,
        principal: Principal
    ) {
        groups.removeMember(groupId, principal.name, membershipId)
    }

    @GetMapping("/{groupId}/members")
    /** Returns the first bounded page of members visible to the authenticated member. */
    @DbReader("group.members.list")
    fun listMembers(
        @PathVariable groupId: UUID,
        @RequestHeader(ApiEndpoints.Headers.ACCEPTANCE_FAULT, required = false) fault: String?,
        @RequestParam(defaultValue = "100") limit: Int = 100,
        principal: Principal
    ): List<GroupMemberResponse> {
        if (fault == ApiEndpoints.Bff.ACCEPTANCE_FAULT_FANOUT) throw ExpenseDomainException(BffErrors.UPSTREAM_PROTOCOL_INVALID, "Acceptance fanout fault")
        return groups.listMembers(groupId, principal.name, limit)
    }

    @PostMapping("/{groupId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    @DbWriter("group.invite.create")
    fun invite(@PathVariable groupId: UUID, @Valid @RequestBody request: CreateInviteRequest, principal: Principal): InviteResponse =
        groups.invite(groupId, principal.name, request)

    @PostMapping("/{groupId}/invites/{token}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DbWriter("group.invite.revoke")
    fun revokeInvite(
        @PathVariable groupId: UUID,
        @PathVariable token: String,
        principal: Principal
    ) {
        groups.revokeInvite(groupId, principal.name, token)
    }
}
