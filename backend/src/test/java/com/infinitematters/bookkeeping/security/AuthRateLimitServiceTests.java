package com.infinitematters.bookkeeping.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitServiceTests {

    @Mock
    private AuthAttemptCounterRepository repository;

    private AuthRateLimitService authRateLimitService;

    @BeforeEach
    void setUp() {
        authRateLimitService = new AuthRateLimitService(repository, 5, Duration.ofMinutes(15));
    }

    @Test
    void blocksWhenFailureThresholdReachedWithinWindow() {
        AuthAttemptCounter counter = new AuthAttemptCounter();
        counter.setAction("login");
        counter.setSubjectKey("owner@acme.test");
        counter.setWindowStart(Instant.now().minusSeconds(60));
        counter.setFailureCount(5);

        when(repository.findByActionAndSubjectKey("login", "owner@acme.test"))
                .thenReturn(Optional.of(counter));

        assertThatThrownBy(() -> authRateLimitService.checkAllowed("login", "OWNER@ACME.TEST"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Too many authentication attempts. Please wait and try again.");
    }

    @Test
    void clearsCounterOnSuccessfulAuthentication() {
        AuthAttemptCounter counter = new AuthAttemptCounter();

        when(repository.findByActionAndSubjectKey("login", "owner@acme.test"))
                .thenReturn(Optional.of(counter));

        authRateLimitService.recordSuccess("login", "OWNER@ACME.TEST");

        verify(repository).delete(counter);
    }

    @Test
    void persistsFailureCounts() {
        when(repository.findByActionAndSubjectKey(eq("login"), eq("owner@acme.test")))
                .thenReturn(Optional.empty());

        authRateLimitService.recordFailure("login", "OWNER@ACME.TEST");

        verify(repository).save(any(AuthAttemptCounter.class));
    }
}
