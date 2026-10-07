package com.subhrodip.squarewise.db.routing

import com.subhrodip.squarewise.db.errors.DbPlatformException
import com.subhrodip.squarewise.errors.catalog.PlatformErrors

/** Opaque causal position returned by PostgreSQL, represented as a monotonic LSN value. */
@JvmInline
value class DbWatermark private constructor(val position: Long) : Comparable<DbWatermark> {
    init {
        if (position < 0) {
            throw DbPlatformException(PlatformErrors.PLATFORM_CONFIGURATION_INVALID, "watermark position must not be negative")
        }
    }

    override fun compareTo(other: DbWatermark): Int = position.compareTo(other.position)

    /** Returns the PostgreSQL `X/XXXXXXX` textual LSN form. */
    fun asLsn(): String = "${(position ushr 32).toString(16)}/${(position and 0xffffffffL).toString(16)}".uppercase()

    companion object {
        /** Parses a PostgreSQL LSN without using wall-clock time. */
        fun parse(value: String): DbWatermark {
            val parts = value.trim().split('/')
            if (parts.size != 2) {
                throw DbPlatformException(PlatformErrors.PLATFORM_CONFIGURATION_INVALID, "watermark must be a PostgreSQL LSN")
            }
            return try {
                val high = parts[0].toULong(16)
                val low = parts[1].toULong(16)
                if (high > UInt.MAX_VALUE.toULong() || low > UInt.MAX_VALUE.toULong()) {
                    throw DbPlatformException(PlatformErrors.PLATFORM_CONFIGURATION_INVALID, "watermark LSN component is out of range")
                }
                DbWatermark((high.toLong() shl 32) or low.toLong())
            } catch (exception: NumberFormatException) {
                throw DbPlatformException(PlatformErrors.PLATFORM_CONFIGURATION_INVALID, "watermark must be a PostgreSQL LSN", exception)
            }
        }

        fun fromPosition(position: Long): DbWatermark = DbWatermark(position)
    }
}

/** Header names used when a writer watermark crosses a service boundary. */
object DbWatermarkHeaders {
    const val WRITER_WATERMARK = "X-Squarewise-Writer-Watermark"
    const val REQUIRED_WATERMARK = "X-Squarewise-Required-Watermark"
}
