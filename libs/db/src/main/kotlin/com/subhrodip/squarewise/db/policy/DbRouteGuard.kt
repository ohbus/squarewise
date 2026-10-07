package com.subhrodip.squarewise.db.policy

import com.subhrodip.squarewise.db.errors.DbPlatformException
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.DbRoute
import com.subhrodip.squarewise.errors.catalog.PlatformErrors

/** Prevents unsafe operations from being routed to a read-only replica. */
class DbRouteGuard {
    /**
     * Validates a requested route against the operation context.
     *
     * @throws DbPlatformException when a reader would violate consistency or mutation safety.
     */
    fun validate(context: DbExecutionContext, requestedRoute: DbRoute) {
        if (requestedRoute == DbRoute.READER && context.isWriterOnly()) {
            throw DbPlatformException(
                PlatformErrors.PLATFORM_CONFIGURATION_INVALID,
                "Database operation '${context.operationName}' must execute on the writer"
            )
        }
    }
}
