package com.subhrodip.squarewise.errors.code

/** Architectural layer represented by the third error-code digit. */
enum class ErrorLayer(val digit: Int) {
    /** External interface boundary. */
    INTERFACE(1),

    /** Application orchestration. */
    APPLICATION(2),

    /** Domain rules. */
    DOMAIN(3),

    /** Persistence adapter. */
    PERSISTENCE(4),

    /** Messaging processing. */
    MESSAGING(5),

    /** External integration. */
    INTEGRATION(6),

    /** Authentication and authorization. */
    SECURITY(7),

    /** Startup and infrastructure. */
    INFRASTRUCTURE(8),

    /** Shared runtime fallback. */
    SHARED_RUNTIME(9),
}
