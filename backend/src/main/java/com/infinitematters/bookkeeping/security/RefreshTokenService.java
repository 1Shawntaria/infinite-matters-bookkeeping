package com.infinitematters.bookkeeping.security;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.notifications.NotificationCategory;
import com.infinitematters.bookkeeping.notifications.NotificationChannel;
import com.infinitematters.bookkeeping.notifications.NotificationService;
import com.infinitematters.bookkeeping.users.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenSessionRepository repository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenSessionRepository repository,
                               AuditService auditService,
                               NotificationService notificationService,
                               @Value("${bookkeeping.auth.refresh-token.ttl:P14D}") Duration refreshTokenTtl) {
        this.repository = repository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public IssuedRefreshToken issue(AppUser user) {
        String rawToken = generateToken();
        RefreshTokenSession session = new RefreshTokenSession();
        session.setUser(user);
        session.setTokenHash(hash(rawToken));
        session.setExpiresAt(Instant.now().plus(refreshTokenTtl));
        RefreshTokenSession saved = repository.save(session);
        return new IssuedRefreshToken(rawToken, saved.getExpiresAt(), saved.getId());
    }

    @Transactional(noRollbackFor = AccessDeniedException.class)
    public RotatedRefreshToken rotate(String refreshToken) {
        RefreshTokenSession currentSession = requireSessionForRotation(refreshToken);
        AppUser user = currentSession.getUser();
        user.getId();
        user.getEmail();
        user.getFullName();
        user.getCreatedAt();
        currentSession.setLastUsedAt(Instant.now());
        currentSession.setRevokedAt(Instant.now());
        currentSession.setRevokedReason("ROTATED");

        IssuedRefreshToken replacement = issue(user);
        currentSession.setReplacedBySessionId(replacement.sessionId());
        repository.save(currentSession);

        return new RotatedRefreshToken(user, replacement);
    }

    @Transactional
    public void revoke(String refreshToken) {
        repository.findByTokenHash(hash(refreshToken)).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                session.setRevokedReason("LOGOUT");
                repository.save(session);
            }
        });
    }

    @Transactional
    public RefreshTokenSession revokeSession(UUID sessionId, UUID currentUserId, String reason) {
        RefreshTokenSession session = repository.findByIdAndUserId(sessionId, currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Session does not belong to current user"));
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(Instant.now());
            session.setRevokedReason(reason);
            repository.save(session);
            auditService.recordForUser(session.getUser().getId(), null, "AUTH_SESSION_REVOKED",
                    "auth_session", session.getId().toString(), reason);
            notificationService.sendAuthNotification(
                    session.getUser(),
                    NotificationCategory.AUTH_SECURITY,
                    NotificationChannel.IN_APP,
                    "An authentication session was revoked: " + reason,
                    "auth_session",
                    session.getId().toString());
        }
        return session;
    }

    @Transactional
    public void revokeAllSessionsForUser(UUID userId, String reason) {
        List<RefreshTokenSession> sessions = repository.findByUserIdOrderByCreatedAtDesc(userId);
        Instant now = Instant.now();
        boolean revokedAny = false;
        for (RefreshTokenSession session : sessions) {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(now);
                session.setRevokedReason(reason);
                repository.save(session);
                revokedAny = true;
            }
        }
        if (revokedAny && !sessions.isEmpty()) {
            AppUser user = sessions.get(0).getUser();
            auditService.recordForUser(userId, null, "AUTH_ALL_SESSIONS_REVOKED",
                    "app_user", userId.toString(), reason);
            notificationService.sendAuthNotification(
                    user,
                    NotificationCategory.AUTH_SECURITY,
                    NotificationChannel.IN_APP,
                    "All authentication sessions were revoked: " + reason,
                    "app_user",
                    userId.toString());
        }
    }

    @Transactional(readOnly = true)
    public List<RefreshTokenSession> listSessions(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private RefreshTokenSession requireSessionForRotation(String refreshToken) {
        RefreshTokenSession session = repository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new AccessDeniedException("Invalid refresh token"));
        if (session.getRevokedAt() != null && session.getReplacedBySessionId() != null) {
            handleReuseDetected(session);
        }
        if (session.getRevokedAt() != null) {
            throw new AccessDeniedException("Refresh token has been revoked");
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new AccessDeniedException("Refresh token expired");
        }
        return session;
    }

    private void handleReuseDetected(RefreshTokenSession session) {
        Instant now = Instant.now();
        if (session.getReuseDetectedAt() == null) {
            session.setReuseDetectedAt(now);
        }
        session.setRevokedReason("REUSE_DETECTED");
        revokeReplacementChain(session.getReplacedBySessionId(), now);
        repository.save(session);
        auditService.recordForUser(session.getUser().getId(), null, "AUTH_REFRESH_TOKEN_REUSE_DETECTED", "auth_session",
                session.getId().toString(), "Replay detected for rotated refresh token");
        notificationService.sendAuthNotification(
                session.getUser(),
                NotificationCategory.AUTH_SECURITY,
                NotificationChannel.IN_APP,
                "Suspicious activity detected: a rotated refresh token was reused and the active session chain was revoked.",
                "auth_session",
                session.getId().toString());
        throw new AccessDeniedException("Refresh token reuse detected; active session chain revoked");
    }

    private void revokeReplacementChain(UUID sessionId, Instant revokedAt) {
        if (sessionId == null) {
            return;
        }
        repository.findById(sessionId).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(revokedAt);
            }
            session.setRevokedReason("REUSE_CHAIN_REVOKED");
            repository.save(session);
            revokeReplacementChain(session.getReplacedBySessionId(), revokedAt);
        });
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash refresh token", exception);
        }
    }

    public record IssuedRefreshToken(String value, Instant expiresAt, java.util.UUID sessionId) {
    }

    public record RotatedRefreshToken(AppUser user, IssuedRefreshToken refreshToken) {
    }
}
