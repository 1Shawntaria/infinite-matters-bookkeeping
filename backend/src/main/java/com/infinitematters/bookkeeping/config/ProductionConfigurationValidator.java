package com.infinitematters.bookkeeping.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;

@Component
public class ProductionConfigurationValidator {
    private static final String PROD_PROFILE = "prod";
    private static final String LOCAL_AUTH_SECRET = "local-development-token-secret-change-me";

    private final Environment environment;
    private final String authTokenSecret;
    private final boolean cookiesSecure;
    private final boolean responseTokensEnabled;
    private final String allowedOrigins;
    private final String passwordResetBaseUrl;

    public ProductionConfigurationValidator(
            Environment environment,
            @Value("${bookkeeping.auth.token.secret:}") String authTokenSecret,
            @Value("${bookkeeping.auth.cookies.secure:false}") boolean cookiesSecure,
            @Value("${bookkeeping.auth.response-tokens.enabled:true}") boolean responseTokensEnabled,
            @Value("${bookkeeping.security.allowed-origins:}") String allowedOrigins,
            @Value("${bookkeeping.auth.password-reset.base-url:}") String passwordResetBaseUrl) {
        this.environment = environment;
        this.authTokenSecret = authTokenSecret;
        this.cookiesSecure = cookiesSecure;
        this.responseTokensEnabled = responseTokensEnabled;
        this.allowedOrigins = allowedOrigins;
        this.passwordResetBaseUrl = passwordResetBaseUrl;
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

        validateAllowedOrigins();
        validateHttpsUrl(passwordResetBaseUrl, "bookkeeping.auth.password-reset.base-url");
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
