package com.subhrodip.squarewise.expensecore.sync.persistence

import com.subhrodip.squarewise.db.routing.DbWriter

/** Writer-side persistence port for synchronization changes. */
@DbWriter("sync.command")
interface SynchronizationCommandStore {
    fun append(entityId: String, payload: String?): Long = append("default", entityId, payload)
    fun append(groupId: String, entityId: String, payload: String?): Long
    fun delete(entityId: String): Long = delete("default", entityId)
    fun delete(groupId: String, entityId: String): Long
}
