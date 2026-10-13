package com.subhrodip.squarewise.accounts.auth.credential

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HMAC-SHA-256 adapter for storing one-time credential digests. */
class HmacCredentialDigest(secret: ByteArray) : CredentialDigest {
    private val key = secret.copyOf().also {
        require(it.size >= MINIMUM_SECRET_BYTES) { "Credential digest secret is too short" }
    }

    override fun digest(credential: String): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        return mac.doFinal(credential.toByteArray(StandardCharsets.UTF_8))
    }

    private companion object {
        const val ALGORITHM: String = "HmacSHA256"
        const val MINIMUM_SECRET_BYTES: Int = 32
    }
}
