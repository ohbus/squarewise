package com.subhrodip.squarewise.errors.request

import com.github.f4b6a3.uuid.UuidCreator
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.exceptions.PlatformDomainException
import java.util.UUID

/** Generates UUIDv7 request identifiers without depending on the IDs library. */
object RequestIdGenerator {
    /**
     * Generates one request identifier and translates source failures into the
     * governed identifier-generation error.
     *
     * @return a time-ordered UUID.
     * @throws PlatformDomainException when the UUID source fails.
     */
    fun next(): UUID = try {
        UuidCreator.getTimeOrderedEpoch()
    } catch (exception: RuntimeException) {
        throw PlatformDomainException(PlatformErrors.IDENTIFIER_GENERATION_FAILED, cause = exception)
    }
}
