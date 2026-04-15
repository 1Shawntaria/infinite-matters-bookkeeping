package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.security.AccessDeniedException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationWebhookSecurityServiceTests {

    @Test
    void verifiesValidSendGridSignature() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        String payload = "[{\"event\":\"delivered\"}]";
        String timestamp = "1772442060";
        String signature = sign(keyPair, timestamp + payload);

        NotificationWebhookSecurityService service = new NotificationWebhookSecurityService(
                "shared-secret",
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        assertThatCode(() -> service.verifyProviderRequest(
                "sendgrid",
                null,
                payload,
                signature,
                timestamp)).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidSendGridSignature() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        NotificationWebhookSecurityService service = new NotificationWebhookSecurityService(
                "shared-secret",
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        assertThatThrownBy(() -> service.verifyProviderRequest(
                "sendgrid",
                null,
                "[]",
                Base64.getEncoder().encodeToString("bad-signature".getBytes(StandardCharsets.UTF_8)),
                "1772442060"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void fallsBackToSharedSecretForNonSignedProviders() {
        NotificationWebhookSecurityService service =
                new NotificationWebhookSecurityService("shared-secret", "");

        assertThatCode(() -> service.verifyProviderRequest(
                "ses",
                "shared-secret",
                "{}",
                null,
                null)).doesNotThrowAnyException();
    }

    private String sign(KeyPair keyPair, String payload) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }
}
