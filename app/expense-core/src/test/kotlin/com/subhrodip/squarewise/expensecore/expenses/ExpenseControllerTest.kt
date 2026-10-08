package com.subhrodip.squarewise.expensecore.expenses

import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.expensecore.expenses.api.ExpenseController
import com.subhrodip.squarewise.expensecore.expenses.api.request.AllocationInputDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.AllocationItemDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.CreateExpenseRequest
import com.subhrodip.squarewise.expensecore.expenses.api.request.MoneyDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.PayerDto
import com.subhrodip.squarewise.expensecore.expenses.api.request.UpdateExpenseRequest
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.InMemoryExpenseStore
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository

import com.subhrodip.squarewise.errors.http.GlobalErrorHandler
import java.util.UUID
import java.security.Principal
import java.util.Optional
import java.lang.reflect.Proxy
import org.mockito.Mockito.`when`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints

class ExpenseControllerTest {
    private val store = InMemoryExpenseStore()
    private val archivedGroups = mutableSetOf<UUID>()
    private val memberships = Proxy.newProxyInstance(
        GroupMembershipRepository::class.java.classLoader,
        arrayOf(GroupMembershipRepository::class.java)
    ) { _, method, args ->
        if (method.name.startsWith("existsByGroupIdAndSubject")) args?.getOrNull(1) == "test-user" else null
    } as GroupMembershipRepository
    private val groups = Proxy.newProxyInstance(
        GroupRepository::class.java.classLoader,
        arrayOf(GroupRepository::class.java)
    ) { _, method, args ->
        if (method.name == "findById") {
            val groupId = args!![0] as UUID
            Optional.of(GroupEntity(groupId, "Test", "TRIP", "EUR", if (groupId in archivedGroups) "ARCHIVED" else "ACTIVE"))
        } else null
    } as GroupRepository
    private val controller = ExpenseController(store, memberships, groups)
    private val authenticatedPrincipalFilter = Filter { request, response, chain ->
        val authenticatedRequest = object : HttpServletRequestWrapper(request as HttpServletRequest) {
            override fun getUserPrincipal(): Principal =
                super.getUserPrincipal() ?: Principal { "test-user" }
        }
        chain.doFilter(authenticatedRequest, response)
    }
    private val mvcBuilder: StandaloneMockMvcBuilder = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(GlobalErrorHandler())
    private val mvc: MockMvc =
        (mvcBuilder.addFilters(authenticatedPrincipalFilter) as StandaloneMockMvcBuilder).build()

    @Test
    fun `rejects missing and blank authenticated subjects before membership lookup`() {
        val missing = assertThrows<SquarewiseException> {
            controller.getBalances(UUID.randomUUID(), null)
        }
        assertEquals(CategoryCode.AUTHENTICATION_ERROR, missing.definition.category)

        val blank = assertThrows<SquarewiseException> {
            controller.getBalances(UUID.randomUUID(), Principal { "   " })
        }
        assertEquals(CategoryCode.AUTHENTICATION_ERROR, blank.definition.category)
    }

    /** Verifies every financial mutation rejects a missing principal before validation or persistence. */
    @Test
    fun `rejects missing principal for create update and delete mutations`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val participantId = UUID.randomUUID().toString()
        val createRequest = CreateExpenseRequest(
            expenseId = expenseId,
            description = "Unauthenticated",
            amount = MoneyDto("EUR", "100"),
            payers = listOf(PayerDto(participantId, MoneyDto("EUR", "100"))),
            allocation = AllocationInputDto(
                "EXACT",
                listOf(AllocationItemDto(participantId, "100"))
            )
        )
        val updateRequest = UpdateExpenseRequest(
            version = 1,
            description = "Unauthenticated update",
            amount = MoneyDto("EUR", "100"),
            payers = createRequest.payers,
            allocation = createRequest.allocation
        )

        assertEquals(
            CategoryCode.AUTHENTICATION_ERROR,
            assertThrows<SquarewiseException> {
                controller.createExpense(groupId, "unauthenticated-create", createRequest, null)
            }.definition.category
        )
        assertEquals(
            CategoryCode.AUTHENTICATION_ERROR,
            assertThrows<SquarewiseException> {
                controller.updateExpense(groupId, expenseId, updateRequest, null)
            }.definition.category
        )
        assertEquals(
            CategoryCode.AUTHENTICATION_ERROR,
            assertThrows<SquarewiseException> {
                controller.deleteExpense(groupId, expenseId, null, null)
            }.definition.category
        )
    }

    /** Verifies payer aggregation overflow is translated to the public validation error. */
    @Test
    fun `rejects payer sum overflow for create and update mutations`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val firstParticipant = UUID.randomUUID().toString()
        val secondParticipant = UUID.randomUUID().toString()
        val maximum = Long.MAX_VALUE.toString()
        val request = CreateExpenseRequest(
            expenseId = expenseId,
            description = "Overflow",
            amount = MoneyDto("EUR", maximum),
            payers = listOf(
                PayerDto(firstParticipant, MoneyDto("EUR", maximum)),
                PayerDto(secondParticipant, MoneyDto("EUR", maximum))
            ),
            allocation = AllocationInputDto(
                "EXACT",
                listOf(AllocationItemDto(firstParticipant, maximum))
            )
        )

        assertEquals(
            CategoryCode.VALIDATION_ERROR,
            assertThrows<SquarewiseException> {
                controller.createExpense(groupId, "overflow-create", request, Principal { "test-user" })
            }.definition.category
        )

        val update = UpdateExpenseRequest(
            version = 1,
            description = request.description,
            amount = request.amount,
            payers = request.payers,
            allocation = request.allocation
        )
        assertEquals(
            CategoryCode.VALIDATION_ERROR,
            assertThrows<SquarewiseException> {
                controller.updateExpense(groupId, expenseId, update, Principal { "test-user" })
            }.definition.category
        )
    }

    @Test
    fun `rejects expense creation for non-member`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val json = """
            {
                "expenseId": "$expenseId", "description": "Unauthorized", "amount": {"currency": "EUR", "minor": "100"},
                "payers": [{"participantId": "$participantId", "amount": {"currency": "EUR", "minor": "100"}}],
                "allocation": {"mode": "EQUAL", "items": [{"participantId": "$participantId", "value": "1"}]}
            }
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .principal(Principal { "non-member" })
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "non-member-expense")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        ).andExpect(status().isNotFound)

        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .principal(Principal { "non-member" })
        ).andExpect(status().isNotFound)

        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupBalances(groupId))
                .principal(Principal { "non-member" })
        ).andExpect(status().isNotFound)

        mvc.perform(
            put(ApiEndpoints.ExpenseCore.V1.groupExpenseById(groupId, expenseId))
                .principal(Principal { "non-member" })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "version": 1, "description": "Unauthorized", "amount": {"currency": "EUR", "minor": "100"},
                        "payers": [{"participantId": "$participantId", "amount": {"currency": "EUR", "minor": "100"}}],
                        "allocation": {"mode": "EQUAL", "items": [{"participantId": "$participantId", "value": "1"}]}
                    }
                """.trimIndent())
        ).andExpect(status().isNotFound)

        mvc.perform(
            delete(ApiEndpoints.ExpenseCore.V1.groupExpenseById(groupId, expenseId) + "?version=1")
                .principal(Principal { "non-member" })
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `rejects expense creation for archived group`() {
        val groupId = UUID.randomUUID()
        archivedGroups += groupId
        val participantId = UUID.randomUUID()
        val json = """
            {
                "expenseId": "${UUID.randomUUID()}", "description": "Archived", "amount": {"currency": "EUR", "minor": "100"},
                "payers": [{"participantId": "$participantId", "amount": {"currency": "EUR", "minor": "100"}}],
                "allocation": {"mode": "EQUAL", "items": [{"participantId": "$participantId", "value": "1"}]}
            }
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "archived-group-expense")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        ).andExpect(status().isConflict)
    }

    @Test
    fun `creates expense with equal allocation successfully`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val aliceId = UUID.randomUUID()
        val bobId = UUID.randomUUID()

        val json = """
            {
                "expenseId": "$expenseId",
                "description": "Dinner in Vienna",
                "category": "food",
                "amount": { "currency": "EUR", "minor": "3000" },
                "payers": [
                    { "participantId": "$aliceId", "amount": { "currency": "EUR", "minor": "3000" } }
                ],
                "allocation": {
                    "mode": "EQUAL",
                    "items": [
                        { "participantId": "$aliceId", "value": "1" },
                        { "participantId": "$bobId", "value": "1" }
                    ]
                }
            }
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "idemp-key-test-12345")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.expenseId").value(expenseId.toString()))
            .andExpect(jsonPath("$.amount.minor").value("3000"))
            .andExpect(jsonPath("$.category").value("food"))
            .andExpect(jsonPath("$.allocations.length()").value(2))

        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].expenseId").value(expenseId.toString()))

        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupBalances(groupId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balances.length()").value(2))
    }

    @Test
    fun `creates expense with exact and percentage allocation modes`() {
        val groupId = UUID.randomUUID()
        val expenseId1 = UUID.randomUUID()
        val aliceId = UUID.randomUUID()
        val bobId = UUID.randomUUID()

        val exactJson = """
            {
                "expenseId": "$expenseId1",
                "description": "Groceries",
                "category": "shopping",
                "amount": { "currency": "EUR", "minor": "1000" },
                "payers": [
                    { "participantId": "$aliceId", "amount": { "currency": "EUR", "minor": "1000" } }
                ],
                "allocation": {
                    "mode": "EXACT",
                    "items": [
                        { "participantId": "$aliceId", "value": "600" },
                        { "participantId": "$bobId", "value": "400" }
                    ]
                }
            }
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "idemp-key-test-exact")
                .contentType(MediaType.APPLICATION_JSON)
                .content(exactJson)
        ).andExpect(status().isCreated)

        val expenseId2 = UUID.randomUUID()
        val percentJson = """
            {
                "expenseId": "$expenseId2",
                "description": "Taxi ride",
                "category": "transport",
                "amount": { "currency": "EUR", "minor": "2000" },
                "payers": [
                    { "participantId": "$bobId", "amount": { "currency": "EUR", "minor": "2000" } }
                ],
                "allocation": {
                    "mode": "PERCENT_BASIS_POINTS",
                    "items": [
                        { "participantId": "$aliceId", "value": "5000" },
                        { "participantId": "$bobId", "value": "5000" }
                    ]
                }
            }
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "idemp-key-test-percent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(percentJson)
        ).andExpect(status().isCreated)
    }

    @Test
    fun `rejects expense when payer amounts do not sum to total`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val aliceId = UUID.randomUUID()

        val json = """
            {
                "expenseId": "$expenseId",
                "description": "Coffee",
                "amount": { "currency": "EUR", "minor": "1000" },
                "payers": [
                    { "participantId": "$aliceId", "amount": { "currency": "EUR", "minor": "800" } }
                ],
                "allocation": {
                    "mode": "EQUAL",
                    "items": [
                        { "participantId": "$aliceId", "value": "1" }
                    ]
                }
            }
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "idemp-key-test-mismatch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `rejects request missing Idempotency-Key header`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val aliceId = UUID.randomUUID()

        val json = """
            {
                "expenseId": "$expenseId",
                "description": "Coffee",
                "amount": { "currency": "EUR", "minor": "500" },
                "payers": [
                    { "participantId": "$aliceId", "amount": { "currency": "EUR", "minor": "500" } }
                ],
                "allocation": {
                    "mode": "EQUAL",
                    "items": [
                        { "participantId": "$aliceId", "value": "1" }
                    ]
                }
            }
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `updates expense successfully and increments version`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val aliceId = UUID.randomUUID()
        val bobId = UUID.randomUUID()

        val createJson = """
            {
                "expenseId": "$expenseId",
                "description": "Original lunch",
                "category": "food",
                "amount": { "currency": "EUR", "minor": "2000" },
                "payers": [
                    { "participantId": "$aliceId", "amount": { "currency": "EUR", "minor": "2000" } }
                ],
                "allocation": {
                    "mode": "EQUAL",
                    "items": [
                        { "participantId": "$aliceId", "value": "1" },
                        { "participantId": "$bobId", "value": "1" }
                    ]
                }
            }
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "idemp-create-before-update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson)
        ).andExpect(status().isCreated)

        val updateJson = """
            {
                "version": 1,
                "description": "Updated lunch with desserts",
                "category": "food",
                "amount": { "currency": "EUR", "minor": "4000" },
                "payers": [
                    { "participantId": "$aliceId", "amount": { "currency": "EUR", "minor": "4000" } }
                ],
                "allocation": {
                    "mode": "EQUAL",
                    "items": [
                        { "participantId": "$aliceId", "value": "1" },
                        { "participantId": "$bobId", "value": "1" }
                    ]
                }
            }
        """.trimIndent()

        mvc.perform(
            put(ApiEndpoints.ExpenseCore.V1.groupExpenseById(groupId, expenseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expenseId").value(expenseId.toString()))
            .andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.amount.minor").value("4000"))
    }

    @Test
    fun `rejects update when version is stale with 409 Conflict`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val aliceId = UUID.randomUUID()

        val createJson = """
            {
                "expenseId": "$expenseId",
                "description": "Coffee",
                "category": "food",
                "amount": { "currency": "EUR", "minor": "500" },
                "payers": [
                    { "participantId": "$aliceId", "amount": { "currency": "EUR", "minor": "500" } }
                ],
                "allocation": {
                    "mode": "EQUAL",
                    "items": [
                        { "participantId": "$aliceId", "value": "1" }
                    ]
                }
            }
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "idemp-conflict-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson)
        ).andExpect(status().isCreated)

        // Version mismatch: sending version 99 when version is 1
        val updateJson = """
            {
                "version": 99,
                "description": "Stale update",
                "category": "food",
                "amount": { "currency": "EUR", "minor": "500" },
                "payers": [
                    { "participantId": "$aliceId", "amount": { "currency": "EUR", "minor": "500" } }
                ],
                "allocation": {
                    "mode": "EQUAL",
                    "items": [
                        { "participantId": "$aliceId", "value": "1" }
                    ]
                }
            }
        """.trimIndent()

        mvc.perform(
            put(ApiEndpoints.ExpenseCore.V1.groupExpenseById(groupId, expenseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson)
        ).andExpect(status().isConflict)
    }

    /** Verifies update validation rejects malformed payer values, currency drift, sums, and allocations before persistence. */
    @Test
    fun `rejects invalid update financial inputs before store mutation`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val participantId = UUID.randomUUID().toString()
        val principal = Principal { "test-user" }
        val base = UpdateExpenseRequest(
            version = 1,
            description = "Updated",
            amount = MoneyDto("EUR", "100"),
            payers = listOf(PayerDto(participantId, MoneyDto("EUR", "100"))),
            allocation = AllocationInputDto(
                "EXACT",
                listOf(AllocationItemDto(participantId, "100"))
            )
        )

        val invalidRequests = listOf(
            base.copy(payers = listOf(PayerDto(participantId, MoneyDto("EUR", "not-an-integer")))),
            base.copy(payers = listOf(PayerDto(participantId, MoneyDto("EUR", "-1")))),
            base.copy(payers = listOf(PayerDto(participantId, MoneyDto("USD", "100")))),
            base.copy(payers = listOf(PayerDto(participantId, MoneyDto("EUR", "99")))),
            base.copy(allocation = AllocationInputDto("EXACT", listOf(AllocationItemDto(participantId, "99"))))
        )

        invalidRequests.forEach { request ->
            val error = assertThrows<SquarewiseException> {
                controller.updateExpense(groupId, expenseId, request, principal)
            }
            assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
        }
    }

    /** Verifies create validation rejects malformed payer values, currency drift, sums, and allocations before persistence. */
    @Test
    fun `rejects invalid create financial inputs before store mutation`() {
        val groupId = UUID.randomUUID()
        val participantId = UUID.randomUUID().toString()
        val principal = Principal { "test-user" }
        val base = CreateExpenseRequest(
            expenseId = UUID.randomUUID(),
            description = "Created",
            amount = MoneyDto("EUR", "100"),
            payers = listOf(PayerDto(participantId, MoneyDto("EUR", "100"))),
            allocation = AllocationInputDto(
                "EXACT",
                listOf(AllocationItemDto(participantId, "100"))
            )
        )

        val invalidRequests = listOf(
            base.copy(payers = listOf(PayerDto(participantId, MoneyDto("EUR", "not-an-integer")))),
            base.copy(payers = listOf(PayerDto(participantId, MoneyDto("EUR", "-1")))),
            base.copy(payers = listOf(PayerDto(participantId, MoneyDto("USD", "100")))),
            base.copy(payers = listOf(PayerDto(participantId, MoneyDto("EUR", "99")))),
            base.copy(allocation = AllocationInputDto("EXACT", listOf(AllocationItemDto(participantId, "99"))))
        )

        invalidRequests.forEachIndexed { index, request ->
            val error = assertThrows<SquarewiseException> {
                controller.createExpense(groupId, "create-validation-$index", request, principal)
            }
            assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
        }
    }

    /** Verifies the controller rejects a category beyond the documented request bound. */
    @Test
    fun `rejects an overlong category before store mutation`() {
        val groupId = UUID.randomUUID()
        val participantId = UUID.randomUUID().toString()
        val request = CreateExpenseRequest(
            expenseId = UUID.randomUUID(),
            description = "Bounded category",
            category = "x".repeat(33),
            amount = MoneyDto("EUR", "100"),
            payers = listOf(PayerDto(participantId, MoneyDto("EUR", "100"))),
            allocation = AllocationInputDto(
                "EXACT",
                listOf(AllocationItemDto(participantId, "100"))
            )
        )

        val error = assertThrows<SquarewiseException> {
            controller.createExpense(groupId, "category-boundary", request, Principal { "test-user" })
        }

        assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
    }

    /** Verifies blank categories use the documented neutral category on create and update. */
    @Test
    fun `defaults blank category to other for create and update`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val participantId = UUID.randomUUID().toString()
        val principal = Principal { "test-user" }
        val allocation = AllocationInputDto(
            "EXACT",
            listOf(AllocationItemDto(participantId, "100"))
        )
        val payer = listOf(PayerDto(participantId, MoneyDto("EUR", "100")))

        val created = controller.createExpense(
            groupId,
            "blank-category-create",
            CreateExpenseRequest(
                expenseId = expenseId,
                description = "Created with blank category",
                category = "   ",
                amount = MoneyDto("EUR", "100"),
                payers = payer,
                allocation = allocation
            ),
            principal
        )
        assertEquals("other", created.category)

        val explicitNull = controller.createExpense(
            groupId,
            "null-category-create",
            CreateExpenseRequest(
                expenseId = UUID.randomUUID(),
                description = "Created with null category",
                category = null,
                amount = MoneyDto("EUR", "100"),
                payers = payer,
                allocation = allocation
            ),
            principal
        )
        assertEquals("other", explicitNull.category)

        val updated = controller.updateExpense(
            groupId,
            expenseId,
            UpdateExpenseRequest(
                version = 1,
                description = "Updated with blank category",
                category = "   ",
                amount = MoneyDto("EUR", "100"),
                payers = payer,
                allocation = allocation
            ),
            principal
        )
        assertEquals("other", updated.category)
    }

    @Test
    fun `deletes expense successfully and removes it from listing`() {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val aliceId = UUID.randomUUID()

        val createJson = """
            {
                "expenseId": "$expenseId",
                "description": "To be deleted",
                "category": "other",
                "amount": { "currency": "EUR", "minor": "1000" },
                "payers": [
                    { "participantId": "$aliceId", "amount": { "currency": "EUR", "minor": "1000" } }
                ],
                "allocation": {
                    "mode": "EQUAL",
                    "items": [
                        { "participantId": "$aliceId", "value": "1" }
                    ]
                }
            }
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "idemp-delete-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson)
        ).andExpect(status().isCreated)

        mvc.perform(delete(ApiEndpoints.ExpenseCore.V1.groupExpenseById(groupId, expenseId)))
            .andExpect(status().isNoContent)

        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    /** Verifies expense collection limits reject unbounded or empty pages at the public boundary. */
    @Test
    fun `rejects invalid expense list limits`() {
        val groupId = UUID.randomUUID()

        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId)).param("limit", "0"))
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))

        mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId)).param("limit", "101"))
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `rejects oversized idempotency key before financial processing`() {
        val groupId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val json = """
            {"expenseId":"${UUID.randomUUID()}","description":"bounded",
             "amount":{"currency":"EUR","minor":"100"},
             "payers":[{"participantId":"$participantId","amount":{"currency":"EUR","minor":"100"}}],
             "allocation":{"mode":"EQUAL","items":[{"participantId":"$participantId","value":"1"}]}}
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "x".repeat(201))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `rejects oversized category before financial processing`() {
        val groupId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val json = """
            {"expenseId":"${UUID.randomUUID()}","description":"bounded","category":"${"x".repeat(33)}",
             "amount":{"currency":"EUR","minor":"100"},
             "payers":[{"participantId":"$participantId","amount":{"currency":"EUR","minor":"100"}}],
             "allocation":{"mode":"EQUAL","items":[{"participantId":"$participantId","value":"1"}]}}
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "bounded-category")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `rejects oversized participant collections before financial processing`() {
        val groupId = UUID.randomUUID()
        val participantIds = (1..101).map { UUID.randomUUID() }
        val payers = participantIds.joinToString(",") {
            "{\"participantId\":\"$it\",\"amount\":{\"currency\":\"EUR\",\"minor\":\"1\"}}"
        }
        val json = """
            {"expenseId":"${UUID.randomUUID()}","description":"bounded",
             "amount":{"currency":"EUR","minor":"101"},"payers":[$payers],
             "allocation":{"mode":"EQUAL","items":[{"participantId":"${participantIds.first()}","value":"1"}]}}
        """.trimIndent()

        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.groupExpenses(groupId))
                .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, "bounded-participants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        ).andExpect(status().isUnprocessableContent)
    }

    /** Verifies the allocation-side participant limit is enforced independently of payer count. */
    @Test
    fun `rejects oversized allocation collection before financial processing`() {
        val groupId = UUID.randomUUID()
        val participantId = UUID.randomUUID().toString()
        val request = CreateExpenseRequest(
            expenseId = UUID.randomUUID(),
            description = "bounded allocations",
            amount = MoneyDto("EUR", "101"),
            payers = listOf(PayerDto(participantId, MoneyDto("EUR", "101"))),
            allocation = AllocationInputDto(
                "EQUAL",
                (1..101).map { AllocationItemDto(UUID.randomUUID().toString(), "1") }
            )
        )

        val error = assertThrows<SquarewiseException> {
            controller.createExpense(
                groupId,
                "bounded-allocation-count",
                request,
                Principal { "test-user" }
            )
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
    }

    /** Verifies simultaneous oversized payer and allocation collections fail at the shared bound. */
    @Test
    fun `rejects simultaneous oversized payer and allocation collections`() {
        val participantId = UUID.randomUUID().toString()
        val request = CreateExpenseRequest(
            expenseId = UUID.randomUUID(),
            description = "both collections bounded",
            amount = MoneyDto("EUR", "101"),
            payers = (1..101).map {
                PayerDto(participantId, MoneyDto("EUR", "1"))
            },
            allocation = AllocationInputDto(
                "EQUAL",
                (1..101).map { AllocationItemDto(UUID.randomUUID().toString(), "1") }
            )
        )

        val error = assertThrows<SquarewiseException> {
            controller.createExpense(
                UUID.randomUUID(),
                "both-collections-bounded",
                request,
                Principal { "test-user" }
            )
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
    }
}
