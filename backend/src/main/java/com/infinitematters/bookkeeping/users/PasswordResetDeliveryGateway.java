package com.infinitematters.bookkeeping.users;

import java.time.Instant;
import java.util.UUID;

public interface PasswordResetDeliveryGateway {
    void sendResetInstructions(AppUser user,
                               String rawToken,
                               Instant expiresAt,
                               UUID tokenId);
}
