package com.infinitematters.bookkeeping.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuthRateLimitService {
    private final AuthAttemptCounterRepository repository;
    private final int maxAttempts;
    private final Duration window;

    public AuthRateLimitService(AuthAttemptCounterRepository repository,
                                @Value("${bookkeeping.auth.rate-limit.max-attempts:5}") int maxAttempts,
                                @Value("${bookkeeping.auth.rate-limit.window:PT15M}") Duration window) {
        this.repository = repository;
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    @Transactional
    public void checkAllowed(String action, String key) {
        cleanupExpired();
        repository.findByActionAndSubjectKey(action, normalizeKey(key))
                .ifPresent(current -> {
                    if (current.getWindowStart().plus(window).isAfter(Instant.now())
                            && current.getFailureCount() >= maxAttempts) {
                        throw new TooManyRequestsException("Too many authentication attempts. Please wait and try again.");
                    }
                });
    }

    @Transactional
    public void recordFailure(String action, String key) {
        cleanupExpired();
        Instant now = Instant.now();
        AuthAttemptCounter counter = repository.findByActionAndSubjectKey(action, normalizeKey(key))
                .orElseGet(() -> {
                    AuthAttemptCounter created = new AuthAttemptCounter();
                    created.setAction(action);
                    created.setSubjectKey(normalizeKey(key));
                    created.setWindowStart(now);
                    created.setFailureCount(0);
                    return created;
                });
        if (counter.getWindowStart().plus(window).isBefore(now)) {
            counter.setWindowStart(now);
            counter.setFailureCount(0);
        }
        counter.setFailureCount(counter.getFailureCount() + 1);
        counter.setLastFailureAt(now);
        repository.save(counter);
    }

    @Transactional
    public void recordSuccess(String action, String key) {
        repository.findByActionAndSubjectKey(action, normalizeKey(key))
                .ifPresent(repository::delete);
    }

    @Transactional
    public void cleanupExpired() {
        repository.deleteByWindowStartBefore(Instant.now().minus(window));
    }

    private String normalizeKey(String key) {
        return key.toLowerCase();
    }
}
