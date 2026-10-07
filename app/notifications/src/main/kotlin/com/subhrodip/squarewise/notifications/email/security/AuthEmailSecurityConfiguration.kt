@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.notifications.email.security
import java.util.Base64
import com.subhrodip.squarewise.notifications.errors.NotificationInputException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Fail-closed configuration for decrypting protected auth-email handoffs. */
@Configuration
class AuthEmailSecurityConfiguration(
    @Value("\${SQUAREWISE_SECURITY_AUTH_EMAIL_ENVELOPE_KEY}") private val encodedKey: String
) {
    /** Creates the configured AES-GCM decryptor; missing or malformed keys fail startup. */
    @Bean
    fun authEmailEnvelopeProtector(): AuthEmailEnvelopeProtector = AuthEmailEnvelopeProtector(
        runCatching { Base64.getDecoder().decode(encodedKey) }
            .getOrElse { throw NotificationInputException("Auth email envelope key must be base64", it) }
            .also { if (it.size != 32) throw NotificationInputException("Auth email envelope key must contain exactly 32 bytes") }
    )
}
