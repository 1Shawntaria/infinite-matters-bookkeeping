package com.infinitematters.bookkeeping.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SendGridEmailNotificationProvider implements EmailNotificationProvider {
    private static final Logger logger = LoggerFactory.getLogger(SendGridEmailNotificationProvider.class);

    private final SendGridApiClient sendGridApiClient;
    private final String fromEmail;

    public SendGridEmailNotificationProvider(SendGridApiClient sendGridApiClient,
            @Value("${bookkeeping.notifications.email.sendgrid.from-email:no-reply@example.test}") String fromEmail) {
        this.sendGridApiClient = sendGridApiClient;
        this.fromEmail = fromEmail;
    }

    @Override
    public String providerKey() {
        return "sendgrid";
    }

    @Override
    public NotificationDeliveryReceipt send(Notification notification) {
        logger.info("Queueing SendGrid email notification id={} userId={} recipientEmail={} fromEmail={} category={}",
                notification.getId(),
                notification.getUser() != null ? notification.getUser().getId() : null,
                notification.resolvedRecipientEmail(),
                fromEmail,
                notification.getCategory());
        return sendGridApiClient.send(notification, fromEmail);
    }
}
