package com.subhrodip.squarewise.bff

import com.subhrodip.squarewise.bff.transport.UpstreamServiceException
import com.subhrodip.squarewise.bff.transport.model.input.AllocationInput
import com.subhrodip.squarewise.bff.transport.model.input.CreateExpenseInput
import com.subhrodip.squarewise.bff.transport.model.input.MoneyInput
import com.subhrodip.squarewise.bff.transport.model.input.PayerInput
import com.subhrodip.squarewise.bff.transport.model.input.RepaymentInput
import com.subhrodip.squarewise.bff.transport.model.output.BffCreateGroup

import com.subhrodip.squarewise.bff.transport.ExpenseCoreGateway
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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.core.codec.DecodingException
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import reactor.core.Exceptions
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * HTTP double tests verifying bounded group-member fanout behavior in [ExpenseCoreGateway].
 *
 * Covers:
 * - Zero, one, and many groups member resolution.
 * - Concurrency bound of at most 4 simultaneous upstream member requests during [ExpenseCoreGateway.listGroups].
 * - Exact preservation of group response order.
 * - Deduplication / request count guarantees (exactly one member request per group).
 * - Upstream error propagation (HTTP 404, 401/403, 5xx) via [UpstreamServiceException].
 * - Malformed member payload error handling.
 * - Upstream timeout handling.
 * - Single group member and details resolution via [ExpenseCoreGateway.getGroup].
 */
class BffFanoutTest {

    private lateinit var server: HttpServer
    private lateinit var serverExecutor: ExecutorService
    private lateinit var gateway: ExpenseCoreGateway
    private var port: Int = 0

    private val recordedPaths = CopyOnWriteArrayList<String>()
    private val memberRequestCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val activeMemberRequests = AtomicInteger(0)
    private val peakMemberConcurrency = AtomicInteger(0)

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        serverExecutor = Executors.newCachedThreadPool()
        server.executor = serverExecutor
        port = server.address.port
        server.start()

        gateway = gatewayWithTimeout(Duration.ofSeconds(5))
    }

    private fun gatewayWithTimeout(timeout: Duration): ExpenseCoreGateway =
        ExpenseCoreGateway(
            builder = WebClient.builder(),
            baseUrl = "http://localhost:$port",
            timeout = timeout
        )

    @AfterEach
    fun tearDown() {
        server.stop(0)
        serverExecutor.shutdownNow()
        serverExecutor.awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun registerHandler(path: String, handler: (HttpExchange) -> Unit) {
        server.createContext(path, HttpHandler { exchange ->
            recordedPaths.add(exchange.requestURI.path)
            handler(exchange)
        })
    }

    private fun respondJson(exchange: HttpExchange, statusCode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set(ApiEndpoints.Headers.CONTENT_TYPE, ApiEndpoints.Headers.APPLICATION_JSON)
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun groupJson(groupId: String, name: String, kind: String = "TRIP", revision: Long = 1): String {
        return """{"groupId":"$groupId","name":"$name","kind":"$kind","revision":$revision,"balances":[],"expenses":[],"members":[]}"""
    }

    /**
     * Verifies that when Expense Core returns an empty group list, listGroups returns an empty list
     * without triggering any downstream member resolution requests.
     */
    @Test
    fun `resolves zero groups without triggering member requests`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            respondJson(exchange, 200, "[]")
        }

        val groups = gateway.listGroups("bearer-token").block()

        assertThat(groups).isNotNull
        assertThat(groups).isEmpty()
        assertThat(recordedPaths).containsExactly(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS)
    }

    /**
     * Verifies that when a single group is returned, its members are correctly fetched and attached.
     */
    @Test
    fun `resolves single group with members successfully`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            respondJson(exchange, 200, "[${groupJson("g-1", "Trip Alpha")}]")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("g-1")) { exchange ->
            respondJson(exchange, 200, """[{"membershipId":"m-1","subject":"alice"},{"membershipId":"m-2","subject":"bob"}]""")
        }

        val groups = gateway.listGroups("bearer-token").block()

        assertThat(groups).isNotNull
        assertThat(groups).hasSize(1)
        val group = groups!![0]
        assertThat(group.groupId).isEqualTo("g-1")
        assertThat(group.name).isEqualTo("Trip Alpha")
        assertThat(group.members).hasSize(2)
        assertThat(group.members.map { it.subject }).containsExactly("alice", "bob")
    }

    /**
     * Verifies bounded concurrency, request deduplication, and ordering for many groups.
     *
     * Concurrency invariant: flatMapSequential(..., 4) limits concurrent member requests
     * to at most 4 simultaneous executions, while strictly preserving group sequence order.
     * Also verifies that no duplicate member requests occur for each group.
     */
    @Test
    fun `resolves many groups with at most 4 concurrent member requests and preserves order`() {
        val groupCount = 10
        val groupsJson = (1..groupCount).joinToString(",", "[", "]") { i ->
            groupJson("g-$i", "Group $i", revision = i.toLong())
        }

        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            respondJson(exchange, 200, groupsJson)
        }

        // Each member request delays briefly to observe concurrency and tracks invocation count
        for (i in 1..groupCount) {
            val gid = "g-$i"
            memberRequestCounts[gid] = AtomicInteger(0)
            registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers(gid)) { exchange ->
                memberRequestCounts[gid]!!.incrementAndGet()
                val current = activeMemberRequests.incrementAndGet()
                peakMemberConcurrency.accumulateAndGet(current) { prev, next -> maxOf(prev, next) }
                try {
                    Thread.sleep(60)
                } finally {
                    activeMemberRequests.decrementAndGet()
                }
                respondJson(exchange, 200, """[{"membershipId":"m-$i","subject":"user-$i"}]""")
            }
        }

        val groups = gateway.listGroups("bearer-token").block()

        assertThat(groups).isNotNull
        assertThat(groups).hasSize(groupCount)

        // Verify order strictly matches groups response
        for (i in 1..groupCount) {
            val group = groups!![i - 1]
            assertThat(group.groupId).isEqualTo("g-$i")
            assertThat(group.name).isEqualTo("Group $i")
            assertThat(group.members).hasSize(1)
            assertThat(group.members[0].subject).isEqualTo("user-$i")
            // Verify no duplicate member request occurred
            assertThat(memberRequestCounts["g-$i"]?.get()).isEqualTo(1)
        }

        // Invariant check: concurrency must never exceed 4 and must achieve concurrency > 1
        assertThat(peakMemberConcurrency.get()).isLessThanOrEqualTo(4)
        assertThat(peakMemberConcurrency.get()).isGreaterThanOrEqualTo(2)
    }

    /**
     * Verifies that if one member resolution fails with HTTP 404, listGroups fails immediately
     * with an [UpstreamServiceException] indicating status 404.
     */
    @Test
    fun `propagates upstream 404 during member resolution in listGroups`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            respondJson(exchange, 200, "[${groupJson("g-1", "Group 1")}]")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("g-1")) { exchange ->
            respondJson(exchange, 404, """{"error":"Not Found"}""")
        }

        val ex = assertThrows<RuntimeException> {
            gateway.listGroups("bearer-token").block()
        }
        val cause = Exceptions.unwrap(ex)
        assertThat(cause).isInstanceOf(UpstreamServiceException::class.java)
        val upstreamEx = cause as UpstreamServiceException
        assertThat(upstreamEx.status).isEqualTo(404)
    }

    /**
     * Verifies that upstream 401 authorization error during listGroups propagates as [UpstreamServiceException].
     */
    @Test
    fun `propagates upstream 401 unauthorized failure`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            respondJson(exchange, 401, """{"error":"Unauthorized"}""")
        }

        val ex = assertThrows<RuntimeException> {
            gateway.listGroups("bearer-token").block()
        }
        val cause = Exceptions.unwrap(ex)
        assertThat(cause).isInstanceOf(UpstreamServiceException::class.java)
        assertThat((cause as UpstreamServiceException).status).isEqualTo(401)
    }

    /**
     * Verifies that upstream 403 forbidden error during member resolution propagates as [UpstreamServiceException].
     */
    @Test
    fun `propagates upstream 403 forbidden failure during member resolution`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            respondJson(exchange, 200, "[${groupJson("g-1", "Group 1")}]")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("g-1")) { exchange ->
            respondJson(exchange, 403, """{"error":"Forbidden"}""")
        }

        val ex = assertThrows<RuntimeException> {
            gateway.listGroups("bearer-token").block()
        }
        val cause = Exceptions.unwrap(ex)
        assertThat(cause).isInstanceOf(UpstreamServiceException::class.java)
        assertThat((cause as UpstreamServiceException).status).isEqualTo(403)
    }

    /**
     * Verifies that upstream 5xx server errors propagate as [UpstreamServiceException].
     */
    @Test
    fun `propagates upstream 5xx errors from member resolution`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            respondJson(exchange, 200, "[${groupJson("g-1", "Group 1")}]")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("g-1")) { exchange ->
            respondJson(exchange, 503, """{"error":"Service Unavailable"}""")
        }

        val ex = assertThrows<RuntimeException> {
            gateway.listGroups("bearer-token").block()
        }
        val cause = Exceptions.unwrap(ex)
        assertThat(cause).isInstanceOf(UpstreamServiceException::class.java)
        assertThat((cause as UpstreamServiceException).status).isEqualTo(503)
    }

    /**
     * Verifies timeout behavior when upstream group or member endpoint exceeds configured timeout.
     */
    @Test
    fun `times out when upstream member resolution exceeds configured duration`() {
        val shortTimeoutGateway = gatewayWithTimeout(Duration.ofMillis(100))
        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            respondJson(exchange, 200, "[${groupJson("g-1", "Group 1")}]")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("g-1")) { exchange ->
            Thread.sleep(500)
            respondJson(exchange, 200, "[]")
        }

        val ex = assertThrows<RuntimeException> {
            shortTimeoutGateway.listGroups("bearer-token").block()
        }
        val cause = Exceptions.unwrap(ex)
        assertThat(cause).isInstanceOf(TimeoutException::class.java)
    }

    /**
     * Verifies handling when upstream returns a malformed member payload.
     */
    @Test
    fun `fails when upstream returns malformed member payload`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            respondJson(exchange, 200, "[${groupJson("g-1", "Group 1")}]")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("g-1")) { exchange ->
            respondJson(exchange, 200, "not-valid-json")
        }

        val ex = assertThrows<RuntimeException> {
            gateway.listGroups("bearer-token").block()
        }
        val cause = Exceptions.unwrap(ex)
        assertThat(cause).isInstanceOf(DecodingException::class.java)
    }

    /**
     * Verifies getGroup fetches group, balances, expenses, and members concurrently,
     * propagating all financial fanout requests while populating members.
     */
    @Test
    fun `getGroup resolves group with balances, expenses, and members`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupById("g-1")) { exchange ->
            respondJson(exchange, 200, groupJson("g-1", "Group 1", revision = 2))
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupBalances("g-1")) { exchange ->
            respondJson(exchange, 200, """{"groupId":"g-1","balances":[{"participantId":"p-1","amount":{"currency":"EUR","minor":"100"}}]}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupExpenses("g-1")) { exchange ->
            respondJson(exchange, 200, """[{"expenseId":"e-1","version":1,"description":"Lunch","amount":{"currency":"EUR","minor":"200"},"allocations":[]}]""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("g-1")) { exchange ->
            respondJson(exchange, 200, """[{"membershipId":"m-1","subject":"alice"}]""")
        }

        val group = gateway.getGroup("g-1", "bearer-token").block()

        assertThat(group).isNotNull
        assertThat(group!!.groupId).isEqualTo("g-1")
        assertThat(group.balances).hasSize(1)
        assertThat(group.expenses).hasSize(1)
        assertThat(group.members).hasSize(1)
        assertThat(group.members[0].subject).isEqualTo("alice")
    }

    /** Verifies a balance failure cannot be presented as an empty successful balance list. */
    @Test
    fun `getGroup propagates balance fanout failure`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupById("g-balance-failure")) { exchange ->
            respondJson(exchange, 200, groupJson("g-balance-failure", "Group"))
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupBalances("g-balance-failure")) { exchange ->
            respondJson(exchange, 503, """{"error":"Balances unavailable"}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupExpenses("g-balance-failure")) { exchange ->
            respondJson(exchange, 200, "[]")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("g-balance-failure")) { exchange ->
            respondJson(exchange, 200, "[]")
        }

        val ex = assertThrows<RuntimeException> {
            gateway.getGroup("g-balance-failure", "bearer-token").block()
        }
        val cause = Exceptions.unwrap(ex)
        assertThat(cause).isInstanceOf(UpstreamServiceException::class.java)
        assertThat((cause as UpstreamServiceException).status).isEqualTo(503)
    }

    /** Verifies an expense failure cannot be presented as an empty successful expense list. */
    @Test
    fun `getGroup propagates expense fanout failure`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupById("g-expense-failure")) { exchange ->
            respondJson(exchange, 200, groupJson("g-expense-failure", "Group"))
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupBalances("g-expense-failure")) { exchange ->
            respondJson(exchange, 200, """{"groupId":"g-expense-failure","balances":[]}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupExpenses("g-expense-failure")) { exchange ->
            respondJson(exchange, 502, """{"error":"Expenses unavailable"}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("g-expense-failure")) { exchange ->
            respondJson(exchange, 200, "[]")
        }

        val ex = assertThrows<RuntimeException> {
            gateway.getGroup("g-expense-failure", "bearer-token").block()
        }
        val cause = Exceptions.unwrap(ex)
        assertThat(cause).isInstanceOf(UpstreamServiceException::class.java)
        assertThat((cause as UpstreamServiceException).status).isEqualTo(502)
    }

    /**
     * Verifies getGroup propagates upstream 404 when group is not found.
     */
    @Test
    fun `getGroup propagates upstream 404 when group does not exist`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupById("missing")) { exchange ->
            respondJson(exchange, 404, """{"error":"Group not found"}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupBalances("missing")) { exchange ->
            respondJson(exchange, 404, """{"error":"Not found"}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupExpenses("missing")) { exchange ->
            respondJson(exchange, 404, """{"error":"Not found"}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("missing")) { exchange ->
            respondJson(exchange, 404, """{"error":"Not found"}""")
        }

        val ex = assertThrows<RuntimeException> {
            gateway.getGroup("missing", "bearer-token").block()
        }
        val cause = Exceptions.unwrap(ex)
        assertThat(cause).isInstanceOf(UpstreamServiceException::class.java)
        assertThat((cause as UpstreamServiceException).status).isEqualTo(404)
    }

    /** Verifies response mapping for the remaining Expense Core gateway operations. */
    @Test
    fun `maps group expense repayment and suggestion responses`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            respondJson(exchange, 200, """{"groupId":"g-1","name":"Trip","kind":"TRIP","revision":4}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupById("g-1")) { exchange ->
            respondJson(exchange, 200, """{"groupId":"g-1","name":"Renamed","kind":"TRIP","revision":5}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupExpenses("g-1")) { exchange ->
            respondJson(exchange, 200, """{"expenseId":"e-1","version":2,"amount":{"currency":"EUR","minor":"1200"},"category":"travel","allocations":[]}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupSettlements("g-1")) { exchange ->
            respondJson(exchange, 200, """{"id":"s-1","fromParticipantId":"p-1","toParticipantId":"p-2","amountMinor":500,"currency":"EUR","status":"RECORDED"}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions("g-1")) { exchange ->
            respondJson(exchange, 200, """[{"fromParticipantId":"p-1","toParticipantId":"p-2","amountMinor":700,"currency":"EUR"}]""")
        }

        val group = gateway.createGroup(BffCreateGroup("Trip", "TRIP", "EUR"), "token").block()
        val updated = gateway.updateGroup("g-1", "Renamed", "token").block()
        val expense = gateway.createExpense(
            "g-1",
            CreateExpenseInput(
                "e-1",
                "Fallback description",
                MoneyInput("EUR", "1200"),
                listOf(PayerInput("p-1", MoneyInput("EUR", "1200"))),
                AllocationInput("EQUAL", emptyList())
            ),
            "idempotency-1",
            "token"
        ).block()
        val settlement = gateway.recordRepayment(
            "g-1",
            RepaymentInput("g-1", "p-1", "p-2", MoneyInput("EUR", "500"), null, "repayment-key-0001"),
            "token"
        ).block()
        val suggestions = gateway.getSettlementSuggestions("g-1", "token").block()

        assertThat(group?.groupId).isEqualTo("g-1")
        assertThat(updated?.name).isEqualTo("Renamed")
        assertThat(expense?.description).isEqualTo("Fallback description")
        assertThat(settlement?.amountMinor).isEqualTo(500)
        assertThat(suggestions).hasSize(1)
        assertThat(suggestions!![0].amountMinor).isEqualTo(700)
    }

    /** Verifies mutation gateway calls preserve upstream HTTP failure status. */
    @Test
    fun `propagates upstream mutation failures as typed service exceptions`() {
        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            respondJson(exchange, 422, """{"error":"invalid group"}""")
        }

        val ex = assertThrows<RuntimeException> {
            gateway.createGroup(BffCreateGroup("", "TRIP", "EUR"), "token").block()
        }

        val cause = Exceptions.unwrap(ex)
        assertThat(cause).isInstanceOf(UpstreamServiceException::class.java)
        assertThat((cause as UpstreamServiceException).status).isEqualTo(422)
    }

    /** Verifies nullable bearer inputs never create an empty or synthetic Authorization header. */
    @Test
    fun `omits authorization when gateway bearer is absent`() {
        val authorizationHeaders = CopyOnWriteArrayList<String>()
        fun capture(exchange: HttpExchange) {
            authorizationHeaders.addAll(
                exchange.requestHeaders.getOrDefault(ApiEndpoints.Headers.AUTHORIZATION, emptyList())
            )
        }

        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            capture(exchange)
            val response = if (exchange.requestMethod == "POST") {
                groupJson("g-null", "No Token")
            } else {
                "[${groupJson("g-null", "No Token")}]"
            }
            respondJson(exchange, 200, response)
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupById("g-null")) { exchange ->
            capture(exchange)
            respondJson(exchange, 200, groupJson("g-null", "No Token"))
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupBalances("g-null")) { exchange ->
            capture(exchange)
            respondJson(exchange, 200, """{"groupId":"g-null","balances":[]}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupExpenses("g-null")) { exchange ->
            capture(exchange)
            val response = if (exchange.requestMethod == "POST") {
                """{"expenseId":"e-null","version":1,"amount":{"currency":"EUR","minor":"1"},"allocations":[]}"""
            } else {
                "[]"
            }
            respondJson(exchange, 200, response)
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("g-null")) { exchange ->
            capture(exchange)
            respondJson(exchange, 200, "[]")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupSettlements("g-null")) { exchange ->
            capture(exchange)
            respondJson(exchange, 200, """{"id":"s-null","fromParticipantId":"p-1","toParticipantId":"p-2","amountMinor":1,"currency":"EUR","status":"RECORDED"}""")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupSettlementSuggestions("g-null")) { exchange ->
            capture(exchange)
            respondJson(exchange, 200, "[]")
        }

        gateway.listGroups(null).block()
        gateway.getGroup("g-null", null).block()
        gateway.createGroup(BffCreateGroup("No Token", "TRIP", "EUR"), null).block()
        gateway.updateGroup("g-null", "No Token", null).block()
        gateway.createExpense(
            "g-null",
            CreateExpenseInput(
                "e-null",
                "No Token",
                MoneyInput("EUR", "1"),
                listOf(PayerInput("p-null", MoneyInput("EUR", "1"))),
                AllocationInput("EQUAL", emptyList())
            ),
            "idempotency-null",
            null
        ).block()
        gateway.recordRepayment(
            "g-null",
            RepaymentInput("g-null", "p-1", "p-2", MoneyInput("EUR", "1"), null, "repayment-key-0002"),
            null
        ).block()
        gateway.getSettlementSuggestions("g-null", null).block()

        assertThat(authorizationHeaders).isEmpty()
    }

    /**
     * Verifies authorization header is forwarded to upstream endpoints.
     */
    @Test
    fun `forwards bearer authorization header to upstream endpoints`() {
        val capturedAuthHeaders = CopyOnWriteArrayList<String>()
        registerHandler(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS) { exchange ->
            capturedAuthHeaders.addAll(exchange.requestHeaders.getOrDefault(ApiEndpoints.Headers.AUTHORIZATION, emptyList()))
            respondJson(exchange, 200, "[${groupJson("g-auth", "Auth Group")}]")
        }
        registerHandler(ApiEndpoints.ExpenseCore.V1.groupMembers("g-auth")) { exchange ->
            capturedAuthHeaders.addAll(exchange.requestHeaders.getOrDefault(ApiEndpoints.Headers.AUTHORIZATION, emptyList()))
            respondJson(exchange, 200, "[]")
        }

        gateway.listGroups("my-secret-token").block()

        assertThat(capturedAuthHeaders).contains("Bearer my-secret-token")
    }
}
