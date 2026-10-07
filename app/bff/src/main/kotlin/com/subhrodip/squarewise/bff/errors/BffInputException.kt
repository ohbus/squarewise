package com.subhrodip.squarewise.bff.errors

/** Typed configuration/input failure at the BFF boundary. */
class BffInputException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
