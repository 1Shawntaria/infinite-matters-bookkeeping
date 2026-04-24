package com.infinitematters.bookkeeping.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;

@Component
public class ProductionConfigurationValidator {
    private static final String PROD_PROFILE = "prod";
    private static final String LOCAL_AUTH_SECRET = "local-development-token-secret-change-me";
    private static final Set<String> SUPPORTED_EMAIL_PROVIDERS = Set.of("logging", "sendgrid");

    private final Environment environment;
    private final String datasourceUrl;
    private final String authTokenSecret;
    private final boolean cookiesSecure;
    private final boolean responseTokensEnabled;
    private final String allowedOrigins;
    private final String passwordResetBaseUrl;
    private final String notificationProviderWebhookSecret;
    private final String emailProvider;
    private final String sendGridApiKey;
    private final String sendGridFromEmail;
    private final String sendGridWebhookPublicKey;

    public ProductionConfigurationValidator(
            Environment environment,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${bookkeeping.auth.token.secret:}") String authTokenSecret,
            @Value("${bookkeeping.auth.cookies.secure:false}") boolean cookiesSecure,
            @Value("${bookkeeping.auth.response-tokens.enabled:true}") boolean responseTokensEnabled,
            @Value("${bookkeeping.security.allowed-origins:}") String allowedOrigins,
            @Value("${bookkeeping.auth.password-reset.base-url:}") String passwordResetBaseUrl,
            @Value("${bookkeeping.notifications.provider.webhook-secret:}") String notificationProviderWebhookSecret,
            @Value("${bookkeeping.notifications.email.provider:logging}") String emailProvider,
            @Value("${bookkeeping.notifications.email.sendgrid.api-key:}") String sendGridApiKey,
            @Value("${bookkeeping.notifications.email.sendgrid.from-email:}") String sendGridFromEmail,
            @Value("${bookkeeping.notifications.webhooks.sendgrid.public-key:}") String sendGridWebhookPublicKey) {
        this.environment = environment;
        this.datasourceUrl = datasourceUrl;
        this.authTokenSecret = authTokenSecret;
        this.cookiesSecure = cookiesSecure;
        this.responseTokensEnabled = responseTokensEnabled;
        this.allowedOrigins = allowedOrigins;
        this.passwordResetBaseUrl = passwordResetBaseUrl;
        this.notificationProviderWebhookSecret = notificationProviderWebhookSecret;
        this.emailProvider = emailProvider;
        this.sendGridApiKey = sendGridApiKey;
        this.sendGridFromEmail = sendGridFromEmail;
        this.sendGridWebhookPublicKey = sendGridWebhookPublicKey;
    }

    @PostConstruct
    void validate() {
        if (!isProdProfileActive()) {
            return;
        }

        require(authTokenSecret, "bookkeeping.auth.token.secret");
        if (authTokenSecret.length() < 32 || LOCAL_AUTH_SECRET.equals(authTokenSecret)) {
            throw new IllegalStateException("Production auth token secret must be unique and at least 32 characters");
        }
        if (!cookiesSecure) {
            throw new IllegalStateException("Production auth cookies must be secure");
        }
        if (responseTokensEnabled) {
            throw new IllegalStateException("Production auth response tokens must be disabled");
        }

        validateDatasource();
        validateAllowedOrigins();
        validateHttpsUrl(passwordResetBaseUrl, "bookkeeping.auth.password-reset.base-url");
        validateNotifications();
    }

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(PROD_PROFILE::equalsIgnoreCase);
    }

    private void validateAllowedOrigins() {
        require(allowedOrigins, "bookkeeping.security.allowed-origins");

        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .forEach(origin -> {
                    if ("*".equals(origin) || origin.contains("*")) {
                        throw new IllegalStateException("Production allowed origins must not contain wildcards");
                    }
                    URI uri = parseUri(origin, "bookkeeping.security.allowed-origins");
                    if (!"https".equalsIgnoreCase(uri.getScheme())) {
                        throw new IllegalStateException("Production allowed origins must use HTTPS");
                    }
                    String host = uri.getHost();
                    if (host == null || host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")) {
                        throw new IllegalStateException("Production allowed origins must not use localhost");
                    }
                });
    }

    private void validateDatasource() {
        require(datasourceUrl, "spring.datasource.url");
        String normalizedDatasourceUrl = datasourceUrl.trim().toLowerCase();
        if (normalizedDatasourceUrl.startsWith("jdbc:h2:")) {
            throw new IllegalStateException("Production datasource must not use H2");
        }
    }

    private void validateNotifications() {
        require(notificationProviderWebhookSecret, "bookkeeping.notifications.provider.webhook-secret");

        if (!SUPPORTED_EMAIL_PROVIDERS.contains(emailProvider.toLowerCase())) {
            throw new IllegalStateException("Unsupported production email provider: " + emailProvider);
        }

        if ("logging".equalsIgnoreCase(emailProvider)) {
            throw new IllegalStateException("Production email provider must deliver real email");
        }

        if (!"sendgrid".equalsIgnoreCase(emailProvider)) {
            return;
        }

        require(sendGridApiKey, "bookkeeping.notifications.email.sendgrid.api-key");
        require(sendGridFromEmail, "bookkeeping.notifications.email.sendgrid.from-email");
        require(sendGridWebhookPublicKey, "bookkeeping.notifications.webhooks.sendgrid.public-key");

        if (!sendGridFromEmail.contains("@")) {
            throw new IllegalStateException(
                    "Production bookkeeping.notifications.email.sendgrid.from-email must be a valid email");
        }
    }

    private void validateHttpsUrl(String value, String propertyName) {
        require(value, propertyName);
        URI uri = parseUri(value, propertyName);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException("Production " + propertyName + " must be an HTTPS URL");
        }
    }

    private URI parseUri(String value, String propertyName) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid production URL in " + propertyName, exception);
        }
    }

    private void require(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required production configuration: " + propertyName);
        }
    }
}
