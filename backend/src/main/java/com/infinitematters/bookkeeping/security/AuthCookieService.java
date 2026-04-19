package com.infinitematters.bookkeeping.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

@Service
public class AuthCookieService {
    private final String accessTokenCookieName;
    private final String refreshTokenCookieName;
    private final String csrfTokenCookieName;
    private final boolean secure;
    private final String sameSite;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthCookieService(@Value("${bookkeeping.auth.cookies.access-token-name:im_access_token}")
                             String accessTokenCookieName,
                             @Value("${bookkeeping.auth.cookies.refresh-token-name:im_refresh_token}")
                             String refreshTokenCookieName,
                             @Value("${bookkeeping.auth.cookies.csrf-token-name:im_csrf_token}")
                             String csrfTokenCookieName,
                             @Value("${bookkeeping.auth.cookies.secure:false}")
                             boolean secure,
                             @Value("${bookkeeping.auth.cookies.same-site:Lax}")
                             String sameSite) {
        this.accessTokenCookieName = accessTokenCookieName;
        this.refreshTokenCookieName = refreshTokenCookieName;
        this.csrfTokenCookieName = csrfTokenCookieName;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public Optional<String> accessToken(HttpServletRequest request) {
        return cookieValue(request, accessTokenCookieName);
    }

    public Optional<String> refreshToken(HttpServletRequest request) {
        return cookieValue(request, refreshTokenCookieName);
    }

    public Optional<String> csrfToken(HttpServletRequest request) {
        return cookieValue(request, csrfTokenCookieName);
    }

    public boolean hasAuthCookie(HttpServletRequest request) {
        return accessToken(request).isPresent() || refreshToken(request).isPresent();
    }

    public void writeAuthCookies(HttpServletResponse response,
                                 AuthTokenService.IssuedToken accessToken,
                                 RefreshTokenService.IssuedRefreshToken refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie(
                accessTokenCookieName,
                accessToken.value(),
                maxAgeUntil(accessToken.expiresAt())).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie(
                refreshTokenCookieName,
                refreshToken.value(),
                maxAgeUntil(refreshToken.expiresAt())).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie(generateToken(), maxAgeUntil(refreshToken.expiresAt())).toString());
    }

    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie(accessTokenCookieName, "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie(refreshTokenCookieName, "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie("", Duration.ZERO).toString());
    }

    private Optional<String> cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private ResponseCookie authCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie csrfCookie(String value, Duration maxAge) {
        return ResponseCookie.from(csrfTokenCookieName, value)
                .httpOnly(false)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private Duration maxAgeUntil(Instant expiresAt) {
        Duration duration = Duration.between(Instant.now(), expiresAt);
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
