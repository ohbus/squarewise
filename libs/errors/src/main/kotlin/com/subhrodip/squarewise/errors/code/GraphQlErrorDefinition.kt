package com.subhrodip.squarewise.errors.code

/** Error definition that is valid for a GraphQL response and has a classification. */
interface GraphQlErrorDefinition : ErrorDefinition {
    /** Stable GraphQL error classification. */
    override val graphqlClassification: String
}
