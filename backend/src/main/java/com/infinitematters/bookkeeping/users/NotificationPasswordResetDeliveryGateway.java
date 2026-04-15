package com.infinitematters.bookkeeping.users;

import com.infinitematters.bookkeeping.notifications.NotificationCategory;
import com.infinitematters.bookkeeping.notifications.NotificationChannel;
import com.infinitematters.bookkeeping.notifications.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class NotificationPasswordResetDeliveryGateway implements PasswordResetDeliveryGateway {
    private final NotificationService notificationService;
    private final String resetBaseUrl;

    public NotificationPasswordResetDeliveryGateway(NotificationService notificationService,
                                                    @Value("${bookkeeping.auth.password-reset.base-url:}") String resetBaseUrl) {
        this.notificationService = notificationService;
        this.resetBaseUrl = resetBaseUrl == null ? "" : resetBaseUrl.trim();
    }

    @Override
    public void sendResetInstructions(AppUser user,
                                      String rawToken,
                                      Instant expiresAt,
                                      UUID tokenId) {
        notificationService.sendAuthNotification(
                user,
                NotificationCategory.PASSWORD_RESET,
                NotificationChannel.EMAIL,
                buildMessage(rawToken, expiresAt),
                "password_reset_token",
                tokenId.toString());
    }

    private String buildMessage(String rawToken, Instant expiresAt) {
        String expiresAtText = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(expiresAt);
        if (resetBaseUrl.isBlank()) {
            return "Password reset requested. Use the issued reset token before it expires at " + expiresAtText + ".";
        }
        String delimiter = resetBaseUrl.contains("?") ? "&" : "?";
        String resetLink = resetBaseUrl + delimiter + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return "Password reset requested. Continue at " + resetLink + " before it expires at " + expiresAtText + ".";
    }
}
