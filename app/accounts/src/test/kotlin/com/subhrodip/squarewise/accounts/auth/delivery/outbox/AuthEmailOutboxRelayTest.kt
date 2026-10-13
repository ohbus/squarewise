package com.subhrodip.squarewise.accounts.auth.delivery.outbox

import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailPublishOutcome
import com.subhrodip.squarewise.accounts.auth.delivery.service.AuthEmailOutboxPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AuthEmailOutboxRelayTest {
    private val publisher = mock(AuthEmailOutboxPublisher::class.java)

    @Test
    fun `poll returns EMPTY when outbox has no messages`() {
        `when`(publisher.publishOne()).thenReturn(AuthEmailPublishOutcome.EMPTY)
        val relay = AuthEmailOutboxRelay(publisher)

        val outcome = relay.poll()

        assertEquals(AuthEmailPublishOutcome.EMPTY, outcome)
        verify(publisher, times(1)).publishOne()
    }

    @Test
    fun `poll drains multiple messages up to maxBatchSize`() {
        `when`(publisher.publishOne())
            .thenReturn(AuthEmailPublishOutcome.PUBLISHED)
            .thenReturn(AuthEmailPublishOutcome.PUBLISHED)
            .thenReturn(AuthEmailPublishOutcome.EMPTY)

        val relay = AuthEmailOutboxRelay(publisher, maxBatchSize = 10)

        val outcome = relay.poll()

        assertEquals(AuthEmailPublishOutcome.PUBLISHED, outcome)
        verify(publisher, times(3)).publishOne()
    }

    @Test
    fun `poll halts on retryable failure`() {
        `when`(publisher.publishOne())
            .thenReturn(AuthEmailPublishOutcome.PUBLISHED)
            .thenReturn(AuthEmailPublishOutcome.RETRY_SCHEDULED)

        val relay = AuthEmailOutboxRelay(publisher, maxBatchSize = 10)

        val outcome = relay.poll()

        assertEquals(AuthEmailPublishOutcome.RETRY_SCHEDULED, outcome)
        verify(publisher, times(2)).publishOne()
    }

    @Test
    fun `rejects non-positive maxBatchSize`() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthEmailOutboxRelay(publisher, maxBatchSize = 0)
        }
    }
}
