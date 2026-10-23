package com.subhrodip.squarewise.bff.graphql

import com.subhrodip.squarewise.bff.transport.ExpenseCoreGateway
import com.subhrodip.squarewise.bff.transport.AccountsGateway
import com.subhrodip.squarewise.bff.messaging.model.BffEventEnvelope
import com.subhrodip.squarewise.bff.messaging.model.ConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.DuplicateConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.ProcessedConsumptionResult
import com.subhrodip.squarewise.bff.messaging.service.BffEventConsumer
import com.subhrodip.squarewise.bff.messaging.persistence.BffEventDeduplicator
import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.BooleanValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.RuntimeWiring
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.util.Locale

class GraphQlScalarConfigurationTest {

    @Test
    fun `MoneyMinor accepts signed integer values and literals`() {
        val scalar = scalar("MoneyMinor")
        val coercing = scalar.coercing

        assertEquals("-12", coercing.parseValue("-12", GraphQLContext.getDefault(), Locale.ROOT))
        assertEquals("42", coercing.parseLiteral(IntValue.of(42), CoercedVariables.emptyVariables(), GraphQLContext.getDefault(), Locale.ROOT))
        assertEquals("900", coercing.parseLiteral(StringValue.of("900"), CoercedVariables.emptyVariables(), GraphQLContext.getDefault(), Locale.ROOT))
    }

    @Test
    fun `MoneyMinor rejects malformed values`() {
        val coercing = scalar("MoneyMinor").coercing

        assertThrows(CoercingParseValueException::class.java) {
            coercing.parseValue("12.5", GraphQLContext.getDefault(), Locale.ROOT)
        }
        assertThrows(CoercingParseLiteralException::class.java) {
            coercing.parseLiteral(StringValue.of(" "), CoercedVariables.emptyVariables(), GraphQLContext.getDefault(), Locale.ROOT)
        }
        assertThrows(CoercingParseLiteralException::class.java) {
            coercing.parseLiteral(BooleanValue.of(true), CoercedVariables.emptyVariables(), GraphQLContext.getDefault(), Locale.ROOT)
        }
    }

    @Test
    fun `DateTime validates ISO instant and serializes values`() {
        val coercing = scalar("DateTime").coercing
        val context = GraphQLContext.getDefault()

        assertEquals("2026-09-18T10:00:00Z", coercing.parseValue("2026-09-18T10:00:00Z", context, Locale.ROOT))
        assertEquals("2026-09-18T10:00:00Z", coercing.serialize("2026-09-18T10:00:00Z", context, Locale.ROOT))
        assertThrows(CoercingParseValueException::class.java) {
            coercing.parseValue("not-a-date", context, Locale.ROOT)
        }
        assertThrows(CoercingParseLiteralException::class.java) {
            coercing.parseLiteral(IntValue.of(20260918), CoercedVariables.emptyVariables(), context, Locale.ROOT)
        }
    }

    /** Verifies a nullable GraphQL string literal is rejected as an empty MoneyMinor value. */
    @Test
    fun `MoneyMinor rejects a null string literal value`() {
        val input = mock(StringValue::class.java)
        doReturn(null).`when`(input).value

        assertThrows(CoercingParseLiteralException::class.java) {
            scalar("MoneyMinor").coercing.parseLiteral(
                input,
                CoercedVariables.emptyVariables(),
                GraphQLContext.getDefault(),
                Locale.ROOT
            )
        }
    }

    /** Verifies DateTime string literals accept ISO instants and reject malformed values. */
    @Test
    fun `DateTime validates string literals`() {
        val coercing = scalar("DateTime").coercing
        val variables = CoercedVariables.emptyVariables()
        val context = GraphQLContext.getDefault()

        assertEquals(
            "2026-09-18T10:00:00Z",
            coercing.parseLiteral(
                StringValue.of("2026-09-18T10:00:00Z"),
                variables,
                context,
                Locale.ROOT
            )
        )
        assertThrows(CoercingParseLiteralException::class.java) {
            coercing.parseLiteral(
                StringValue.of("not-a-date"),
                variables,
                context,
                Locale.ROOT
            )
        }
    }

    /** Verifies a nullable GraphQL string literal is rejected as an invalid DateTime value. */
    @Test
    fun `DateTime rejects a null string literal value`() {
        val input = mock(StringValue::class.java)
        doReturn(null).`when`(input).value

        assertThrows(CoercingParseLiteralException::class.java) {
            scalar("DateTime").coercing.parseLiteral(
                input,
                CoercedVariables.emptyVariables(),
                GraphQLContext.getDefault(),
                Locale.ROOT
            )
        }
    }

    private fun scalar(name: String): GraphQLScalarType {
        val builder = RuntimeWiring.newRuntimeWiring()
        GraphQlScalarConfiguration().runtimeWiringConfigurer().configure(builder)
        return builder.build().scalars[name] ?: error("Scalar $name was not registered")
    }
}
