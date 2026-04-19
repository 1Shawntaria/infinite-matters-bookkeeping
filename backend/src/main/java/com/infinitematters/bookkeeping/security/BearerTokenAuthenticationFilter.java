package com.infinitematters.bookkeeping.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinitematters.bookkeeping.web.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenService authTokenService;
    private final AuthCookieService authCookieService;
    private final ObjectMapper objectMapper;

    public BearerTokenAuthenticationFilter(AuthTokenService authTokenService,
                                           AuthCookieService authCookieService,
                                           ObjectMapper objectMapper) {
        this.authTokenService = authTokenService;
        this.authCookieService = authCookieService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = tokenFromRequest(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (hasAuthenticatedPrincipal()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthTokenService.AuthenticatedUser authenticatedUser = authTokenService.authenticate(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            authenticatedUser.email(),
                            token,
                            List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (AccessDeniedException exception) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(request, response, exception.getMessage());
        }
    }

    private String tokenFromRequest(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return authCookieService.accessToken(request).orElse(null);
    }

    private boolean hasAuthenticatedPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private void writeUnauthorizedResponse(HttpServletRequest request,
                                           HttpServletResponse response,
                                           String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(
                Instant.now(),
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                message,
                request.getRequestURI(),
                RequestLoggingFilter.getRequestId(request));
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
