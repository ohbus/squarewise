package com.subhrodip.squarewise.expensecore.messaging.outbox.persistence

import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
/** Compatibility facade combining outbox command and query operations. */
interface OutboxStore : OutboxCommandStore, OutboxQueryStore
