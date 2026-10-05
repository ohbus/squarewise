package com.subhrodip.squarewise.accounts

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import com.subhrodip.squarewise.errors.http.GlobalErrorHandler
import com.subhrodip.squarewise.errors.request.RequestIdFilter
import com.subhrodip.squarewise.security.OidcConfigurationGuard
import com.subhrodip.squarewise.db.config.DbStatementTelemetryConfiguration

@SpringBootApplication
@Import(GlobalErrorHandler::class, RequestIdFilter::class, OidcConfigurationGuard::class, DbStatementTelemetryConfiguration::class)
class AccountsApplication

/** Starts the Accounts Spring Boot application for IDE and command-line launches. */
fun main(args: Array<String>) {
    runApplication<AccountsApplication>(*args)
}
