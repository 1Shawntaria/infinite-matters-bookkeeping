package com.infinitematters.bookkeeping.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdentityFilter extends OncePerRequestFilter {
    public static final String ORGANIZATION_HEADER = "X-Organization-Id";
    private static final String ATTRIBUTE = RequestIdentityFilter.class.getName() + ".organizationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        request.setAttribute(ATTRIBUTE, parseUuid(request.getHeader(ORGANIZATION_HEADER)));
        filterChain.doFilter(request, response);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    public static UUID getOrganizationId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof UUID uuid ? uuid : null;
    }
}
