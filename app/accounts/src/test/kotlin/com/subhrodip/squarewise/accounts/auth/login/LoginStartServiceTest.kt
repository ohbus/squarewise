package com.subhrodip.squarewise.accounts.auth.login

import com.subhrodip.squarewise.errors.code.CategoryCode
import org.mockito.ArgumentMatchers.any

import com.subhrodip.squarewise.accounts.auth.abuse.LoginRateLimitService
import com.subhrodip.squarewise.accounts.auth.abuse.RateLimitStoreUnavailableException
import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialService
import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailMessage
import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailTemplate
import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailDeliveryResult
import com.subhrodip.squarewise.accounts.auth.delivery.service.AuthEmailSender
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/** Verifies generic login-start admission, delivery, and fail-closed outcomes. */
class LoginStartServiceTest {
    private val rateLimitService = mock(LoginRateLimitService::class.java)
    private val credentialService = mock(LoginCredentialService::class.java)
    private val emailSender = mock(AuthEmailSender::class.java)
    private val service = LoginStartService(rateLimitService, credentialService, emailSender)
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `admitted code request sends the matching delivery template`() {
        val credential = deliveryCredential(LoginCredentialService.CredentialKind.CODE)
        `when`(rateLimitService.tryAcquire("raw@example.com", "trusted-network", now)).thenReturn(true)
        `when`(
            credentialService.issue(
                "raw@example.com",
                LoginCredentialService.CredentialKind.CODE,
                now,
            )
        ).thenReturn(credential)
        assertEquals(
            LoginStartResult.ACCEPTED,
            service.start("raw@example.com", "trusted-network", LoginCredentialService.CredentialKind.CODE, now),
        )

        val message = ArgumentCaptor.forClass(AuthEmailMessage::class.java)
        verify(emailSender).send(
            message.capture() ?: AuthEmailMessage("ignored@example.com", AuthEmailTemplate.LOGIN_LINK, "", now)
        )
        assertEquals("canonical@example.com", message.value.recipient)
        assertEquals(AuthEmailTemplate.LOGIN_CODE, message.value.template)
        assertEquals("plaintext", message.value.credential)
        assertEquals(credential.expiresAt, message.value.expiresAt)
    }

    @Test
    fun `admitted link request maps to the link template`() {
        val credential = deliveryCredential(LoginCredentialService.CredentialKind.LINK)
        `when`(rateLimitService.tryAcquire("user@example.com", "network", now)).thenReturn(true)
        `when`(
            credentialService.issue(
                "user@example.com",
                LoginCredentialService.CredentialKind.LINK,
                now,
            )
        ).thenReturn(credential)

        assertEquals(
            LoginStartResult.ACCEPTED,
            service.start("user@example.com", "network", LoginCredentialService.CredentialKind.LINK, now),
        )

        val message = ArgumentCaptor.forClass(AuthEmailMessage::class.java)
        verify(emailSender).send(
            message.capture() ?: AuthEmailMessage("ignored@example.com", AuthEmailTemplate.LOGIN_LINK, "", now)
        )
        assertEquals(AuthEmailTemplate.LOGIN_LINK, message.value.template)
    }

    @Test
    fun `credential issuance failure remains generic and does not send`() {
        `when`(rateLimitService.tryAcquire("user@example.com", "network", now)).thenReturn(true)
        `when`(
            credentialService.issue(
                "user@example.com",
                LoginCredentialService.CredentialKind.LINK,
                now,
            )
        ).thenThrow(IllegalArgumentException("invalid input"))

        assertEquals(
            LoginStartResult.ACCEPTED,
            service.start("user@example.com", "network", LoginCredentialService.CredentialKind.LINK, now),
        )
        verify(emailSender, never()).send(
            any(AuthEmailMessage::class.java)
                ?: AuthEmailMessage("ignored@example.com", AuthEmailTemplate.LOGIN_LINK, "", now)
        )
    }

    @Test
    fun `delivery failure remains generic after credential issuance`() {
        val credential = deliveryCredential(LoginCredentialService.CredentialKind.LINK)
        `when`(rateLimitService.tryAcquire("person@example.test", "network", now)).thenReturn(true)
        `when`(
            credentialService.issue(
                "person@example.test",
                LoginCredentialService.CredentialKind.LINK,
                now
            )
        ).thenReturn(credential)
        doThrow(IllegalStateException("mail provider unavailable"))
            .`when`(emailSender)
            .send(
                any(AuthEmailMessage::class.java)
                    ?: AuthEmailMessage("ignored@example.com", AuthEmailTemplate.LOGIN_LINK, "", now)
            )

        val result = service.start(
            "person@example.test",
            "network",
            LoginCredentialService.CredentialKind.LINK,
            now
        )

        assertEquals(LoginStartResult.ACCEPTED, result)
        verify(emailSender).send(
            any(AuthEmailMessage::class.java)
                ?: AuthEmailMessage("ignored@example.com", AuthEmailTemplate.LOGIN_LINK, "", now)
        )
    }

    @Test
    fun `rate-limit denial fails with the stable error code`() {
        `when`(rateLimitService.tryAcquire("user@example.com", "network", now)).thenReturn(false)

        val exception = assertThrows(SquarewiseException::class.java) {
            service.start("user@example.com", "network", LoginCredentialService.CredentialKind.LINK, now)
        }

        assertEquals(CategoryCode.RATE_LIMIT_EXCEEDED, exception.definition.category)
        verify(credentialService, never()).issue(
            "user@example.com",
            LoginCredentialService.CredentialKind.LINK,
            now,
        )
    }

    @Test
    fun `rate-limit store failure fails closed with the same error code`() {
        `when`(rateLimitService.tryAcquire("user@example.com", "network", now)).thenThrow(
            RateLimitStoreUnavailableException(IllegalStateException("redis unavailable"))
        )

        val exception = assertThrows(SquarewiseException::class.java) {
            service.start("user@example.com", "network", LoginCredentialService.CredentialKind.LINK, now)
        }

        assertEquals(CategoryCode.RATE_LIMIT_EXCEEDED, exception.definition.category)
        verify(credentialService, never()).issue(
            "user@example.com",
            LoginCredentialService.CredentialKind.LINK,
            now,
        )
    }

    private fun deliveryCredential(kind: LoginCredentialService.CredentialKind): LoginCredentialService.DeliveryCredential =
        LoginCredentialService.DeliveryCredential(
            credentialId = UUID.randomUUID(),
            canonicalEmail = "canonical@example.com",
            plaintext = "plaintext",
            expiresAt = now.plusSeconds(600),
            kind = kind,
        )
}
