package com.subhrodip.squarewise.ids.generation

import com.subhrodip.squarewise.errors.request.RequestIdGenerator
import java.util.UUID

/** Creates time-ordered RFC 9562 UUIDv7 identifiers for new Squarewise records. */
object UuidGenerator {
    /** Returns a governed UUIDv7 from the shared request-ID generation port. */
    fun next(): UUID = RequestIdGenerator.next()
}
