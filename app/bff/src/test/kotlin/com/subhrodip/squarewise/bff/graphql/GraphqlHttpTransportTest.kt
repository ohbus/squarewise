package com.subhrodip.squarewise.bff.graphql

import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

import com.subhrodip.squarewise.bff.transport.ExpenseCoreGateway
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import com.subhrodip.squarewise.bff.transport.AccountsGateway
import com.subhrodip.squarewise.bff.messaging.model.BffEventEnvelope
import com.subhrodip.squarewise.bff.messaging.model.ConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.DuplicateConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.ProcessedConsumptionResult
import com.subhrodip.squarewise.bff.messaging.service.BffEventConsumer
import com.subhrodip.squarewise.bff.messaging.persistence.BffEventDeduplicator
import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import com.subhrodip.squarewise.bff.realtime.LiveUpdate
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import com.subhrodip.squarewise.bff.transport.model.input.AllocationInput
import com.subhrodip.squarewise.bff.transport.model.input.AllocationItemInput
import com.subhrodip.squarewise.bff.transport.model.output.BffCreateGroup
import com.subhrodip.squarewise.bff.transport.model.output.BffExpense
import com.subhrodip.squarewise.bff.transport.model.output.BffGroup
import com.subhrodip.squarewise.bff.transport.model.output.BffMoney
import com.subhrodip.squarewise.bff.transport.model.output.BffProfile
import com.subhrodip.squarewise.bff.transport.model.output.BffSettlement
import com.subhrodip.squarewise.bff.transport.model.output.BffSuggestedSettlement
import com.subhrodip.squarewise.bff.transport.model.auth.AccountsTokenResponse
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginStartRequest
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginStartResponse
import com.subhrodip.squarewise.bff.transport.model.input.CreateExpenseInput
import com.subhrodip.squarewise.bff.transport.model.input.MoneyInput
import com.subhrodip.squarewise.bff.transport.model.input.PayerInput
import com.subhrodip.squarewise.bff.transport.model.input.RepaymentInput

import com.subhrodip.squarewise.bff.transport.UpstreamServiceException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.`when`
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Mono
import java.util.concurrent.TimeoutException
import java.util.UUID

/** Verifies the real WebFlux GraphQL HTTP handler and its response envelope. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration",
        "squarewise.security.browser.allowed-origins=https://app.example.test",
        "squarewise.bff.rate-limit.enabled=false"
    ]
)
class GraphqlHttpTransportTest {
    @TestConfiguration
    class PermitAllSecurity {
        @Bean
        fun testSecurity(http: ServerHttpSecurity): SecurityWebFilterChain =
            http.csrf { it.disable() }.authorizeExchange { it.anyExchange().permitAll() }.build()
    }

    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: WebTestClient

    @BeforeEach
    fun setUpClient() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @MockitoBean
    private lateinit var accountsGateway: AccountsGateway

    @MockitoBean
    private lateinit var expenseCoreGateway: ExpenseCoreGateway

    private fun expectGraphqlError(query: String, privateDetail: String) {
        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to query))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.errors").isArray
            .jsonPath("$.data").isEqualTo(null)
            .jsonPath("$.errors[0].message").value<String> { message ->
                assert(!message.contains(privateDetail))
            }
    }

    @Test
    fun `graphql route returns data for a profile query`() {
        `when`(accountsGateway.getMe(null)).thenReturn(
            Mono.just(BffProfile("account-1", "Alice", "Europe/Vienna", "EUR"))
        )

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "{ me { accountId displayName } }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.me.accountId").isEqualTo("account-1")
            .jsonPath("$.data.me.displayName").isEqualTo("Alice")
    }

    @Test
    fun `graphql route exposes group and settlement suggestion queries`() {
        val group = BffGroup("group-1", "Trip", "TRIP", revision = 2)
        `when`(expenseCoreGateway.listGroups(null)).thenReturn(Mono.just(listOf(group)))
        `when`(expenseCoreGateway.getSettlementSuggestions("group-1", null)).thenReturn(
            Mono.just(listOf(BffSuggestedSettlement("alice", "bob", 1250, "EUR")))
        )

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "{ groups { id name revision } settlementSuggestions(groupId: \"group-1\") { fromParticipantId amount { minor currency } } }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.groups[0].id").isEqualTo("group-1")
            .jsonPath("$.data.settlementSuggestions[0].amount.minor").isEqualTo("1250")
    }

    @Test
    fun `graphql route exposes group and repayment mutations`() {
        val group = BffGroup("group-2", "Household", "HOUSEHOLD", revision = 1)
        val settlement = BffSettlement("settlement-1", "alice", "bob", 500, "RECORDED", "EUR")
        `when`(expenseCoreGateway.createGroup(BffCreateGroup("Household", "HOUSEHOLD", "EUR"), null))
            .thenReturn(Mono.just(group))
        `when`(expenseCoreGateway.recordRepayment("group-2", RepaymentInput("group-2", "alice", "bob", MoneyInput("EUR", "500"), "repaid", "repayment-key-0001"), null))
            .thenReturn(Mono.just(settlement))

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "mutation { createGroup(input: { name: \"Household\", kind: HOUSEHOLD, currency: \"EUR\" }) { id name } recordRepayment(input: { groupId: \"group-2\", fromParticipantId: \"alice\", toParticipantId: \"bob\", amount: { currency: \"EUR\", minor: \"500\" }, reason: \"repaid\", idempotencyKey: \"repayment-key-0001\" }) { id status amount { minor currency } } }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.createGroup.id").isEqualTo("group-2")
            .jsonPath("$.data.recordRepayment.amount.minor").isEqualTo("500")
    }

    @Test
    fun `graphql route exposes group update and expense creation mutations`() {
        val group = BffGroup("group-3", "Renamed", "TRIP", revision = 3)
        val expenseId = UUID.randomUUID().toString()
        val input = CreateExpenseInput(
            expenseId,
            "Dinner",
            MoneyInput("EUR", "1000"),
            listOf(PayerInput("alice", MoneyInput("EUR", "1000"))),
            AllocationInput("EQUAL", listOf(AllocationItemInput("bob", "1")))
        )
        val expense = BffExpense(expenseId, 1, "Dinner", BffMoney("EUR", "1000"))
        `when`(expenseCoreGateway.updateGroup("group-3", "Renamed", null)).thenReturn(Mono.just(group))
        `when`(expenseCoreGateway.createExpense("group-3", input, "idempotency-key-1234", null))
            .thenReturn(Mono.just(expense))

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "mutation { updateGroup(groupId: \"group-3\", name: \"Renamed\") { id name revision } }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.updateGroup.name").isEqualTo("Renamed")

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(
                mapOf(
                    "query" to "mutation { createExpense(groupId: \"group-3\", input: { expenseId: \"$expenseId\", description: \"Dinner\", " +
                        "amount: { currency: \"EUR\", minor: \"1000\" }, payers: [{ participantId: \"alice\", amount: { currency: \"EUR\", minor: \"1000\" } }], " +
                        "allocation: { mode: \"EQUAL\", items: [{ participantId: \"bob\", value: \"1\" }] } }, " +
                        "idempotencyKey: \"idempotency-key-1234\") { id description amount { minor currency } } }"
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.createExpense.id").isEqualTo(expenseId)
            .jsonPath("$.data.createExpense.amount.minor").isEqualTo("1000")
    }

    @Test
    fun `graphql route rejects malformed required variables before calling upstream`() {
        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "mutation { createExpense(groupId: \"not-a-uuid\", input: { expenseId: \"${UUID.randomUUID()}\", description: \"Dinner\", amount: { currency: \"EUR\", minor: \"100\" }, payers: [], allocation: { mode: EQUAL, items: [] } }, idempotencyKey: \"short\") { id } }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.errors").isArray
            .jsonPath("$.data").doesNotExist()
    }

    @Test
    fun `graphql route returns errors for an invalid field`() {
        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "{ doesNotExist }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.errors").isArray
            .jsonPath("$.errors[0].extensions.code").isEqualTo("VALIDATION_ERROR")
            .jsonPath("$.data").doesNotExist()
    }

    /**
     * Verifies malformed JSON is rejected by the HTTP transport before GraphQL
     * execution; no upstream gateway call should be required for this failure.
     */
    @Test
    fun `graphql route rejects malformed json payload`() {
        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .header(ApiEndpoints.Headers.CONTENT_TYPE, ApiEndpoints.Headers.APPLICATION_JSON)
            .bodyValue("{\"query\":")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `graphql route rejects an untrusted browser origin`() {
        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .header("Origin", "https://evil.example.test")
            .bodyValue(mapOf("query" to "{ __typename }"))
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `graphql route answers preflight only for an exact configured origin`() {
        client.options().uri(ApiEndpoints.Bff.GRAPHQL)
            .header("Origin", "https://app.example.test")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Authorization,Content-Type")
            .exchange()
            .expectStatus().isNoContent
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://app.example.test")
    }

    @Test
    fun `browser verification sets secure httpOnly cookies without returning refresh token`() {
        `when`(accountsGateway.verifyBrowserLogin("one-time-credential")).thenReturn(
            Mono.just(AccountsTokenResponse("access-jwt", "Bearer", 600, "refresh-secret"))
        )

        client.post().uri(ApiEndpoints.Bff.BROWSER_LOGIN_VERIFY)
            .header("Origin", "https://app.example.test")
            .bodyValue(mapOf("credential" to "one-time-credential"))
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueMatches("Set-Cookie", ".*squarewise_access=access-jwt.*Secure.*HTTPOnly.*")
            .expectBody()
            .jsonPath("$.accessToken").doesNotExist()
            .jsonPath("$.expiresIn").isEqualTo(600)
            .jsonPath("$.refreshToken").doesNotExist()
    }

    @Test
    fun `browser login start forwards the validated request`() {
        `when`(accountsGateway.startBrowserLogin(BrowserLoginStartRequest("alice@example.test", "EMAIL")))
            .thenReturn(Mono.just(BrowserLoginStartResponse("ACCEPTED", 60)))

        client.post().uri(ApiEndpoints.Bff.BROWSER_LOGIN_START)
            .header("Origin", "https://app.example.test")
            .bodyValue(mapOf("email" to "alice@example.test", "channel" to "EMAIL"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("ACCEPTED")
            .jsonPath("$.retryAfterSeconds").isEqualTo(60)
    }

    @Test
    fun `browser refresh requires exact origin and csrf proof`() {
        client.post().uri(ApiEndpoints.Bff.BROWSER_TOKEN_REFRESH)
            .header("Origin", "https://app.example.test")
            .cookie("squarewise_refresh", "refresh-secret")
            .exchange()
            .expectStatus().isForbidden

        `when`(accountsGateway.refreshBrowserSession("refresh-secret")).thenReturn(
            Mono.just(AccountsTokenResponse("new-access", "Bearer", 600, "new-refresh"))
        )
        client.post().uri(ApiEndpoints.Bff.BROWSER_TOKEN_REFRESH)
            .header("Origin", "https://app.example.test")
            .header("X-CSRF-Token", "nonce")
            .cookie("squarewise_refresh", "refresh-secret")
            .cookie("squarewise_csrf", "nonce")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accessToken").doesNotExist()
            .jsonPath("$.expiresIn").isEqualTo(600)
            .jsonPath("$.refreshToken").doesNotExist()
    }

    @Test
    fun `browser refresh without a cookie returns the authentication error`() {
        client.post().uri(ApiEndpoints.Bff.BROWSER_TOKEN_REFRESH)
            .header("Origin", "https://app.example.test")
            .header("X-CSRF-Token", "nonce")
            .cookie("squarewise_csrf", "nonce")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `browser logout revokes the rotated family and clears all cookies`() {
        `when`(accountsGateway.refreshBrowserSession("refresh-secret")).thenReturn(
            Mono.just(AccountsTokenResponse("access-jwt", "Bearer", 600, "rotated-refresh"))
        )
        `when`(accountsGateway.logoutBrowserSession("access-jwt", "rotated-refresh"))
            .thenReturn(Mono.empty())

        client.post().uri(ApiEndpoints.Bff.BROWSER_LOGOUT)
            .header("Origin", "https://app.example.test")
            .header("X-CSRF-Token", "nonce")
            .cookie("squarewise_refresh", "refresh-secret")
            .cookie("squarewise_csrf", "nonce")
            .exchange()
            .expectStatus().isNoContent
            .expectHeader().valueMatches("Set-Cookie", ".*squarewise_access=;.*Max-Age=0.*")

        verify(accountsGateway).logoutBrowserSession("access-jwt", "rotated-refresh")
    }

    @Test
    fun `browser logout without a refresh cookie is idempotent and clears cookies`() {
        client.post().uri(ApiEndpoints.Bff.BROWSER_LOGOUT)
            .header("Origin", "https://app.example.test")
            .header("X-CSRF-Token", "nonce")
            .cookie("squarewise_csrf", "nonce")
            .exchange()
            .expectStatus().isNoContent
            .expectHeader().valueMatches("Set-Cookie", ".*squarewise_access=;.*Max-Age=0.*")

        verifyNoInteractions(accountsGateway)
    }

    @Test
    fun `browser auth rejects an untrusted origin before upstream execution`() {
        client.post().uri(ApiEndpoints.Bff.BROWSER_LOGIN_VERIFY)
            .header("Origin", "https://evil.example.test")
            .bodyValue(mapOf("credential" to "one-time-credential"))
            .exchange()
            .expectStatus().isForbidden

        verifyNoInteractions(accountsGateway)
    }

    @Test
    fun `upstream authorization failure remains a GraphQL error without leaking detail`() {
        `when`(expenseCoreGateway.getGroup("group-1", null))
            .thenReturn(Mono.error(UpstreamServiceException(403, "private authorization detail")))

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "{ group(id: \"group-1\") { id } }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.errors").isArray
            .jsonPath("$.errors[0].message").value<String> { message ->
                assert(!message.contains("private authorization detail"))
            }
    }

    @Test
    fun `upstream rate limiting is exposed as the GraphQL rate limited code`() {
        `when`(expenseCoreGateway.getGroup("group-rate-limited", null))
            .thenReturn(Mono.error(UpstreamServiceException(429, "private rate limit detail")))

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "{ group(id: \"group-rate-limited\") { id } }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.errors[0].extensions.code").isEqualTo(CategoryCode.RATE_LIMIT_EXCEEDED.name)
            .jsonPath("$.errors[0].extensions.retryAfterSeconds").isEqualTo(60)
            .jsonPath("$.errors[0].extensions.requestId").isNotEmpty
            .jsonPath("$.errors[0].message").isEqualTo(PlatformErrors.SECURITY_RATE_LIMITED.safeDetail)
    }

    @Test
    fun `upstream validation and timeout failures use the GraphQL error envelope`() {
        `when`(expenseCoreGateway.updateGroup("group-1", "", null))
            .thenReturn(Mono.error(UpstreamServiceException(400, "private validation detail")))

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "mutation { updateGroup(groupId: \"group-1\", name: \"\") { id } }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.errors").isArray
            .jsonPath("$.errors[0].message").value<String> { message ->
                assert(!message.contains("private validation detail"))
            }

        `when`(expenseCoreGateway.getGroup("group-2", null))
            .thenReturn(Mono.error(TimeoutException("private timeout detail")))

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "{ group(id: \"group-2\") { id } }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.errors").isArray
            .jsonPath("$.errors[0].message").value<String> { message ->
                assert(!message.contains("private timeout detail"))
            }
    }

    @Test
    fun `malformed upstream response is represented by a GraphQL error`() {
        `when`(expenseCoreGateway.getGroup("group-3", null))
            .thenReturn(Mono.error(IllegalStateException("private malformed payload")))

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "{ group(id: \"group-3\") { id } }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.errors").isArray
            .jsonPath("$.errors[0].message").value<String> { message ->
                assert(!message.contains("private malformed payload"))
            }
    }

    /** Verifies every GraphQL query converts upstream failures into redacted HTTP 200 error envelopes. */
    @Test
    fun `query operations redact upstream timeout and failure details`() {
        `when`(accountsGateway.getMe(null))
            .thenReturn(Mono.error(TimeoutException("private profile timeout")))
        expectGraphqlError(
            query = "{ me { accountId } }",
            privateDetail = "private profile timeout"
        )

        `when`(expenseCoreGateway.listGroups(null))
            .thenReturn(Mono.error(TimeoutException("private groups timeout")))
        expectGraphqlError(
            query = "{ groups { id } }",
            privateDetail = "private groups timeout"
        )

        `when`(expenseCoreGateway.getSettlementSuggestions("group-failure", null))
            .thenReturn(Mono.error(UpstreamServiceException(503, "private suggestions failure")))
        expectGraphqlError(
            query = "{ settlementSuggestions(groupId: \"group-failure\") { fromParticipantId } }",
            privateDetail = "private suggestions failure"
        )
    }

    /** Verifies settlement suggestions preserve an upstream empty result as successful query data. */
    @Test
    fun `settlement suggestions expose empty upstream result`() {
        `when`(expenseCoreGateway.getSettlementSuggestions("group-empty", null)).thenReturn(Mono.just(emptyList()))

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to "{ settlementSuggestions(groupId: \"group-empty\") { fromParticipantId } }"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.settlementSuggestions").isArray
            .jsonPath("$.data.settlementSuggestions").isEmpty
    }

    /** Verifies every GraphQL mutation converts upstream failures into redacted HTTP 200 error envelopes. */
    @Test
    fun `mutation operations redact upstream conflict timeout and dependency failures`() {
        val createGroupInput = BffCreateGroup("Conflict", "TRIP", "EUR")
        `when`(expenseCoreGateway.createGroup(createGroupInput, null))
            .thenReturn(Mono.error(UpstreamServiceException(409, "private group conflict")))
        expectGraphqlError(
            query = "mutation { createGroup(input: { name: \"Conflict\", kind: TRIP, currency: \"EUR\" }) { id } }",
            privateDetail = "private group conflict"
        )

        `when`(expenseCoreGateway.updateGroup("group-timeout", "Renamed", null))
            .thenReturn(Mono.error(TimeoutException("private update timeout")))
        expectGraphqlError(
            query = "mutation { updateGroup(groupId: \"group-timeout\", name: \"Renamed\") { id } }",
            privateDetail = "private update timeout"
        )

        val expenseId = UUID.randomUUID().toString()
        val expenseInput = CreateExpenseInput(
            expenseId,
            "Failure",
            MoneyInput("EUR", "100"),
            listOf(PayerInput("alice", MoneyInput("EUR", "100"))),
            AllocationInput("EQUAL", listOf(AllocationItemInput("alice", "1")))
        )
        `when`(expenseCoreGateway.createExpense("group-failure", expenseInput, "dependency-failure-key", null))
            .thenReturn(Mono.error(UpstreamServiceException(503, "private expense dependency")))
        expectGraphqlError(
            query = "mutation { createExpense(groupId: \"group-failure\", input: { expenseId: \"$expenseId\", " +
                "description: \"Failure\", amount: { currency: \"EUR\", minor: \"100\" }, " +
                "payers: [{ participantId: \"alice\", amount: { currency: \"EUR\", minor: \"100\" } }], " +
                "allocation: { mode: \"EQUAL\", items: [{ participantId: \"alice\", value: \"1\" }] } }, " +
                "idempotencyKey: \"dependency-failure-key\") { id } }",
            privateDetail = "private expense dependency"
        )

        val repaymentInput = RepaymentInput(
            "group-conflict",
            "alice",
            "bob",
            MoneyInput("EUR", "100"),
            "conflict",
            "repayment-key-0002"
        )
        `when`(expenseCoreGateway.recordRepayment("group-conflict", repaymentInput, null))
            .thenReturn(Mono.error(UpstreamServiceException(409, "private repayment conflict")))
        expectGraphqlError(
            query = "mutation { recordRepayment(input: { groupId: \"group-conflict\", fromParticipantId: \"alice\", " +
                "toParticipantId: \"bob\", amount: { currency: \"EUR\", minor: \"100\" }, reason: \"conflict\", idempotencyKey: \"repayment-key-0002\" }) { id } }",
            privateDetail = "private repayment conflict"
        )
    }

    @Test
    fun `rejects complexity over the configured bound before gateway execution`() {
        val query = (1..40).joinToString(" ") { alias ->
            "group$alias: groups { id name revision }"
        }.let { "{ $it }" }

        client.post().uri(ApiEndpoints.Bff.GRAPHQL)
            .bodyValue(mapOf("query" to query))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.errors").isArray
            .jsonPath("$.errors[0].extensions.code").isEqualTo(CategoryCode.RATE_LIMIT_EXCEEDED.name)
            .jsonPath("$.data").doesNotExist()

        verifyNoInteractions(accountsGateway, expenseCoreGateway)
    }
}
