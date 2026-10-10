package com.subhrodip.squarewise.accounts.profile.api

import com.subhrodip.squarewise.accounts.requests.deletion.service.DeletionRequestService
import com.subhrodip.squarewise.accounts.requests.export.service.ExportRequestService
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import java.security.Principal
import java.util.UUID
import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.ReadConsistency
import com.subhrodip.squarewise.observability.db.DbTelemetry
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileStore
import org.springframework.security.core.Authentication

private const val INTERNAL_SERVICE_SCOPE = "SCOPE_squarewise.internal"

@RestController
@RequestMapping(ApiEndpoints.Accounts.V1.BASE)
class ProfileController(
    private val profiles: ProfileStore,
    private val deletionService: DeletionRequestService,
    private val exportService: ExportRequestService,
    private val dbTelemetry: DbTelemetry = DbTelemetry()
) {
    @GetMapping(ApiEndpoints.Accounts.V1.ME)
    fun get(principal: Principal): ProfileResponse =
        profiles.get(principal.name) ?: throw AccountsDomainException(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND)

    @PatchMapping(ApiEndpoints.Accounts.V1.ME)
    fun update(
        principal: Principal,
        @Valid @RequestBody request: ProfilePatchRequest
    ): ProfileResponse {
        request.validateNotEmpty()
        return profiles.update(principal.name, request)
    }

    @PostMapping(ApiEndpoints.Accounts.V1.ME_DELETION_REQUEST)
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun requestDeletion(principal: Principal) {
        deletionService.request(principal.name)
    }

    @PostMapping(ApiEndpoints.Accounts.V1.ME_EXPORT_REQUEST)
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun requestExport(principal: Principal?): ExportRequestResponse {
        val subject = principal?.name ?: throw AccountsDomainException(AccountsErrors.PROFILE_SUBJECT_INVALID)
        val request = exportService.request(subject)
        return ExportRequestResponse(request.exportId, request.status, request.requestedAt)
    }

    @GetMapping(ApiEndpoints.Accounts.V1.ME_EXPORT_REQUESTS)
    fun listExportRequests(principal: Principal?): List<ExportRequestResponse> {
        val subject = principal?.name ?: throw AccountsDomainException(AccountsErrors.PROFILE_SUBJECT_INVALID)
        return exportService.listBySubject(subject).map {
            ExportRequestResponse(it.exportId, it.status, it.requestedAt)
        }
    }

    /**
     * Retrieves a profile by account ID.
     *
     * Authorized only if the validated service token possesses internal workload authority or is performing a
     * self-lookup matching their authenticated profile account ID. Arbitrary cross-user lookups
     * are rejected with 403 Forbidden.
     *
     * @param accountId target account identifier.
     * @param principal authenticated caller, including validated service authorities.
     * @return [ProfileResponse] matching the account ID.
     * @throws AccountsDomainException if unauthenticated, unauthorized, or not found.
     */
    @GetMapping(ApiEndpoints.Accounts.V1.PROFILES_BY_ID)
    fun getProfileById(
        @PathVariable accountId: UUID,
        principal: Principal?,
    ): ProfileResponse {
        val isWorkload = hasInternalServiceAuthority(principal)
        if (!isWorkload) {
            val callerSubject = principal?.name ?: throw AccountsDomainException(AccountsErrors.PROFILE_SUBJECT_INVALID)
            val callerProfile = profiles.get(callerSubject) ?: throw AccountsDomainException(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND)
            if (callerProfile.accountId != accountId) {
                throw AccountsDomainException(AccountsErrors.FOREIGN_PROFILE_ACCESS_DENIED)
            }
        }
        return dbTelemetry.measureQuery("profile.lookup", "approved-query") {
            DbContextHolder.withContext(profileReadContext("profile.lookup")) {
                profiles.findById(accountId) ?: throw AccountsDomainException(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND)
            }
        }
    }

    /**
     * Batch lookups profiles for a collection of account IDs.
     *
     * Restricted to callers with validated internal service authority or end-user callers limited to
     * querying their own profile ID. Batch lookups encompassing foreign profiles by non-workload
     * callers are rejected with 403 Forbidden.
     *
     * @param request batch lookup payload with up to 100 account IDs.
     * @param principal authenticated caller, including validated service authorities.
     * @return list of resolved [ProfileResponse] records matching existing IDs.
     * @throws AccountsDomainException if unauthenticated or unauthorized.
     */
    @PostMapping(ApiEndpoints.Accounts.V1.PROFILES_BATCH)
    fun getProfilesBatch(
        @Valid @RequestBody request: BatchProfileRequest,
        principal: Principal?,
    ): List<ProfileResponse> {
        val isWorkload = hasInternalServiceAuthority(principal)
        if (!isWorkload) {
            val callerSubject = principal?.name ?: throw AccountsDomainException(AccountsErrors.PROFILE_SUBJECT_INVALID)
            val callerProfile = profiles.get(callerSubject) ?: throw AccountsDomainException(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND)
            val requestedDistinctIds = request.accountIds.toSet()
            if (requestedDistinctIds.any { it != callerProfile.accountId }) {
                throw AccountsDomainException(AccountsErrors.BATCH_LOOKUP_UNAUTHORIZED)
            }
        }
        return dbTelemetry.measureQuery("profile.batch_lookup", "approved-query") {
            DbContextHolder.withContext(profileReadContext("profile.batch_lookup")) { profiles.findByIds(request.accountIds) }
        }
    }

    private fun profileReadContext(operationName: String) = DbExecutionContext(
        operationName = operationName,
        kind = DbOperationKind.QUERY,
        consistency = ReadConsistency.EVENTUAL,
        readerEligible = true
    )

    private fun hasInternalServiceAuthority(principal: Principal?): Boolean =
        (principal as? Authentication)?.authorities?.any { it.authority == INTERNAL_SERVICE_SCOPE } == true
}
