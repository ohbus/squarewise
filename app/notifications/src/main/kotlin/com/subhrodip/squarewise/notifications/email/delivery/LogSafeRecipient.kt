package com.subhrodip.squarewise.notifications.email.delivery

import java.security.MessageDigest

/** Converts recipient identity into a stable non-reversible log correlation value. */
fun opaqueRecipientId(recipient: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(recipient.trim().lowercase().toByteArray())
    return digest.take(8).joinToString("") { "%02x".format(it) }
}
