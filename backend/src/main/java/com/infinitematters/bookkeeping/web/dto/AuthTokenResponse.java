package com.infinitematters.bookkeeping.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthTokenResponse(String accessToken,
                                String tokenType,
                                Instant expiresAt,
                                String refreshToken,
                                UUID refreshSessionId,
                                Instant refreshTokenExpiresAt,
                                UserResponse user) {
}
