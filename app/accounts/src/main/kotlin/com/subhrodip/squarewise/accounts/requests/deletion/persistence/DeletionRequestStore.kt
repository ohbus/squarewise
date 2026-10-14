package com.subhrodip.squarewise.accounts.requests.deletion.persistence

import com.subhrodip.squarewise.accounts.requests.deletion.model.DeletionRequest
import com.subhrodip.squarewise.db.routing.DbReader
import com.subhrodip.squarewise.db.routing.DbWriter

/** Persistence port for account deletion requests. */
interface DeletionRequestStore {
    /** Submits or retrieves the current request for a subject. */
    @DbWriter("accounts.deletion.command")
    fun request(subject: String): DeletionRequest

    /** Retrieves the current request, if present. */
    @DbReader("accounts.deletion.query")
    fun get(subject: String): DeletionRequest?

    /** Cancels an existing request. */
    @DbWriter("accounts.deletion.command")
    fun cancel(subject: String): DeletionRequest?

    /** Marks an existing request as completed. */
    @DbWriter("accounts.deletion.command")
    fun complete(subject: String): DeletionRequest?
}
