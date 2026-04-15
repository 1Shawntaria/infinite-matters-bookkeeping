package com.infinitematters.bookkeeping.security;

import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

@Service
public class RequestIdentityService {
    private final HttpServletRequest request;
    private final UserService userService;

    public RequestIdentityService(HttpServletRequest request, UserService userService) {
        this.request = request;
        this.userService = userService;
    }

    public UUID requireOrganizationId() {
        return Optional.ofNullable(RequestIdentityFilter.getOrganizationId(request))
                .orElseThrow(() -> new IllegalArgumentException("Missing X-Organization-Id header"));
    }

    public UUID requireUserId() {
        return currentUserId()
                .orElseThrow(() -> new AccessDeniedException("Authentication is required"));
    }

    public AppUser requireUser() {
        return currentUserEmail()
                .map(userService::getByEmail)
                .orElseThrow(() -> new AccessDeniedException("Authentication is required"));
    }

    public Optional<UUID> currentUserId() {
        return currentUserEmail().map(email -> userService.getByEmail(email).getId());
    }

    public Optional<String> currentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(authentication.getName());
    }
}
