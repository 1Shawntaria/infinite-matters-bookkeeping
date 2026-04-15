package com.infinitematters.bookkeeping.security;

import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.users.UserRole;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class TenantAccessService {
    private final RequestIdentityService requestIdentityService;
    private final UserService userService;

    public TenantAccessService(RequestIdentityService requestIdentityService, UserService userService) {
        this.requestIdentityService = requestIdentityService;
        this.userService = userService;
    }

    public UUID requireAccess(UUID organizationId) {
        UUID requestOrganizationId = requestIdentityService.requireOrganizationId();
        UUID userId = requestIdentityService.requireUserId();

        if (!requestOrganizationId.equals(organizationId)) {
            throw new AccessDeniedException("Header organization does not match requested organization");
        }
        if (!userService.hasAccess(organizationId, userId)) {
            throw new AccessDeniedException("User does not have access to organization " + organizationId);
        }
        return userId;
    }

    public UUID requireRole(UUID organizationId, Set<UserRole> allowedRoles) {
        UUID userId = requireAccess(organizationId);
        UserRole role = userService.roleForOrganization(organizationId, userId);
        if (!allowedRoles.contains(role)) {
            throw new AccessDeniedException("User does not have the required role for organization " + organizationId);
        }
        return userId;
    }
}
