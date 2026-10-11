package com.subhrodip.squarewise.accounts.requests.deletion.service

import com.subhrodip.squarewise.accounts.requests.deletion.model.DeletionRequest
import com.subhrodip.squarewise.accounts.requests.deletion.persistence.DeletionRequestStore
import org.springframework.stereotype.Service

@Service
class DeletionRequestService(
    private val store: DeletionRequestStore
) {
    fun request(subject: String): DeletionRequest = store.request(subject)
    fun get(subject: String): DeletionRequest? = store.get(subject)
    fun cancel(subject: String): DeletionRequest? = store.cancel(subject)
    fun complete(subject: String): DeletionRequest? = store.complete(subject)
}
