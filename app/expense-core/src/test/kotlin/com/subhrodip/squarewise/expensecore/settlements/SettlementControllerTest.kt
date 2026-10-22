package com.subhrodip.squarewise.expensecore.settlements

import com.subhrodip.squarewise.expensecore.settlements.api.SettlementController
import com.subhrodip.squarewise.expensecore.settlements.persistence.InMemorySettlementStore
import com.subhrodip.squarewise.expensecore.settlements.service.SettlementService
import com.subhrodip.squarewise.expensecore.settlements.service.SettlementSuggestionEngine
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.InMemoryExpenseStore
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.groups.cache.GroupAccessCache
import com.subhrodip.squarewise.expensecore.validation.EntityReferenceValidator

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID
import com.subhrodip.squarewise.errors.web.GlobalErrorAdvice
import java.time.Instant
import java.security.Principal
import java.lang.reflect.Proxy
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.mockito.Mockito.mock
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints

class SettlementControllerTest {
    private val expenseStore = InMemoryExpenseStore()
    private val suggestionEngine = SettlementSuggestionEngine(expenseStore)
    private val service = SettlementService(InMemorySettlementStore(), suggestionEngine)
    private val memberships = Proxy.newProxyInstance(
        GroupMembershipRepository::class.java.classLoader,
        arrayOf(GroupMembershipRepository::class.java)
    ) { _, method, args ->
        if (method.name.startsWith("existsByGroupIdAndSubject")) args?.getOrNull(1) == "test-user" else null
    } as GroupMembershipRepository
    private val groups = mock(GroupRepository::class.java)
    private val controller = SettlementController(service, EntityReferenceValidator(groups, memberships))
    private val mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(GlobalErrorAdvice()).build()
    private val user = RequestPostProcessor { request -> request.userPrincipal = Principal { "test-user" }; request }

    @Test
    fun `rejects non numeric amount`() {
        mvc.perform(post(ApiEndpoints.ExpenseCore.V1.groupSettlements(UUID.randomUUID())).with(user)
            .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "settlement-key-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fromParticipantId\":\"${UUID.randomUUID()}\",\"toParticipantId\":\"${UUID.randomUUID()}\",\"amountMinor\":\"x\",\"currency\":\"EUR\"}"))
            .andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `rejects amount that does not fit in signed long`() {
        mvc.perform(post(ApiEndpoints.ExpenseCore.V1.groupSettlements(UUID.randomUUID())).with(user)
            .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "settlement-key-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fromParticipantId\":\"${UUID.randomUUID()}\",\"toParticipantId\":\"${UUID.randomUUID()}\",\"amountMinor\":\"${"9".repeat(20)}\",\"currency\":\"EUR\"}"))
            .andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `returns settlement suggestions successfully`() {
        val groupId = UUID.randomUUID()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()

        val record = ExpenseRecord(
            expenseId = UUID.randomUUID(),
            groupId = groupId,
            description = "Lunch",
            category = "food",
            currency = "USD",
            amountMinor = 1000,
            version = 1,
            allocationMode = "EQUAL",
            createdAt = Instant.now(),
            payers = listOf(ExpensePayer(alice, 1000)),
            allocations = listOf(ExpenseAllocation(alice, 500), ExpenseAllocation(bob, 500))
        )
        expenseStore.create(groupId, record, "test-key-1")

        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions(groupId)).with(user))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].fromParticipantId").value(bob.toString()))
            .andExpect(jsonPath("$[0].toParticipantId").value(alice.toString()))
            .andExpect(jsonPath("$[0].amountMinor").value(500))
            .andExpect(jsonPath("$[0].currency").value("USD"))
    }

    @Test
    fun `returns empty list when no debts exist`() {
        val groupId = UUID.randomUUID()
        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions(groupId)).with(user))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }


    @Test
    fun `hides settlement suggestions from non-members`() {
        val nonMember = RequestPostProcessor { request -> request.userPrincipal = Principal { "non-member" }; request }

        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions(UUID.randomUUID())).with(nonMember)
        ).andExpect(status().isNotFound)
    }

    /** Verifies that the settlement boundary fails closed when no authenticated principal exists. */
    @Test
    fun `rejects settlement suggestions without an authenticated principal`() {
        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions(UUID.randomUUID()))
        ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }

    /** Verifies that a principal with no usable subject cannot cross the settlement boundary. */
    @Test
    fun `rejects settlement suggestions for a blank principal subject`() {
        val blankSubject = RequestPostProcessor { request -> request.userPrincipal = Principal { " " }; request }

        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions(UUID.randomUUID())).with(blankSubject)
        ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }

    /** Verifies recording, reversal, and reversal replay through the public REST boundary. */
    @Test
    fun `records and idempotently reverses settlement`() {
        val groupId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val recorded = mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupSettlements(groupId)).with(user).header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "settlement-key-0001")
                .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fromParticipantId\":\"$from\",\"toParticipantId\":\"$to\",\"amountMinor\":\"1250\",\"currency\":\"EUR\"}")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.fromParticipantId").value(from.toString()))
            .andExpect(jsonPath("$.toParticipantId").value(to.toString()))
            .andExpect(jsonPath("$.amountMinor").value(1250))
            .andExpect(jsonPath("$.status").value("RECORDED"))
            .andReturn().response.contentAsString
        val settlementId = Regex("\\\"id\\\":\\\"([^\\\"]+)\\\"").find(recorded)!!.groupValues[1]
        val reversalPath = ApiEndpoints.ExpenseCore.V1.groupSettlementReversal(groupId, settlementId)

        mvc.perform(post(reversalPath).with(user).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"paid externally\"}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REVERSED"))
            .andExpect(jsonPath("$.reason").value("paid externally"))

        mvc.perform(post(reversalPath).with(user).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"retry must not overwrite\"}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REVERSED"))
            .andExpect(jsonPath("$.reason").value("paid externally"))
    }

    /** Verifies settlement recording replay and altered-payload conflict at HTTP boundary. */
    @Test
    fun `replays same settlement key and rejects altered payload`() {
        val groupId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val path = ApiEndpoints.ExpenseCore.V1.groupSettlements(groupId)
        val payload = "{\"fromParticipantId\":\"$from\",\"toParticipantId\":\"$to\",\"amountMinor\":\"1250\",\"currency\":\"EUR\"}"

        val first = mvc.perform(post(path).with(user)
            .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "public-settlement-key-0001")
            .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        mvc.perform(post(path).with(user)
            .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "public-settlement-key-0001")
            .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(Regex("\\\"id\\\":\\\"([^\\\"]+)\\\"").find(first)!!.groupValues[1]))

        mvc.perform(post(path).with(user)
            .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "public-settlement-key-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fromParticipantId\":\"$from\",\"toParticipantId\":\"$to\",\"amountMinor\":\"1300\",\"currency\":\"EUR\"}"))
            .andExpect(status().isConflict)
    }

    /** Verifies invalid settlement invariants and unknown reversals use public client errors. */
    @Test
    fun `rejects invalid settlements and returns not found for unknown reversal`() {
        val groupId = UUID.randomUUID()
        val participant = UUID.randomUUID()
        mvc.perform(post(ApiEndpoints.ExpenseCore.V1.groupSettlements(groupId)).with(user).header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "invalid-key-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fromParticipantId\":\"$participant\",\"toParticipantId\":\"$participant\",\"amountMinor\":\"100\",\"currency\":\"EUR\"}"))
            .andExpect(status().isUnprocessableContent)

        mvc.perform(post(ApiEndpoints.ExpenseCore.V1.groupSettlements(groupId)).with(user).header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "invalid-key-0002")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fromParticipantId\":\"${UUID.randomUUID()}\",\"toParticipantId\":\"${UUID.randomUUID()}\",\"amountMinor\":\"0\",\"currency\":\"EUR\"}"))
            .andExpect(status().isUnprocessableContent)

        mvc.perform(post(ApiEndpoints.ExpenseCore.V1.groupSettlementReversal(groupId, UUID.randomUUID())).with(user)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"unknown\"}"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `controller with groupAccessCache validates and caches active membership`() {
        val cache = GroupAccessCache()
        val cachedController = SettlementController(service, EntityReferenceValidator(groups, memberships, cache))
        val cachedMvc = MockMvcBuilders.standaloneSetup(cachedController)
            .setControllerAdvice(GlobalErrorAdvice()).build()

        val groupId = UUID.randomUUID()
        // Suggestions call checks membership via cache
        cachedMvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions(groupId)).with(user))
            .andExpect(status().isOk)

        // Subsequent call exercises cache hit in GroupAccessCache.isMember
        cachedMvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions(groupId)).with(user))
            .andExpect(status().isOk)

        // Non-member request
        val nonMember = RequestPostProcessor { request -> request.userPrincipal = Principal { "other-user" }; request }
        cachedMvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions(groupId)).with(nonMember))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `rejects requests when principal is missing or blank`() {
        val groupId = UUID.randomUUID()
        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions(groupId)))
            .andExpect(status().isUnauthorized)

        val blankUser = RequestPostProcessor { request -> request.userPrincipal = Principal { "   " }; request }
        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions(groupId)).with(blankUser))
            .andExpect(status().isUnauthorized)
    }
}
