package com.infinitematters.bookkeeping.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinitematters.bookkeeping.users.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthTokenService {
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final Duration tokenTtl;

    public AuthTokenService(ObjectMapper objectMapper,
                            @Value("${bookkeeping.auth.token.secret}") String secret,
                            @Value("${bookkeeping.auth.token.ttl:PT8H}") Duration tokenTtl) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tokenTtl = tokenTtl;
    }

    public IssuedToken issueToken(AppUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(tokenTtl);
        TokenPayload payload = new TokenPayload(user.getId(), user.getEmail(), issuedAt, expiresAt);
        String payloadPart = encodePayload(payload);
        String signaturePart = sign(payloadPart);
        return new IssuedToken(payloadPart + "." + signaturePart, expiresAt);
    }

    public AuthenticatedUser authenticate(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new AccessDeniedException("Invalid bearer token");
        }

        String payloadPart = parts[0];
        String providedSignature = parts[1];
        String expectedSignature = sign(payloadPart);
        if (!MessageDigest.isEqual(providedSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("Invalid bearer token");
        }

        TokenPayload payload = decodePayload(payloadPart);
        if (payload.expiresAt().isBefore(Instant.now())) {
            throw new AccessDeniedException("Bearer token expired");
        }
        return new AuthenticatedUser(payload.userId(), payload.email(), payload.expiresAt());
    }

    private String encodePayload(TokenPayload payload) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to issue auth token", exception);
        }
    }

    private TokenPayload decodePayload(String payloadPart) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(payloadPart);
            return objectMapper.readValue(json, TokenPayload.class);
        } catch (Exception exception) {
            throw new AccessDeniedException("Invalid bearer token");
        }
    }

    private String sign(String payloadPart) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] signature = mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign auth token", exception);
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public record AuthenticatedUser(UUID userId, String email, Instant expiresAt) {
    }

    private record TokenPayload(UUID userId, String email, Instant issuedAt, Instant expiresAt) {
    }
}
