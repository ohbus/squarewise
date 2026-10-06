@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.accounts.auth.config
import com.subhrodip.squarewise.accounts.auth.abuse.LoginRateLimitKeyDeriver
import com.subhrodip.squarewise.accounts.auth.abuse.LoginRateLimitService
import com.subhrodip.squarewise.accounts.auth.abuse.LoginVerificationRateLimitService
import com.subhrodip.squarewise.accounts.auth.abuse.RefreshRateLimitService
import com.subhrodip.squarewise.accounts.auth.delivery.service.AuthEmailSender
import com.subhrodip.squarewise.accounts.auth.login.LoginStartService

import com.subhrodip.squarewise.accounts.auth.abuse.ClientAddressResolver
import com.subhrodip.squarewise.accounts.auth.abuse.TrustedProxyProperties
import com.subhrodip.squarewise.accounts.auth.credential.CredentialDigest
import com.subhrodip.squarewise.accounts.auth.credential.HmacCredentialDigest
import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialRepository
import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialService
import com.subhrodip.squarewise.accounts.auth.credential.OneTimeCredentialIssuer
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import com.subhrodip.squarewise.security.ratelimit.HmacRateLimitKeyDeriver
import com.subhrodip.squarewise.security.ratelimit.RedisRateLimiter
import com.subhrodip.squarewise.security.ratelimit.RateLimitRedisHealthIndicator
import io.micrometer.core.instrument.MeterRegistry
import com.subhrodip.squarewise.accounts.auth.delivery.security.AesGcmCredentialEnvelopeProtector
import com.subhrodip.squarewise.accounts.auth.delivery.security.CredentialEnvelopeProtector
import java.util.Base64
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate

/** Fail-closed deployment wiring for passwordless credential cryptography. */
@Configuration
@Import(RateLimitRedisHealthIndicator::class)
@EnableConfigurationProperties(TrustedProxyProperties::class)
class AuthenticationCredentialConfiguration(
    @Value("\${SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET}")
    private val encodedDigestSecret: String,
    @Value("\${SQUAREWISE_SECURITY_AUTH_EMAIL_ENVELOPE_KEY}")
    private val encodedEnvelopeKey: String,
    @Value("\${SQUAREWISE_AUTH_LOGIN_RESEND_COOLDOWN_SECONDS:60}")
    private val loginResendCooldownSeconds: Long,
    @Value("\${SQUAREWISE_AUTH_LOGIN_VERIFY_MAX_REQUESTS:5}")
    private val loginVerifyMaximumRequests: Int,
    @Value("\${SQUAREWISE_AUTH_LOGIN_VERIFY_WINDOW_SECONDS:300}")
    private val loginVerifyWindowSeconds: Long
) {
    init {
        require(loginResendCooldownSeconds in 0..900) {
            "SQUAREWISE_AUTH_LOGIN_RESEND_COOLDOWN_SECONDS must be between 0 and 900"
        }
        require(loginVerifyMaximumRequests in 1..1_000_000) {
            "SQUAREWISE_AUTH_LOGIN_VERIFY_MAX_REQUESTS must be between 1 and 1000000"
        }
        require(loginVerifyWindowSeconds in 1..86_400) {
            "SQUAREWISE_AUTH_LOGIN_VERIFY_WINDOW_SECONDS must be between 1 and 86400"
        }
    }
    /** Creates the mandatory Redis-backed distributed limiter for runtime profiles. */
    @Bean
    fun rateLimiter(redis: StringRedisTemplate, meterRegistry: MeterRegistry): RateLimiter =
        RedisRateLimiter(redis, HmacRateLimitKeyDeriver.fromBase64(encodedDigestSecret), meterRegistry)

    /** Creates the HMAC digest adapter from a deployment-only base64 secret. */
    @Bean
    fun credentialDigest(): CredentialDigest = HmacCredentialDigest(decodeSecret())

    /** Creates the secure one-time credential issuer. */
    @Bean
    fun oneTimeCredentialIssuer(digest: CredentialDigest): OneTimeCredentialIssuer =
        OneTimeCredentialIssuer(digest)

    /** Creates the transactional application service for login credentials. */
    @Bean
    fun loginCredentialService(
        repository: LoginCredentialRepository,
        issuer: OneTimeCredentialIssuer
    ): LoginCredentialService = LoginCredentialService(repository, issuer)

    /** Creates the fail-closed AES-GCM protector for auth-email handoffs. */
    @Bean
    fun credentialEnvelopeProtector(): CredentialEnvelopeProtector =
        AesGcmCredentialEnvelopeProtector(decodeEnvelopeKey())

    /** Creates the rate limit key deriver. */
    @Bean
    fun loginRateLimitKeyDeriver(digest: CredentialDigest): LoginRateLimitKeyDeriver =
        LoginRateLimitKeyDeriver(digest)

    /** Creates the transactional login rate limit service. */
    @Bean
    fun loginRateLimitService(
        keyDeriver: LoginRateLimitKeyDeriver,
        rateLimiter: RateLimiter
    ): LoginRateLimitService = LoginRateLimitService(
        keyDeriver,
        rateLimiter,
        resendCooldown = Duration.ofSeconds(loginResendCooldownSeconds)
    )

    /** Creates the fail-closed credential-verification limiter. */
    @Bean
    fun loginVerificationRateLimitService(
        digest: CredentialDigest,
        rateLimiter: RateLimiter
    ): LoginVerificationRateLimitService = LoginVerificationRateLimitService(
        digest = digest,
        rateLimiter = rateLimiter,
        window = Duration.ofSeconds(loginVerifyWindowSeconds),
        maximumRequests = loginVerifyMaximumRequests
    )

    /** Creates the fail-closed refresh-token rotation limiter. */
    @Bean
    fun refreshRateLimitService(
        digest: CredentialDigest,
        rateLimiter: RateLimiter
    ): RefreshRateLimitService =
        RefreshRateLimitService(digest, rateLimiter)

    /** Creates the login start application service. */
    @Bean
    fun loginStartService(
        rateLimitService: LoginRateLimitService,
        credentialService: LoginCredentialService,
        emailSender: AuthEmailSender
    ): LoginStartService =
        LoginStartService(rateLimitService, credentialService, emailSender)

    /**
     * Creates the [ClientAddressResolver] from deployment-configured trusted-proxy addresses.
     *
     * SEC-007: Trusted proxies are resolved from [TrustedProxyProperties]; only forwarded
     * headers from these addresses are trusted for client IP extraction.
     */
    @Bean
    fun clientAddressResolver(properties: TrustedProxyProperties): ClientAddressResolver =
        ClientAddressResolver.fromProperties(properties)

    private fun decodeSecret(): ByteArray = runCatching {
        Base64.getDecoder().decode(encodedDigestSecret)
    }.getOrElse { throw IllegalArgumentException("Credential digest secret must be base64", it) }
        .also { require(it.size >= 32) { "Credential digest secret must contain at least 32 bytes" } }

    private fun decodeEnvelopeKey(): ByteArray = runCatching {
        Base64.getDecoder().decode(encodedEnvelopeKey)
    }.getOrElse { throw IllegalArgumentException("Auth email envelope key must be base64", it) }
        .also { require(it.size == 32) { "Auth email envelope key must contain exactly 32 bytes" } }
}
