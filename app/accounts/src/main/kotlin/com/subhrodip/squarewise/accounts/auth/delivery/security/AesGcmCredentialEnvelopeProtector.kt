package com.subhrodip.squarewise.accounts.auth.delivery.security

import com.subhrodip.squarewise.accounts.errors.AccountsInputException

import com.subhrodip.squarewise.accounts.auth.delivery.model.CredentialDeliveryContext
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-GCM implementation for short-lived authentication-email handoffs. */
class AesGcmCredentialEnvelopeProtector(
    key: ByteArray,
    private val random: SecureRandom = SecureRandom()
) : CredentialEnvelopeProtector {
    private val keySpec = SecretKeySpec(key.copyOf().also { require(it.size == KEY_BYTES) }, ALGORITHM)

    override fun protect(plaintext: String, context: CredentialDeliveryContext): String {
        require(plaintext.isNotEmpty()) { "credential must not be empty" }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(context.aad())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            ByteBuffer.allocate(1 + nonce.size + ciphertext.size)
                .put(FORMAT_VERSION)
                .put(nonce)
                .put(ciphertext)
                .array()
        )
    }

    override fun reveal(envelope: String, context: CredentialDeliveryContext): String {
        val encoded = runCatching { Base64.getUrlDecoder().decode(envelope) }
            .getOrElse { throw AccountsInputException("invalid credential envelope", it) }
        require(encoded.size > 1 + NONCE_BYTES + TAG_BYTES) { "invalid credential envelope" }
        require(encoded[0] == FORMAT_VERSION) { "unsupported credential envelope version" }
        val nonce = encoded.copyOfRange(1, 1 + NONCE_BYTES)
        val ciphertext = encoded.copyOfRange(1 + NONCE_BYTES, encoded.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(context.aad())
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (error: AEADBadTagException) {
            throw AccountsInputException("credential envelope authentication failed", error)
        } catch (error: GeneralSecurityException) {
            throw AccountsInputException("invalid credential envelope", error)
        }
    }

    private fun CredentialDeliveryContext.aad(): ByteArray =
        "v1|${recipient}|${template.name}".toByteArray(StandardCharsets.UTF_8)

    private companion object {
        const val ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
    }
}
