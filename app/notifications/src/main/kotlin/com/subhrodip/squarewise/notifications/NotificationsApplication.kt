package com.subhrodip.squarewise.notifications

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import com.subhrodip.squarewise.errors.web.GlobalErrorAdvice
import com.subhrodip.squarewise.errors.request.RequestIdFilter
import com.subhrodip.squarewise.security.OidcConfigurationGuard
import com.subhrodip.squarewise.db.config.DbStatementTelemetryConfiguration

@SpringBootApplication
@Import(GlobalErrorAdvice::class, RequestIdFilter::class, OidcConfigurationGuard::class, DbStatementTelemetryConfiguration::class)
class NotificationsApplication

/** Starts the Notifications Spring Boot application for IDE and command-line launches. */
fun main(args: Array<String>) {
    runApplication<NotificationsApplication>(*args)
}
