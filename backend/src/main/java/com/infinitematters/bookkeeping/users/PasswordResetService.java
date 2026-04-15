package com.infinitematters.bookkeeping.users;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.security.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class PasswordResetService {
    private final PasswordResetTokenRepository repository;
    private final AuditService auditService;
    private final PasswordResetDeliveryGateway passwordResetDeliveryGateway;
    private final Duration ttl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(PasswordResetTokenRepository repository,
                                AuditService auditService,
                                PasswordResetDeliveryGateway passwordResetDeliveryGateway,
                                @Value("${bookkeeping.auth.password-reset.ttl:PT30M}") Duration ttl) {
        this.repository = repository;
        this.auditService = auditService;
        this.passwordResetDeliveryGateway = passwordResetDeliveryGateway;
        this.ttl = ttl;
    }

    @Transactional
    public Optional<IssuedPasswordResetToken> issue(AppUser user) {
        String rawToken = generateToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(Instant.now().plus(ttl));
        PasswordResetToken saved = repository.save(token);
        auditService.recordForUser(user.getId(), null, "PASSWORD_RESET_REQUESTED", "app_user", user.getId().toString(),
                "Password reset token issued");
        passwordResetDeliveryGateway.sendResetInstructions(user, rawToken, saved.getExpiresAt(), saved.getId());
        return Optional.of(new IssuedPasswordResetToken(rawToken, saved.getExpiresAt()));
    }

    @Transactional
    public AppUser consume(String rawToken) {
        PasswordResetToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AccessDeniedException("Invalid password reset token"));
        if (token.getConsumedAt() != null) {
            throw new AccessDeniedException("Password reset token has already been used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new AccessDeniedException("Password reset token expired");
        }
        token.setConsumedAt(Instant.now());
        repository.save(token);
        AppUser user = token.getUser();
        user.getId();
        user.getEmail();
        auditService.recordForUser(user.getId(), null, "PASSWORD_RESET_CONSUMED", "app_user", user.getId().toString(),
                "Password reset token consumed");
        return user;
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash password reset token", exception);
        }
    }

    public record IssuedPasswordResetToken(String token, Instant expiresAt) {
    }
}
