package com.infinitematters.bookkeeping.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinitematters.bookkeeping.web.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
public class RequestIdentityFilter extends OncePerRequestFilter {
    public static final String ORGANIZATION_HEADER = "X-Organization-Id";
    private static final String ATTRIBUTE = RequestIdentityFilter.class.getName() + ".organizationId";
    private final ObjectMapper objectMapper;

    public RequestIdentityFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            request.setAttribute(ATTRIBUTE, parseUuid(request.getHeader(ORGANIZATION_HEADER)));
        } catch (IllegalArgumentException exception) {
            writeBadRequestResponse(request, response);
            return;
        }
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

    private void writeBadRequestResponse(HttpServletRequest request,
                                         HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(
                Instant.now(),
                HttpServletResponse.SC_BAD_REQUEST,
                "Bad Request",
                "Invalid X-Organization-Id header",
                request.getRequestURI(),
                RequestLoggingFilter.getRequestId(request));
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
