package com.subhrodip.squarewise.accounts.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialEntity;
import com.subhrodip.squarewise.accounts.auth.delivery.outbox.AuthEmailOutboxEntity;
import com.subhrodip.squarewise.accounts.auth.identity.persistence.AccountIdentityEntity;
import com.subhrodip.squarewise.accounts.auth.session.AuthSessionEntity;
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileEntity;
import com.subhrodip.squarewise.accounts.requests.deletion.model.DeletionStatus;
import com.subhrodip.squarewise.accounts.requests.deletion.persistence.AccountDeletionRequestEntity;
import com.subhrodip.squarewise.accounts.requests.export.model.ExportStatus;
import com.subhrodip.squarewise.accounts.requests.export.persistence.AccountExportRequestEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies the JavaBean surface used by JPA for Accounts persistence entities. */
class AccountsJavaBeanCompatibilityTest {
    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    void auth_entities_expose_jpa_getters_and_setters() {
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        byte[] digest = new byte[] {1, 2, 3};

        AccountIdentityEntity identity = new AccountIdentityEntity(identityId, accountId, "issuer", "subject", "a@example.test", true, "ACTIVE", NOW, NOW);
        identity.setEmail("updated@example.test");
        identity.setEmailVerified(false);
        assertEquals(identityId, identity.getIdentityId());
        assertEquals(accountId, identity.getAccountId());
        assertEquals("issuer", identity.getIssuer());
        assertEquals("subject", identity.getProviderSubject());
        assertEquals("updated@example.test", identity.getEmail());
        assertEquals(false, identity.getEmailVerified());

        LoginCredentialEntity credential = new LoginCredentialEntity(credentialId, "a@example.test", digest, "LINK", NOW, NOW, 5, null);
        credential.setRemainingAttempts(2);
        credential.setConsumedAt(NOW);
        assertEquals(credentialId, credential.getCredentialId());
        assertEquals("a@example.test", credential.getCanonicalEmail());
        assertEquals(2, credential.getRemainingAttempts());
        assertEquals(NOW, credential.getConsumedAt());

        AuthSessionEntity session = new AuthSessionEntity(sessionId, accountId, "subject", familyId, digest, NOW, NOW, NOW, NOW, null, null, "browser", "BROWSER");
        session.setRevokedAt(NOW);
        session.setReplacedBySessionId(UUID.randomUUID());
        assertEquals(sessionId, session.getSessionId());
        assertEquals(accountId, session.getAccountId());
        assertEquals(familyId, session.getFamilyId());
        assertEquals("BROWSER", session.getClientKind());
        assertEquals(NOW, session.getRevokedAt());
    }

    @Test
    void outbox_and_profile_entities_expose_jpa_getters_and_setters() {
        UUID accountId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        AuthEmailOutboxEntity outbox = new AuthEmailOutboxEntity(outboxId, eventId, "a@example.test", "LOGIN_LINK", "encrypted", NOW, NOW, NOW, 0, "PENDING");
        outbox.setAttempts(3);
        outbox.setStatus("CLAIMED");
        assertEquals(outboxId, outbox.getOutboxId());
        assertEquals(eventId, outbox.getEventId());
        assertEquals("a@example.test", outbox.getRecipient());
        assertEquals("encrypted", outbox.getEncryptedCredential());
        assertEquals(3, outbox.getAttempts());
        assertEquals("CLAIMED", outbox.getStatus());

        ProfileEntity profile = new ProfileEntity(accountId, "subject", "Alice", "UTC", "EUR", false);
        profile.setDisplayName("Alice Updated");
        profile.setDeletionRequested(true);
        assertEquals(accountId, profile.getAccountId());
        assertEquals("subject", profile.getSubject());
        assertEquals("Alice Updated", profile.getDisplayName());
        assertEquals(true, profile.getDeletionRequested());

        AccountDeletionRequestEntity deletion = new AccountDeletionRequestEntity("subject", DeletionStatus.REQUESTED, NOW);
        deletion.setStatus(DeletionStatus.COMPLETED);
        deletion.setRequestedAt(NOW);
        assertEquals("subject", deletion.getSubject());
        assertEquals(DeletionStatus.COMPLETED, deletion.getStatus());
        assertEquals(NOW, deletion.getRequestedAt());

        AccountExportRequestEntity export = new AccountExportRequestEntity(UUID.randomUUID(), "subject", ExportStatus.REQUESTED, NOW);
        export.setStatus(ExportStatus.READY);
        export.setRequestedAt(NOW);
        assertEquals("subject", export.getSubject());
        assertEquals(ExportStatus.READY, export.getStatus());
        assertEquals(NOW, export.getRequestedAt());
    }
}
