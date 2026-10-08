package com.subhrodip.squarewise.expensecore.messaging.outbox.persistence
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
/** Read-only outbox inspection port. */
fun interface OutboxQueryStore { fun snapshot(): List<OutboxMessage> }
