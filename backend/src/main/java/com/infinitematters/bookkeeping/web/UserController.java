package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.web.dto.AddMembershipRequest;
import com.infinitematters.bookkeeping.web.dto.CreateUserRequest;
import com.infinitematters.bookkeeping.web.dto.MembershipResponse;
import com.infinitematters.bookkeeping.web.dto.OrganizationResponse;
import com.infinitematters.bookkeeping.web.dto.UserResponse;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.users.UserRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final OrganizationService organizationService;
    private final TenantAccessService tenantAccessService;
    private final RequestIdentityService requestIdentityService;

    public UserController(UserService userService,
                          OrganizationService organizationService,
                          TenantAccessService tenantAccessService,
                          RequestIdentityService requestIdentityService) {
        this.userService = userService;
        this.organizationService = organizationService;
        this.tenantAccessService = tenantAccessService;
        this.requestIdentityService = requestIdentityService;
    }

    @PostMapping
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(userService.create(request.email(), request.fullName(), request.password()));
    }

    @PostMapping("/memberships")
    public MembershipResponse addMembership(@Valid @RequestBody AddMembershipRequest request) {
        tenantAccessService.requireRole(request.organizationId(), Set.of(UserRole.OWNER, UserRole.ADMIN));
        return MembershipResponse.from(userService.addMembership(
                request.organizationId(),
                request.userId(),
                request.role()));
    }

    @GetMapping("/organizations")
    public List<OrganizationResponse> listOrganizationsForCurrentUser() {
        UUID userId = requestIdentityService.requireUserId();
        return userService.membershipsForUser(userId)
                .stream()
                .map(OrganizationResponse::from)
                .toList();
    }
}
