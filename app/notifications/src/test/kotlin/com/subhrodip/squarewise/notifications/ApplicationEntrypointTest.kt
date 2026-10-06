package com.subhrodip.squarewise.notifications

import org.junit.jupiter.api.Test

/** Verifies the Notifications command-line entrypoint can bootstrap a non-web test application. */
class ApplicationEntrypointTest {
    @Test
    fun `main bootstraps the non-web test profile`() {
        main(
            arrayOf(
                "--spring.profiles.active=test",
                "--spring.main.web-application-type=none",
                "--spring.main.register-shutdown-hook=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.datasource.url=jdbc:h2:mem:notifications-entrypoint;DB_CLOSE_DELAY=-1"
            )
        )
    }
}
