package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.web.dto.CreateOrganizationRequest;
import com.infinitematters.bookkeeping.web.dto.OrganizationResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationService organizationService;
    private final UserService userService;
    private final AuditService auditService;
    private final RequestIdentityService requestIdentityService;

    public OrganizationController(OrganizationService organizationService,
                                  UserService userService,
                                  AuditService auditService,
                                  RequestIdentityService requestIdentityService) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.auditService = auditService;
        this.requestIdentityService = requestIdentityService;
    }

    @PostMapping
    public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
        var organization = organizationService.create(
                request.name(),
                request.planTier(),
                request.timezone());
        userService.addMembership(organization.getId(), request.ownerUserId(), UserRole.OWNER);
        auditService.record(organization.getId(), "ORGANIZATION_CREATED", "organization", organization.getId().toString(),
                "Organization created with owner " + request.ownerUserId());
        return OrganizationResponse.from(organization);
    }

    @GetMapping
    public List<OrganizationResponse> list() {
        var userId = requestIdentityService.requireUserId();
        return userService.membershipsForUser(userId)
                .stream()
                .map(membership -> OrganizationResponse.from(membership.getOrganization()))
                .toList();
    }
}
