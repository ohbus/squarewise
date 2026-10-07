package com.subhrodip.squarewise.db.config

import com.subhrodip.squarewise.db.errors.DbPlatformException
import com.subhrodip.squarewise.errors.catalog.PlatformErrors

/** Bounded JDBC pool settings for one database target. */
data class PoolProperties(
    var url: String = "",
    var username: String = "",
    var password: String = "",
    var maximumPoolSize: Int = 10,
    var connectionTimeoutMs: Long = 2_000,
    var maxLifetimeMs: Long = 1_800_000
) {
    /** Validates safety-critical pool bounds and required endpoint data. */
    fun validate(name: String) {
        if (url.isBlank()) invalid("squarewise.db.$name.url is required")
        if (username.isBlank()) invalid("squarewise.db.$name.username is required")
        if (maximumPoolSize !in 1..200) invalid("squarewise.db.$name.maximum-pool-size must be between 1 and 200")
        if (connectionTimeoutMs !in 250..120_000) invalid("squarewise.db.$name.connection-timeout-ms is invalid")
        if (maxLifetimeMs < 30_000) invalid("squarewise.db.$name.max-lifetime-ms is invalid")
    }

    private fun invalid(detail: String): Nothing = throw DbPlatformException(
        PlatformErrors.PLATFORM_CONFIGURATION_INVALID,
        detail
    )
}
