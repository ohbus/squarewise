package com.subhrodip.squarewise.notifications.email.security
import java.nio.charset.StandardCharsets
import com.subhrodip.squarewise.notifications.errors.NotificationInputException
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Decrypts Accounts-created authentication-email envelopes for delivery only. */
class AuthEmailEnvelopeProtector(key: ByteArray) {
    private val keySpec = SecretKeySpec(key.copyOf().also { require(it.size == 32) }, "AES")

    /** Reveals a credential only for the exact recipient/template context. */
    fun reveal(envelope: String, recipient: String, template: String): String {
        val bytes = runCatching { Base64.getUrlDecoder().decode(envelope) }
            .getOrElse { throw NotificationInputException("invalid auth email envelope", it) }
        if (bytes.size <= 1 + NONCE_BYTES + TAG_BYTES || bytes[0] != VERSION) {
            throw NotificationInputException("invalid auth email envelope")
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cipher.updateAAD("v1|$recipient|$template".toByteArray(StandardCharsets.UTF_8))
            String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), StandardCharsets.UTF_8)
        } catch (error: AEADBadTagException) {
            throw NotificationInputException("auth email envelope authentication failed", error)
        }
    }

    private companion object {
        const val VERSION: Byte = 1
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
    }
}
