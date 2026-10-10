package com.subhrodip.squarewise.accounts.auth.credential

import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.security.errors.PlatformDomainException

/** HMAC-SHA-256 adapter for storing one-time credential digests. */
class HmacCredentialDigest(secret: ByteArray) : CredentialDigest {
    private val key = secret.copyOf().also {
        require(it.size >= MINIMUM_SECRET_BYTES) { "Credential digest secret is too short" }
    }

    override fun digest(credential: String): ByteArray {
        try {
            val mac = Mac.getInstance(ALGORITHM)
            mac.init(SecretKeySpec(key, ALGORITHM))
            return mac.doFinal(credential.toByteArray(StandardCharsets.UTF_8))
        } catch (exception: GeneralSecurityException) {
            throw PlatformDomainException(PlatformErrors.PLATFORM_CONFIGURATION_INVALID, cause = exception)
        }
    }

    private companion object {
        const val ALGORITHM: String = "HmacSHA256"
        const val MINIMUM_SECRET_BYTES: Int = 32
    }
}
