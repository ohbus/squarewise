package com.subhrodip.squarewise.accounts.auth.identity

import com.subhrodip.squarewise.accounts.errors.AccountsInputException

import java.net.IDN
import java.text.Normalizer
import java.util.Locale

/** Canonical email address used for authentication lookup and throttling only. */
@JvmInline
value class EmailAddress private constructor(val value: String) {
    companion object {
        private const val MAX_LENGTH: Int = 254
        private const val MAX_LOCAL_LENGTH: Int = 64

        /**
         * Parses and canonicalizes an email address at the authentication
         * boundary.
         *
         * The complete input is NFC-normalized and root-locale case-folded;
         * the domain is then converted to ASCII using IDN rules. The result is
         * bounded and contains exactly one separator. Email is a mutable
         * contact attribute and must not be used as an authorization subject.
         *
         * @param raw user-provided email address.
         * @return canonical address suitable for lookup and rate limiting.
         * @throws IllegalArgumentException for malformed or overlong input.
         */
        fun parse(raw: String): EmailAddress {
            val normalized = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
                .lowercase(Locale.ROOT)
            require(normalized.length <= MAX_LENGTH) { "Email address is too long" }
            require(normalized.count { it == '@' } == 1) { "Email address must contain one @" }

            val separator = normalized.indexOf('@')
            val local = normalized.substring(0, separator)
            val domainInput = normalized.substring(separator + 1)
            require(local.isNotEmpty() && local.length <= MAX_LOCAL_LENGTH) {
                "Email local part is invalid"
            }
            require(local.none { it.isWhitespace() || it.isISOControl() }) {
                "Email local part contains invalid whitespace"
            }
            require(domainInput.isNotEmpty() && domainInput.length <= 253) {
                "Email domain is invalid"
            }

            val domain = runCatching { IDN.toASCII(domainInput, IDN.USE_STD3_ASCII_RULES) }
                .getOrElse { throw AccountsInputException("Email domain is invalid", it) }
            require(domain.isNotEmpty() && domain.none { it == '@' || it.isWhitespace() }) {
                "Email domain is invalid"
            }
            require(domain.split('.').all { label -> label.isNotEmpty() && label.length <= 63 }) {
                "Email domain labels are invalid"
            }

            return EmailAddress("$local@$domain")
        }
    }
}
