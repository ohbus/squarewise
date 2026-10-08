package com.subhrodip.squarewise.expensecore.sync.persistence

import com.subhrodip.squarewise.expensecore.sync.domain.SyncPage

/** Reader-side persistence port for bounded synchronization snapshots. */
interface SynchronizationQueryStore {
    fun snapshot(after: String?, limit: Int): SyncPage = snapshot("default", after, limit)
    fun snapshot(groupId: String, after: String?, limit: Int): SyncPage
}
