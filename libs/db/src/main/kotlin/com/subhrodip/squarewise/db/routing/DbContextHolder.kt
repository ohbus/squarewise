package com.subhrodip.squarewise.db.routing

/** Thread-bound execution context used only while a synchronous JDBC transaction runs. */
object DbContextHolder {
    private val context = ThreadLocal<DbExecutionContext?>()

    /** Returns the current context or the safe writer-default context. */
    fun current(): DbExecutionContext = context.get()
        ?: DbExecutionContext("unclassified", DbOperationKind.COMMAND)

    /** Returns the currently active execution context on this thread, or null if none established. */
    fun getActiveContext(): DbExecutionContext? = context.get()

    /** Executes [block] with [executionContext] and always restores the previous context. */
    fun <T> withContext(executionContext: DbExecutionContext, block: () -> T): T {
        val previous = context.get()
        context.set(executionContext.copy(requiredWatermark = executionContext.requiredWatermark ?: DbCausalContext.requiredWatermark()))
        return try {
            block()
        } finally {
            if (previous == null) context.remove() else context.set(previous)
        }
    }
}
