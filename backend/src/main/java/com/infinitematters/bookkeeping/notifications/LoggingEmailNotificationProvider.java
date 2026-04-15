package com.infinitematters.bookkeeping.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LoggingEmailNotificationProvider implements EmailNotificationProvider {
    private static final Logger logger = LoggerFactory.getLogger(LoggingEmailNotificationProvider.class);

    @Override
    public String providerKey() {
        return "logging";
    }

    @Override
    public NotificationDeliveryReceipt send(Notification notification) {
        logger.info("Delivering email notification id={} userId={} recipientEmail={} category={} referenceType={} referenceId={} message={}",
                notification.getId(),
                notification.getUser() != null ? notification.getUser().getId() : null,
                notification.resolvedRecipientEmail(),
                notification.getCategory(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getMessage());
        return new NotificationDeliveryReceipt("logging-email-provider", "log-" + UUID.randomUUID());
    }
}
