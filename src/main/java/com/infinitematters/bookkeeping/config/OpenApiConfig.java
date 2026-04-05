package com.infinitematters.bookkeeping.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI bookkeepingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Infinite Matters Bookkeeping API")
                .description("AI-assisted bookkeeping workflow and close management API. "
                        + "For frontend home-screen integrations, use /api/dashboard/home as the versioned dashboard contract boundary.")
                .version("v1")
                .contact(new Contact().name("Infinite Matters"))
                .license(new License().name("Proprietary")));
    }
}
