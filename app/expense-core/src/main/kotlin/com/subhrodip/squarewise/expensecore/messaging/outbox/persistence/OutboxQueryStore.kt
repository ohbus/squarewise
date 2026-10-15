package com.subhrodip.squarewise.expensecore.messaging.outbox.persistence

import com.subhrodip.squarewise.db.routing.DbReader
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
/** Read-only outbox inspection port. */
@DbReader("outbox.query")
fun interface OutboxQueryStore { fun snapshot(): List<OutboxMessage> }
