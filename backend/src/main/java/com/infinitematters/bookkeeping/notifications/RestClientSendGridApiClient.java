package com.infinitematters.bookkeeping.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RestClientSendGridApiClient implements SendGridApiClient {
    private final RestClient restClient;
    private final String apiKey;

    public RestClientSendGridApiClient(RestClient.Builder restClientBuilder,
                                       @Value("${bookkeeping.notifications.email.sendgrid.base-url:https://api.sendgrid.com}")
                                       String baseUrl,
                                       @Value("${bookkeeping.notifications.email.sendgrid.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public NotificationDeliveryReceipt send(Notification notification, String fromEmail) {
        String recipientEmail = notification.resolvedRecipientEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new NotificationDeliveryException(
                    "Notification recipient email is required for SendGrid delivery",
                    false,
                    "MISSING_RECIPIENT");
        }

        Map<String, Object> body = Map.of(
                "from", Map.of("email", fromEmail),
                "personalizations", List.of(Map.of(
                        "to", List.of(Map.of("email", recipientEmail))
                )),
                "subject", subjectFor(notification),
                "content", List.of(Map.of(
                        "type", MediaType.TEXT_PLAIN_VALUE,
                        "value", notification.getMessage()
                )));

        String providerMessageId;
        try {
            providerMessageId = restClient.post()
                    .uri("/v3/mail/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getFirst("X-Message-Id");
        } catch (RestClientResponseException exception) {
            throw toDeliveryException(exception);
        }

        if (providerMessageId == null || providerMessageId.isBlank()) {
            providerMessageId = "sendgrid-" + UUID.randomUUID();
        }
        return new NotificationDeliveryReceipt("sendgrid", providerMessageId);
    }

    private NotificationDeliveryException toDeliveryException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 429) {
            return new NotificationDeliveryException("SendGrid rate limit reached", true, "RATE_LIMITED");
        }
        if (status >= 500) {
            return new NotificationDeliveryException("SendGrid provider unavailable", true, "PROVIDER_UNAVAILABLE");
        }
        if (status == 400 || status == 401 || status == 403 || status == 404) {
            return new NotificationDeliveryException("SendGrid rejected request: " + status, false, "PROVIDER_REJECTED");
        }
        return new NotificationDeliveryException("SendGrid delivery failed: " + status, false, "DELIVERY_FAILED");
    }

    private String subjectFor(Notification notification) {
        return switch (notification.getCategory()) {
            case PASSWORD_RESET -> "Reset your Infinite Matters password";
            case AUTH_SECURITY -> "Security notice for your Infinite Matters account";
            case WORKFLOW -> "Bookkeeping task reminder";
            default -> "Infinite Matters notification";
        };
    }
}
