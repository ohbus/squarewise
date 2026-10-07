package com.subhrodip.squarewise.errors.code

/**
 * Immutable six-digit machine identity in the `DMLCEE` format.
 *
 * The first four digits are non-zero namespace digits. The final two digits
 * are a sequence from `01` through `99`; `00` is never a valid allocation.
 * Construction performs only direct character checks, while decomposition is
 * constant-time property access and does not use reflection or regular expressions.
 *
 * @property value canonical six-digit machine representation
 * @throws IllegalArgumentException when the length, digit, or sequence invariant fails
 */
@JvmInline
value class ErrorCode(val value: String) {
    init {
        require(value.length == CODE_LENGTH) { "ErrorCode must contain exactly six digits" }
        require(value[0].isNamespaceDigit() && value[1].isNamespaceDigit() &&
            value[2].isNamespaceDigit() && value[3].isNamespaceDigit()) {
            "ErrorCode namespace digits must be between 1 and 9"
        }
        require(value[4].isAsciiDigit() && value[5].isAsciiDigit() && !(value[4] == '0' && value[5] == '0')) {
            "ErrorCode sequence must be between 01 and 99"
        }
    }

    /** Domain digit `D`. */
    val domainDigit: Int get() = value[0].digitToInt()

    /** Module digit `M`. */
    val moduleDigit: Int get() = value[1].digitToInt()

    /** Architectural layer digit `L`. */
    val layerDigit: Int get() = value[2].digitToInt()

    /** Remediation category digit `C`. */
    val categoryDigit: Int get() = value[3].digitToInt()

    /** Allocation sequence `EE` as an integer from 1 through 99. */
    val sequence: Int get() = value[4].digitToInt() * 10 + value[5].digitToInt()

    /** Human-readable display form `DM-L-C-EE`. */
    val displayCode: String get() = "${value.substring(0, 2)}-${value[2]}-${value[3]}-${value.substring(4, 6)}"

    private fun Char.isNamespaceDigit(): Boolean = this in '1'..'9'

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    private companion object {
        const val CODE_LENGTH: Int = 6
    }
}
