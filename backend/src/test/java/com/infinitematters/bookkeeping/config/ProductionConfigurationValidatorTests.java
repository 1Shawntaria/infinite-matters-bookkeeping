package com.infinitematters.bookkeeping.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTests {
    @Test
    void skipsValidationOutsideProdProfile() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                new MockEnvironment().withProperty("spring.profiles.active", "local"),
                "short",
                false,
                true,
                "http://localhost:3000",
                "");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void acceptsHardenedProductionConfiguration() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                prodEnvironment(),
                "production-token-secret-with-at-least-32-characters",
                true,
                false,
                "https://app.infinitematters.example,https://admin.infinitematters.example",
                "https://app.infinitematters.example/reset-password");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsWeakProductionTokenSecret() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                prodEnvironment(),
                "local-development-token-secret-change-me",
                true,
                false,
                "https://app.infinitematters.example",
                "https://app.infinitematters.example/reset-password");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth token secret");
    }

    @Test
    void rejectsInsecureProductionCookies() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                prodEnvironment(),
                "production-token-secret-with-at-least-32-characters",
                false,
                false,
                "https://app.infinitematters.example",
                "https://app.infinitematters.example/reset-password");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cookies");
    }

    @Test
    void rejectsBrowserVisibleProductionTokens() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                prodEnvironment(),
                "production-token-secret-with-at-least-32-characters",
                true,
                true,
                "https://app.infinitematters.example",
                "https://app.infinitematters.example/reset-password");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("response tokens");
    }

    @Test
    void rejectsLocalhostProductionOrigins() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                prodEnvironment(),
                "production-token-secret-with-at-least-32-characters",
                true,
                false,
                "https://localhost:3000",
                "https://app.infinitematters.example/reset-password");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("localhost");
    }

    @Test
    void rejectsNonHttpsPasswordResetBaseUrl() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                prodEnvironment(),
                "production-token-secret-with-at-least-32-characters",
                true,
                false,
                "https://app.infinitematters.example",
                "http://app.infinitematters.example/reset-password");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password-reset.base-url");
    }

    private MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }
}
