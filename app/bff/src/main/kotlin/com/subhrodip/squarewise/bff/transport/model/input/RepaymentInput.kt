package com.subhrodip.squarewise.bff.transport.model.input

/** GraphQL input for recording a repayment. */
data class RepaymentInput(
    val groupId: String?,
    val fromParticipantId: String,
    val toParticipantId: String,
    val amount: MoneyInput,
    val reason: String?,
    val idempotencyKey: String
)
