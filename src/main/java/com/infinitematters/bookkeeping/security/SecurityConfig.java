package com.infinitematters.bookkeeping.security;

import com.infinitematters.bookkeeping.users.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
                                            RequestIdentityFilter requestIdentityFilter,
                                            RequestLoggingFilter requestLoggingFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/providers/notifications/events", "/api/providers/notifications/events/*").permitAll()
                        .requestMatchers("/api/users", "/api/organizations",
                                "/api/auth/token", "/api/auth/refresh", "/api/auth/logout",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(requestLoggingFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(bearerTokenAuthenticationFilter, BasicAuthenticationFilter.class)
                .addFilterAfter(requestIdentityFilter, BasicAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(UserService userService) {
        return username -> {
            var appUser = userService.getByEmail(username);
            return User.withUsername(appUser.getEmail())
                    .password(appUser.getPasswordHash())
                    .authorities("ROLE_USER")
                    .build();
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
