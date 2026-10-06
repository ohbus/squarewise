package com.subhrodip.squarewise.errors.diagnostics

/** Singleton empty diagnostics implementation. */
object EmptyDiagnostics : ErrorDiagnostics {
    override val entries: Map<String, String> = emptyMap()
}
