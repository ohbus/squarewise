package com.subhrodip.squarewise.errors.request

import org.slf4j.MDC

/**
 * Thread-local and SLF4J MDC context manager for propagating HTTP request correlation IDs.
 *
 * Invariants:
 * - When [with] executes an action, the correlation ID is bound to both a [ThreadLocal]
 *   and [org.slf4j.MDC] using key [MDC_KEY].
 * - MDC and ThreadLocal bindings are guaranteed to be cleaned up in a `finally` block,
 *   preventing thread-pool contamination across servlet container worker threads.
 */
object RequestIdContext {
    const val MDC_KEY: String = "requestId"
    private val current = ThreadLocal<String?>()

    /**
     * Retrieves the current request correlation ID, checking ThreadLocal, then MDC,
     * or defaulting to `"missing-request-id"`.
     *
     * @return non-blank correlation ID string
     */
    fun get(): String = current.get() ?: MDC.get(MDC_KEY) ?: "missing-request-id"

    /** Return the active ID or create one when a security filter runs first. */
    fun getOrGenerate(): String = current.get() ?: MDC.get(MDC_KEY) ?: RequestIdGenerator.next().toString()

    /**
     * Executes the given [action] within the context of the provided correlation [value].
     *
     * @param value the correlation ID to associate with the current thread and MDC
     * @param action the executable block to run
     * @return the result of [action]
     */
    fun <T> with(value: String, action: () -> T): T {
        current.set(value)
        MDC.put(MDC_KEY, value)
        return try {
            action()
        } finally {
            MDC.remove(MDC_KEY)
            current.remove()
        }
    }
}
