package com.subhrodip.squarewise.errors.observability

/** Minimal tracing port implemented by an OpenTelemetry bridge at the edge. */
interface ErrorTraceSpan {
    /** Attach a bounded string attribute to the active span. */
    fun setAttribute(key: String, value: String)

    /** Mark the active span as failed for an unexpected/server-side error. */
    fun markError()
}
