package com.subhrodip.squarewise.bff.transport

import java.net.URI
import org.springframework.http.HttpMethod

import com.subhrodip.squarewise.db.routing.DbWatermark
import com.subhrodip.squarewise.db.routing.DbWatermarkHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import reactor.util.context.Context

/** Verifies BFF bearer and causal-watermark propagation across the gateway filter. */
class BffGatewayFiltersTest {
    @Test
    fun `forwards nonblank context values and retains the greatest downstream watermark`() {
        var forwarded: ClientRequest? = null
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/graphql").build())
        val downstream = DbWatermark.fromPosition(20).asLsn()
        val next = ExchangeFunction { request ->
            forwarded = request
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(DbWatermarkHeaders.WRITER_WATERMARK, downstream)
                    .build()
            )
        }

        BffGatewayFilters.bearerPropagation.filter(
            ClientRequest.create(HttpMethod.GET, URI.create("http://expense-core/groups")).build(),
            next
        ).contextWrite(
            Context.of(
                BearerTokenContext.KEY, "opaque-access-token",
                BearerTokenContext.WATERMARK_KEY, DbWatermark.fromPosition(10).asLsn(),
                BearerTokenContext.EXCHANGE_KEY, exchange
            )
        ).block()

        assertThat(forwarded?.headers()?.getFirst("Authorization")).isEqualTo("Bearer opaque-access-token")
        assertThat(forwarded?.headers()?.getFirst(DbWatermarkHeaders.REQUIRED_WATERMARK))
            .isEqualTo(DbWatermark.fromPosition(10).asLsn())
        assertThat(exchange.response.headers.getFirst(DbWatermarkHeaders.WRITER_WATERMARK)).isEqualTo(downstream)
    }

    @Test
    fun `does not add blank context values or overwrite a greater existing watermark`() {
        var forwarded: ClientRequest? = null
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        exchange.response.headers.set(DbWatermarkHeaders.WRITER_WATERMARK, DbWatermark.fromPosition(30).asLsn())
        val next = ExchangeFunction { request ->
            forwarded = request
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(DbWatermarkHeaders.WRITER_WATERMARK, DbWatermark.fromPosition(20).asLsn())
                    .build()
            )
        }

        BffGatewayFilters.bearerPropagation.filter(
            ClientRequest.create(HttpMethod.GET, URI.create("http://expense-core/groups")).build(),
            next
        ).contextWrite(
            Context.of(
                BearerTokenContext.KEY, "",
                BearerTokenContext.WATERMARK_KEY, "",
                BearerTokenContext.EXCHANGE_KEY, exchange
            )
        ).block()

        assertThat(forwarded?.headers()?.getFirst("Authorization")).isNull()
        assertThat(forwarded?.headers()?.getFirst(DbWatermarkHeaders.REQUIRED_WATERMARK)).isNull()
        assertThat(exchange.response.headers.getFirst(DbWatermarkHeaders.WRITER_WATERMARK))
            .isEqualTo(DbWatermark.fromPosition(30).asLsn())
    }

    /** Verifies a valid downstream watermark advances a lower response watermark. */
    @Test
    fun `advances a lower existing downstream watermark`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        exchange.response.headers.set(DbWatermarkHeaders.WRITER_WATERMARK, DbWatermark.fromPosition(10).asLsn())
        val downstream = DbWatermark.fromPosition(20).asLsn()
        val next = ExchangeFunction {
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(DbWatermarkHeaders.WRITER_WATERMARK, downstream)
                    .build()
            )
        }

        BffGatewayFilters.bearerPropagation.filter(
            ClientRequest.create(HttpMethod.GET, URI.create("http://expense-core/groups")).build(),
            next
        ).contextWrite(Context.of(BearerTokenContext.EXCHANGE_KEY, exchange)).block()

        assertThat(exchange.response.headers.getFirst(DbWatermarkHeaders.WRITER_WATERMARK))
            .withFailMessage("expected %s but was %s", downstream, exchange.response.headers.getFirst(DbWatermarkHeaders.WRITER_WATERMARK))
            .isEqualTo(downstream)
    }

    @Test
    fun `ignores malformed downstream watermark without mutating response`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        val next = ExchangeFunction {
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(DbWatermarkHeaders.WRITER_WATERMARK, "not-an-lsn")
                    .build()
            )
        }

        BffGatewayFilters.bearerPropagation.filter(
            ClientRequest.create(HttpMethod.GET, URI.create("http://expense-core/groups")).build(),
            next
        ).contextWrite(Context.of(BearerTokenContext.EXCHANGE_KEY, exchange)).block()

        assertThat(exchange.response.headers.getFirst(DbWatermarkHeaders.WRITER_WATERMARK)).isNull()
    }

    /** Verifies an upstream response without a watermark leaves the response header unchanged. */
    @Test
    fun `ignores absent downstream watermark without mutating response`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        exchange.response.headers.set(DbWatermarkHeaders.WRITER_WATERMARK, DbWatermark.fromPosition(12).asLsn())
        val next = ExchangeFunction {
            Mono.just(ClientResponse.create(HttpStatus.OK).build())
        }

        BffGatewayFilters.bearerPropagation.filter(
            ClientRequest.create(HttpMethod.GET, URI.create("http://expense-core/groups")).build(),
            next
        ).contextWrite(Context.of(BearerTokenContext.EXCHANGE_KEY, exchange)).block()

        assertThat(exchange.response.headers.getFirst(DbWatermarkHeaders.WRITER_WATERMARK))
            .isEqualTo(DbWatermark.fromPosition(12).asLsn())
    }

    @Test
    fun `handles missing exchange context without propagating credentials or failing`() {
        var forwarded: ClientRequest? = null
        val next = ExchangeFunction { request ->
            forwarded = request
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(DbWatermarkHeaders.WRITER_WATERMARK, DbWatermark.fromPosition(7).asLsn())
                    .build()
            )
        }

        BffGatewayFilters.bearerPropagation.filter(
            ClientRequest.create(HttpMethod.GET, URI.create("http://expense-core/groups")).build(),
            next
        ).contextWrite(Context.empty()).block()

        assertThat(forwarded?.headers()?.getFirst("Authorization")).isNull()
        assertThat(forwarded?.headers()?.getFirst(DbWatermarkHeaders.REQUIRED_WATERMARK)).isNull()
    }
}
