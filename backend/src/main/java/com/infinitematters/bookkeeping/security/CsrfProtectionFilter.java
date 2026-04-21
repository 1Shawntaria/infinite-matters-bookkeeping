package com.infinitematters.bookkeeping.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinitematters.bookkeeping.web.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CsrfProtectionFilter extends OncePerRequestFilter {
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> CSRF_EXEMPT_POST_PATHS = Set.of(
            "/api/users",
            "/api/organizations",
            "/api/auth/token",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/providers/notifications/events");

    private final AuthCookieService authCookieService;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedOrigins;

    public CsrfProtectionFilter(AuthCookieService authCookieService,
                                ObjectMapper objectMapper,
                                @Value("${bookkeeping.security.allowed-origins:http://localhost:3000,http://localhost:3001}")
                                String allowedOrigins) {
        this.authCookieService = authCookieService;
        this.objectMapper = objectMapper;
        this.allowedOrigins = parseOrigins(allowedOrigins);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isSafeMethod(request) || isCsrfExempt(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (hasBearerToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean hasAuthCookie = authCookieService.hasAuthCookie(request);
        if (hasAuthCookie && !isAllowedBrowserOrigin(request)) {
            writeForbiddenResponse(request, response, "Request origin is not allowed");
            return;
        }

        if (hasAuthCookie && !hasValidCsrfToken(request)) {
            writeForbiddenResponse(request, response, "CSRF token is missing or invalid");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSafeMethod(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT));
    }

    private boolean isCsrfExempt(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String path = request.getRequestURI();
        return CSRF_EXEMPT_POST_PATHS.contains(path)
                || path.startsWith("/api/providers/notifications/events/");
    }

    private boolean hasBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.startsWith(BEARER_PREFIX);
    }

    private boolean isAllowedBrowserOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origin.isBlank()) {
            return allowedOrigins.contains(origin);
        }

        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer == null || referer.isBlank()) {
            return true;
        }

        String refererOrigin = originFromUri(referer);
        return refererOrigin != null && allowedOrigins.contains(refererOrigin);
    }

    private boolean hasValidCsrfToken(HttpServletRequest request) {
        String headerToken = request.getHeader(CSRF_HEADER);
        if (headerToken == null || headerToken.isBlank()) {
            return false;
        }
        return authCookieService.csrfToken(request)
                .filter(cookieToken -> cookieToken.equals(headerToken))
                .isPresent();
    }

    private Set<String> parseOrigins(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String originFromUri(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port >= 0 ? ":" + port : "");
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void writeForbiddenResponse(HttpServletRequest request,
                                        HttpServletResponse response,
                                        String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(
                Instant.now(),
                HttpServletResponse.SC_FORBIDDEN,
                "Forbidden",
                message,
                request.getRequestURI(),
                RequestLoggingFilter.getRequestId(request));
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
