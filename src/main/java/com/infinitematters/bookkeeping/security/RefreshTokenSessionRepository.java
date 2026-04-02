package com.infinitematters.bookkeeping.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {
    Optional<RefreshTokenSession> findByTokenHash(String tokenHash);
    List<RefreshTokenSession> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<RefreshTokenSession> findByIdAndUserId(UUID sessionId, UUID userId);
}
