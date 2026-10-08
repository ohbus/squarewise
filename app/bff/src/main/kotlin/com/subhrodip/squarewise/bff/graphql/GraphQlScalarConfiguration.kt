package com.subhrodip.squarewise.bff.graphql

import graphql.language.IntValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.GraphQLScalarType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

@Configuration
class GraphQlScalarConfiguration {

    @Bean
    fun runtimeWiringConfigurer(): RuntimeWiringConfigurer {
        val moneyMinorScalar = buildMoneyMinorScalar()
        val dateTimeScalar = buildDateTimeScalar()

        return RuntimeWiringConfigurer { wiringBuilder ->
            wiringBuilder.scalar(moneyMinorScalar)
            wiringBuilder.scalar(dateTimeScalar)
        }
    }

    private fun buildMoneyMinorScalar(): GraphQLScalarType =
        GraphQLScalarType.newScalar()
            .name("MoneyMinor")
            .description("String-backed integer minor unit representation")
            .coercing(object : Coercing<String, String> {
                override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): String {
                    return dataFetcherResult.toString()
                }

                override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): String {
                    val s = input.toString()
                    if (!s.matches(Regex("^-?[0-9]+$"))) {
                        throw CoercingParseValueException("Invalid MoneyMinor value: $s")
                    }
                    return s
                }

                override fun parseLiteral(
                    input: Value<*>,
                    variables: CoercedVariables,
                    graphQLContext: GraphQLContext,
                    locale: Locale
                ): String {
                    if (input is StringValue) {
                        val value = input.value ?: ""
                        if (!value.matches(Regex("^-?[0-9]+$"))) {
                            throw CoercingParseLiteralException("Invalid MoneyMinor literal: $value")
                        }
                        return value
                    }
                    if (input is IntValue) {
                        return input.value.toString()
                    }
                    throw CoercingParseLiteralException("Expected string or int literal for MoneyMinor")
                }
            })
            .build()

    private fun buildDateTimeScalar(): GraphQLScalarType =
        GraphQLScalarType.newScalar()
            .name("DateTime")
            .description("ISO-8601 DateTime scalar")
            .coercing(object : Coercing<String, String> {
                override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): String {
                    return dataFetcherResult.toString()
                }

                override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): String {
                    val s = input.toString()
                    try {
                        Instant.parse(s)
                        return s
                    } catch (e: DateTimeParseException) {
                        throw CoercingParseValueException("Invalid DateTime value: $s", e)
                    }
                }

                override fun parseLiteral(
                    input: Value<*>,
                    variables: CoercedVariables,
                    graphQLContext: GraphQLContext,
                    locale: Locale
                ): String {
                    if (input is StringValue) {
                        val value = input.value ?: ""
                        try {
                            Instant.parse(value)
                            return value
                        } catch (e: DateTimeParseException) {
                            throw CoercingParseLiteralException("Invalid DateTime literal: $value", e)
                        }
                    }
                    throw CoercingParseLiteralException("Expected string literal for DateTime")
                }
            })
            .build()
}
