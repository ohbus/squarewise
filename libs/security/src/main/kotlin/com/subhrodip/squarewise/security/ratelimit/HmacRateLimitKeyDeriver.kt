package com.subhrodip.squarewise.security.ratelimit

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.security.errors.PlatformDomainException

/** HMAC-SHA-256 key derivation for distributed rate-limit identifiers. */
class HmacRateLimitKeyDeriver private constructor(private val secret: ByteArray) : RateLimitKeyDeriver {
    init {
        if (secret.size < MINIMUM_SECRET_BYTES) {
            throw PlatformDomainException(PlatformErrors.PLATFORM_CONFIGURATION_INVALID, "Rate-limit key secret must contain at least 32 bytes")
        }
    }

    override fun derive(key: String): String = Mac.getInstance(ALGORITHM).run {
        init(SecretKeySpec(secret, algorithm))
        doFinal(key.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private const val MINIMUM_SECRET_BYTES = 32

        /** Creates a deriver from a required base64 deployment secret. */
        fun fromBase64(encodedSecret: String): HmacRateLimitKeyDeriver = runCatching {
            Base64.getDecoder().decode(encodedSecret)
        }.getOrElse { throw PlatformDomainException(PlatformErrors.PLATFORM_CONFIGURATION_INVALID, "Rate-limit key secret must be base64", it) }
            .let(::HmacRateLimitKeyDeriver)
    }
}
