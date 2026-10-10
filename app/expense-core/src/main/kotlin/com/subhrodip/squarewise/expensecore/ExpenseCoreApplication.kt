package com.subhrodip.squarewise.expensecore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import com.subhrodip.squarewise.errors.web.GlobalErrorAdvice
import com.subhrodip.squarewise.errors.request.RequestIdFilter
import com.subhrodip.squarewise.security.OidcConfigurationGuard
import com.subhrodip.squarewise.db.config.DbStatementTelemetryConfiguration

@SpringBootApplication
@Import(GlobalErrorAdvice::class, RequestIdFilter::class, OidcConfigurationGuard::class, DbStatementTelemetryConfiguration::class)
class ExpenseCoreApplication


/** Starts the Expense Core Spring Boot application for IDE and command-line launches. */
fun main(args: Array<String>) {
    runApplication<ExpenseCoreApplication>(*args)
}
