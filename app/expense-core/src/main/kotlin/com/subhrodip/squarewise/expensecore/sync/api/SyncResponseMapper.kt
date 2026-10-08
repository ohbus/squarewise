package com.subhrodip.squarewise.expensecore.sync.api

import com.subhrodip.squarewise.expensecore.sync.domain.SyncPage

/** Maps synchronization domain pages to the REST response model. */
fun SyncPage.toResponse(): SyncPageResponse = SyncPageResponse(
    changes = changes.map { SyncChangeResponse(it.revision, it.entityId, it.deleted, it.payload) },
    nextCursor = nextCursor,
    hasMore = hasMore
)
