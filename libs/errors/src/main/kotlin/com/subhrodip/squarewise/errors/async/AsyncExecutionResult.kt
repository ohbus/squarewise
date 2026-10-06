package com.subhrodip.squarewise.errors.async

/** Result union separating successful work from an acknowledgement decision. */
sealed interface AsyncExecutionResult<out T> {
    /** Successfully completed operation; the caller may acknowledge the message. */
    data class Completed<T>(val value: T) : AsyncExecutionResult<T>

    /** Non-fatal failure and its bounded consumer disposition. */
    data class Failed(
        val disposition: MessageDisposition,
        val deadLetter: DeadLetterRecord?,
    ) : AsyncExecutionResult<Nothing>
}
