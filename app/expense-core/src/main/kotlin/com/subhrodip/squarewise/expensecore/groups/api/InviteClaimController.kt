package com.subhrodip.squarewise.expensecore.groups.api

import com.subhrodip.squarewise.expensecore.groups.persistence.store.GroupStore
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import java.security.Principal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** REST controller for claiming group invitations. */
@RestController
@RequestMapping(ApiEndpoints.ExpenseCore.V1.BASE + "/invites")
class InviteClaimController(private val groups: GroupStore) {
    /** Claims an invitation for the authenticated subject. */
    @PostMapping("/{token}/claim")
    fun claim(@PathVariable token: String, principal: Principal): GroupResponse = groups.claim(token, principal.name)
}
