package com.subhrodip.squarewise.errors.code

/** Error definition that is valid for a REST response and has an HTTP status. */
interface RestErrorDefinition : ErrorDefinition {
    /** Required status code for the REST representation. */
    override val httpStatus: Int
}
