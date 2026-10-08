package com.subhrodip.squarewise.accounts.profile
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach

import com.subhrodip.squarewise.accounts.profile.api.ProfileController
import com.subhrodip.squarewise.accounts.profile.api.ProfilePatchRequest
import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import com.subhrodip.squarewise.accounts.profile.persistence.InMemoryProfileStore
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileStore

import com.subhrodip.squarewise.accounts.requests.deletion.service.DeletionRequestService
import com.subhrodip.squarewise.accounts.requests.deletion.persistence.InMemoryDeletionRequestStore
import com.subhrodip.squarewise.accounts.requests.export.service.ExportRequestService
import com.subhrodip.squarewise.accounts.requests.export.persistence.InMemoryExportRequestStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import com.subhrodip.squarewise.errors.http.GlobalErrorHandler
import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.ReadConsistency
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import java.nio.charset.StandardCharsets
import java.security.Principal
import java.time.Instant
import java.util.UUID
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ProfileControllerTest {
    private val recordingProfiles = RecordingProfileStore(InMemoryProfileStore())
    private val controller = ProfileController(
            profiles = recordingProfiles,
            deletionService = DeletionRequestService(InMemoryDeletionRequestStore { Instant.parse("2026-01-01T00:00:00Z") }),
            exportService = ExportRequestService(InMemoryExportRequestStore { Instant.parse("2026-01-01T00:00:00Z") })
        )
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(
        controller
    )
        .setControllerAdvice(GlobalErrorHandler())
        .build()
    private val alice = RequestPostProcessor { request ->
        request.userPrincipal = Principal { "oidc|alice" }
        request
    }

    private val aliceId = UUID.nameUUIDFromBytes("oidc|alice".toByteArray(StandardCharsets.UTF_8))
    private val policyId = UUID.nameUUIDFromBytes("oidc|policy".toByteArray(StandardCharsets.UTF_8))

    @BeforeEach
    fun setUp() {
        recordingProfiles.seed("oidc|alice", aliceId)
        recordingProfiles.seed("oidc|policy", policyId)
    }

    @Test
    fun `profile lookup by account id uses the approved eventual reader policy`() {
        val accountId = recordingProfiles.get("oidc|policy")!!.accountId

        mvc.perform(
            get(ApiEndpoints.Accounts.V1.profileById(accountId))
                .header(ApiEndpoints.Headers.WORKLOAD_ROLE, ApiEndpoints.Headers.WORKLOAD_ROLE_INTERNAL)
        )
            .andExpect(status().isOk)

        assertEquals("profile.lookup", recordingProfiles.lastContext?.operationName)
        assertEquals(DbOperationKind.QUERY, recordingProfiles.lastContext?.kind)
        assertEquals(ReadConsistency.EVENTUAL, recordingProfiles.lastContext?.consistency)
        assertTrue(recordingProfiles.lastContext?.readerEligible == true)
    }

    @Test
    fun `gets profile for authenticated subject`() {
        mvc.perform(get(ApiEndpoints.Accounts.V1.PATH_ME).with(alice))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("oidc|alice"))
            .andExpect(jsonPath("$.defaultCurrency").value("EUR"))
    }

    @Test
    fun `returns 404 when authenticated profile does not exist`() {
        val unmappedUser = RequestPostProcessor { request ->
            request.userPrincipal = Principal { "oidc|unmapped" }
            request
        }
        mvc.perform(get(ApiEndpoints.Accounts.V1.PATH_ME).with(unmappedUser))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `updates profile with validated fields`() {
        mvc.perform(patch(ApiEndpoints.Accounts.V1.PATH_ME).with(alice).contentType(MediaType.APPLICATION_JSON)
            .content("{\"displayName\":\"Alice\",\"timezone\":\"Europe/Vienna\",\"defaultCurrency\":\"USD\"}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Alice"))
            .andExpect(jsonPath("$.timezone").value("Europe/Vienna"))
            .andExpect(jsonPath("$.defaultCurrency").value("USD"))
    }

    @Test
    fun `rejects empty patch`() {
        mvc.perform(patch(ApiEndpoints.Accounts.V1.PATH_ME).with(alice).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `accepts deletion request`() {
        assertEquals(202, mvc.perform(post(ApiEndpoints.Accounts.V1.PATH_ME_DELETION_REQUEST).with(alice)).andReturn().response.status)
    }

    @Test
    fun `creates export request for authenticated subject`() {
        mvc.perform(post(ApiEndpoints.Accounts.V1.PATH_ME_EXPORT_REQUEST).with(alice))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.exportId").isNotEmpty)
            .andExpect(jsonPath("$.status").value("REQUESTED"))
            .andExpect(jsonPath("$.requestedAt").isNotEmpty)
    }

    @Test
    fun `lists export requests for authenticated subject`() {
        mvc.perform(post(ApiEndpoints.Accounts.V1.PATH_ME_EXPORT_REQUEST).with(alice))
            .andExpect(status().isAccepted)

        mvc.perform(get(ApiEndpoints.Accounts.V1.PATH_ME_EXPORT_REQUESTS).with(alice))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].exportId").isNotEmpty)
            .andExpect(jsonPath("$[0].status").value("REQUESTED"))
            .andExpect(jsonPath("$[0].requestedAt").isNotEmpty)
    }

    @Test
    fun `rejects export request without authentication`() {
        mvc.perform(post(ApiEndpoints.Accounts.V1.PATH_ME_EXPORT_REQUEST))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }

    @Test
    fun `rejects export request listing without authentication`() {
        mvc.perform(get(ApiEndpoints.Accounts.V1.PATH_ME_EXPORT_REQUESTS))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }

    @Test
    fun `rejects export request with invalid subject`() {
        val invalidUser = RequestPostProcessor { request ->
            request.userPrincipal = Principal { "invalid subject with spaces" }
            request
        }
        mvc.perform(post(ApiEndpoints.Accounts.V1.PATH_ME_EXPORT_REQUEST).with(invalidUser))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }

    @Test
    fun `rejects export request listing with invalid subject`() {
        val invalidUser = RequestPostProcessor { request ->
            request.userPrincipal = Principal { "invalid subject with spaces" }
            request
        }
        mvc.perform(get(ApiEndpoints.Accounts.V1.PATH_ME_EXPORT_REQUESTS).with(invalidUser))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }

    @Test
    fun `allows user to get their own profile by account id`() {
        mvc.perform(get(ApiEndpoints.Accounts.V1.profileById(aliceId)).with(alice))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accountId").value(aliceId.toString()))
            .andExpect(jsonPath("$.displayName").value("oidc|alice"))
            .andExpect(jsonPath("$.timezone").value("UTC"))
            .andExpect(jsonPath("$.defaultCurrency").value("EUR"))
    }

    @Test
    fun `rejects cross-account profile lookup by account id with 403 forbidden`() {
        val bobId = UUID.nameUUIDFromBytes("oidc|bob".toByteArray(StandardCharsets.UTF_8))
        recordingProfiles.seed("oidc|bob", bobId)

        mvc.perform(get(ApiEndpoints.Accounts.V1.profileById(bobId)).with(alice))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("AUTHORIZATION_ERROR"))
            .andExpect(jsonPath("$.detail").value("Access denied to foreign profile"))
    }

    @Test
    fun `rejects profile lookup by account id without authentication with 401 unauthorized`() {
        mvc.perform(get(ApiEndpoints.Accounts.V1.profileById(aliceId)))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }

    @Test
    fun `rejects profile lookup when authenticated subject has no profile`() {
        val unmappedUser = RequestPostProcessor { request ->
            request.userPrincipal = Principal { "oidc|unmapped" }
            request
        }

        mvc.perform(get(ApiEndpoints.Accounts.V1.profileById(aliceId)).with(unmappedUser))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `allows internal workload to lookup any profile by account id`() {
        val bobId = UUID.nameUUIDFromBytes("oidc|bob".toByteArray(StandardCharsets.UTF_8))
        recordingProfiles.seed("oidc|bob", bobId)

        mvc.perform(
            get(ApiEndpoints.Accounts.V1.profileById(bobId))
                .header(ApiEndpoints.Headers.WORKLOAD_ROLE, ApiEndpoints.Headers.WORKLOAD_ROLE_INTERNAL)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accountId").value(bobId.toString()))
            .andExpect(jsonPath("$.displayName").value("oidc|bob"))
    }

    @Test
    fun `returns 404 when profile not found by account id for authorized workload`() {
        val nonExistentId = UUID.randomUUID()
        mvc.perform(
            get(ApiEndpoints.Accounts.V1.profileById(nonExistentId))
                .header(ApiEndpoints.Headers.WORKLOAD_ROLE, ApiEndpoints.Headers.WORKLOAD_ROLE_INTERNAL)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `store finds profile by account id and returns null when absent`() {
        val store = InMemoryProfileStore()
        val created = store.seed("oidc|bob")
        assertEquals(created, store.findById(created.accountId))
        assertNull(store.findById(UUID.randomUUID()))
    }

    @Test
    fun `gets profiles in batch for valid account ids with internal workload authority`() {
        val bobId = UUID.nameUUIDFromBytes("oidc|bob".toByteArray(StandardCharsets.UTF_8))
        recordingProfiles.seed("oidc|bob", bobId)

        val nonExistentId = UUID.randomUUID()

        val requestBody = "{\"accountIds\": [\"$aliceId\", \"$bobId\", \"$nonExistentId\"]}"
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_PROFILES_BATCH)
                .header(ApiEndpoints.Headers.WORKLOAD_ROLE, ApiEndpoints.Headers.WORKLOAD_ROLE_INTERNAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))

        val emptyBatch = "{\"accountIds\": [\"$nonExistentId\"]}"
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_PROFILES_BATCH)
                .header(ApiEndpoints.Headers.WORKLOAD_ROLE, ApiEndpoints.Headers.WORKLOAD_ROLE_INTERNAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(emptyBatch)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `allows user to batch query only their own profile`() {
        val requestBody = "{\"accountIds\": [\"$aliceId\"]}"
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_PROFILES_BATCH)
                .with(alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].accountId").value(aliceId.toString()))
    }

    @Test
    fun `rejects batch profile lookup by user with foreign account id`() {
        val bobId = UUID.nameUUIDFromBytes("oidc|bob".toByteArray(StandardCharsets.UTF_8))
        recordingProfiles.seed("oidc|bob", bobId)

        val requestBody = "{\"accountIds\": [\"$aliceId\", \"$bobId\"]}"
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_PROFILES_BATCH)
                .with(alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("AUTHORIZATION_ERROR"))
            .andExpect(jsonPath("$.detail").value("Batch profile lookup requires internal workload authority"))
    }

    @Test
    fun `rejects batch profile lookup without authentication with 401 unauthorized`() {
        val requestBody = "{\"accountIds\": [\"$aliceId\"]}"
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_PROFILES_BATCH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }

    @Test
    fun `rejects batch lookup when authenticated subject has no profile`() {
        val unmappedUser = RequestPostProcessor { request ->
            request.userPrincipal = Principal { "oidc|unmapped" }
            request
        }

        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_PROFILES_BATCH)
                .with(unmappedUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountIds\": [\"$aliceId\"]}")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `rejects batch profile lookup with empty account ids`() {
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_PROFILES_BATCH)
                .header(ApiEndpoints.Headers.WORKLOAD_ROLE, ApiEndpoints.Headers.WORKLOAD_ROLE_INTERNAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountIds\": []}")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `rejects batch profile lookup with invalid uuid`() {
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_PROFILES_BATCH)
                .header(ApiEndpoints.Headers.WORKLOAD_ROLE, ApiEndpoints.Headers.WORKLOAD_ROLE_INTERNAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountIds\": [\"not-a-valid-uuid\"]}")
        )
            .andExpect(status().isBadRequest)
    }

    /** Verifies the public batch boundary enforces its documented maximum of 100 account IDs. */
    @Test
    fun `rejects batch profile lookup above maximum size`() {
        val accountIds = (1..101).joinToString(",") { "\"${UUID.randomUUID()}\"" }

        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_PROFILES_BATCH)
                .header(ApiEndpoints.Headers.WORKLOAD_ROLE, ApiEndpoints.Headers.WORKLOAD_ROLE_INTERNAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountIds\":[$accountIds]}")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    /** Verifies duplicate requested IDs produce one profile rather than duplicated response rows. */
    @Test
    fun `deduplicates repeated profile identifiers in batch response`() {
        mvc.perform(
            post(ApiEndpoints.Accounts.V1.PATH_PROFILES_BATCH)
                .header(ApiEndpoints.Headers.WORKLOAD_ROLE, ApiEndpoints.Headers.WORKLOAD_ROLE_INTERNAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountIds\":[\"$aliceId\",\"$aliceId\"]}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].accountId").value(aliceId.toString()))
    }

    @Test
    fun `store finds profiles in batch and ignores non-existent ids`() {
        val store = InMemoryProfileStore()
        val p1 = store.seed("oidc|user-1")
        val p2 = store.seed("oidc|user-2")
        val p3 = store.seed("oidc|user-3")

        val result = store.findByIds(listOf(p1.accountId, p3.accountId, UUID.randomUUID()))
        assertEquals(2, result.size)
        val resultIds = result.map { it.accountId }.toSet()
        assertTrue(resultIds.contains(p1.accountId))
        assertTrue(resultIds.contains(p3.accountId))
    }
}

private class RecordingProfileStore(private val delegate: InMemoryProfileStore) : ProfileStore {
    var lastContext: DbExecutionContext? = null

    override fun get(subject: String): ProfileResponse? = delegate.get(subject)

    override fun create(
        accountId: UUID,
        subject: String,
        displayName: String,
        timezone: String,
        defaultCurrency: String
    ): ProfileResponse = delegate.create(accountId, subject, displayName, timezone, defaultCurrency)

    override fun findById(accountId: UUID): ProfileResponse? {
        lastContext = DbContextHolder.current()
        return delegate.findById(accountId)
    }

    override fun findByIds(accountIds: List<UUID>): List<ProfileResponse> {
        lastContext = DbContextHolder.current()
        return delegate.findByIds(accountIds)
    }

    override fun update(subject: String, patch: ProfilePatchRequest): ProfileResponse = delegate.update(subject, patch)

    override fun requestDeletion(subject: String) = delegate.requestDeletion(subject)

    fun seed(subject: String, accountId: UUID = UUID.randomUUID()) = delegate.seed(subject, accountId)
}
