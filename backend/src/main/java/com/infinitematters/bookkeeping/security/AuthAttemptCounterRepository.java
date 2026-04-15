package com.infinitematters.bookkeeping.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthAttemptCounterRepository extends JpaRepository<AuthAttemptCounter, UUID> {
    Optional<AuthAttemptCounter> findByActionAndSubjectKey(String action, String subjectKey);
    void deleteByWindowStartBefore(Instant cutoff);
}
