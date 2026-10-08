package com.subhrodip.squarewise.errors.exceptions

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import com.subhrodip.squarewise.errors.diagnostics.MapDiagnostics
import com.subhrodip.squarewise.errors.diagnostics.ResourceIdentifier

/** Resource lookup failure whose definition supports anti-enumeration mapping. */
open class EntityNotFoundException(
    resource: ResourceIdentifier,
    definition: ErrorDefinition = PlatformErrors.RESOURCE_NOT_FOUND,
    diagnostics: ErrorDiagnostics = MapDiagnostics.of(
        mapOf("resource.type" to resource.value)
    ),
    cause: Throwable? = null,
) : SquarewiseException(definition, diagnostics, cause)
