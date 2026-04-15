package com.infinitematters.bookkeeping.web.dto;

import java.time.Instant;

public record ForgotPasswordResponse(String status,
                                     String deliveryChannel,
                                     String resetToken,
                                     Instant resetTokenExpiresAt) {
}
