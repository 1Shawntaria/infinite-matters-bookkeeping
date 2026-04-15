package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.security.RefreshTokenSession;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionSummary(UUID sessionId,
                                 Instant createdAt,
                                 Instant expiresAt,
                                 Instant lastUsedAt,
                                 Instant revokedAt,
                                 String revokedReason,
                                 Instant reuseDetectedAt,
                                 UUID replacedBySessionId,
                                 boolean active) {
    public static AuthSessionSummary from(RefreshTokenSession session) {
        boolean active = session.getRevokedAt() == null && session.getExpiresAt().isAfter(Instant.now());
        return new AuthSessionSummary(
                session.getId(),
                session.getCreatedAt(),
                session.getExpiresAt(),
                session.getLastUsedAt(),
                session.getRevokedAt(),
                session.getRevokedReason(),
                session.getReuseDetectedAt(),
                session.getReplacedBySessionId(),
                active);
    }
}
