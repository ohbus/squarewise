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

    /**
     * Executes [block] routed to a read replica, optionally targeting a named reader pool.
     *
     * @param target optional explicit reader instance name.
     * @param operation telemetry operation name.
     * @param consistency consistency guarantee.
     * @param block computation to execute within reader context.
     * @return result of [block].
     */
    fun <T> withReader(
        target: String = "",
        operation: String = "db.query",
        consistency: ReadConsistency = ReadConsistency.EVENTUAL,
        block: () -> T
    ): T {
        val executionContext = DbExecutionContext(
            operationName = operation,
            kind = DbOperationKind.QUERY,
            consistency = consistency,
            readerEligible = true,
            targetReader = target.takeIf { it.isNotBlank() }
        )
        return withContext(executionContext, block)
    }

    /**
     * Executes [block] routed to the primary writer or an optionally named target writer pool.
     *
     * @param target optional explicit writer instance name.
     * @param operation telemetry operation name.
     * @param block computation to execute within writer context.
     * @return result of [block].
     */
    fun <T> withWriter(
        target: String = "",
        operation: String = "db.command",
        block: () -> T
    ): T {
        val executionContext = DbExecutionContext(
            operationName = operation,
            kind = DbOperationKind.COMMAND,
            consistency = ReadConsistency.STRONG,
            readerEligible = false,
            targetWriter = target.takeIf { it.isNotBlank() }
        )
        return withContext(executionContext, block)
    }
}
