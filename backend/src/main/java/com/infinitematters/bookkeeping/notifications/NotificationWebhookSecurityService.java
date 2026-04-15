package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.security.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class NotificationWebhookSecurityService {
    private final String sharedSecret;
    private final String sendGridPublicKey;

    public NotificationWebhookSecurityService(
            @Value("${bookkeeping.notifications.provider.webhook-secret:local-webhook-secret}") String sharedSecret,
            @Value("${bookkeeping.notifications.webhooks.sendgrid.public-key:}") String sendGridPublicKey) {
        this.sharedSecret = sharedSecret;
        this.sendGridPublicKey = sendGridPublicKey;
    }

    public VerifiedProviderEvent verifyGenericSecret(String candidateSecret) {
        if (candidateSecret == null || !candidateSecret.equals(sharedSecret)) {
            throw new AccessDeniedException("Invalid provider webhook secret");
        }
        return new VerifiedProviderEvent("SHARED_SECRET", "secret");
    }

    public VerifiedProviderEvent verifyProviderRequest(String providerKey,
                                                       String candidateSecret,
                                                       String payload,
                                                       String signatureHeader,
                                                       String timestampHeader) {
        if ("sendgrid".equalsIgnoreCase(providerKey)) {
            return verifySendGridSignature(payload, signatureHeader, timestampHeader);
        }
        return verifyGenericSecret(candidateSecret);
    }

    private VerifiedProviderEvent verifySendGridSignature(String payload, String signatureHeader, String timestampHeader) {
        if (signatureHeader == null || timestampHeader == null) {
            throw new AccessDeniedException("Missing SendGrid webhook signature headers");
        }
        if (sendGridPublicKey == null || sendGridPublicKey.isBlank()) {
            throw new AccessDeniedException("SendGrid webhook public key is not configured");
        }
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(parsePublicKey(sendGridPublicKey));
            verifier.update((timestampHeader + payload).getBytes(StandardCharsets.UTF_8));
            boolean verified = verifier.verify(Base64.getDecoder().decode(signatureHeader));
            if (!verified) {
                throw new AccessDeniedException("Invalid SendGrid webhook signature");
            }
            return new VerifiedProviderEvent("SENDGRID_SIGNATURE", timestampHeader);
        } catch (AccessDeniedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AccessDeniedException("Unable to verify SendGrid webhook signature");
        }
    }

    private PublicKey parsePublicKey(String encodedKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(keyBytes));
    }
}
