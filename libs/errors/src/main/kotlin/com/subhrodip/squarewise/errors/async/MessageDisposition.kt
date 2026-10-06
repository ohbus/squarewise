package com.subhrodip.squarewise.errors.async

/** Consumer action after an asynchronous operation returns a governed failure. */
enum class MessageDisposition {
    ACKNOWLEDGED,
    NACK_REQUEUE,
    DEAD_LETTERED,
}
