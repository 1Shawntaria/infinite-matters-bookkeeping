package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.web.dto.CreateOrganizationRequest;
import com.infinitematters.bookkeeping.web.dto.OrganizationResponse;
import com.infinitematters.bookkeeping.web.dto.UpdateWorkspaceSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationService organizationService;
    private final UserService userService;
    private final AuditService auditService;
    private final RequestIdentityService requestIdentityService;
    private final TenantAccessService tenantAccessService;

    public OrganizationController(OrganizationService organizationService,
                                  UserService userService,
                                  AuditService auditService,
                                  RequestIdentityService requestIdentityService,
                                  TenantAccessService tenantAccessService) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.auditService = auditService;
        this.requestIdentityService = requestIdentityService;
        this.tenantAccessService = tenantAccessService;
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

    @GetMapping("/settings")
    public OrganizationResponse settings(@RequestParam UUID organizationId) {
        UUID userId = tenantAccessService.requireAccess(organizationId);
        UserRole role = userService.roleForOrganization(organizationId, userId);
        var organization = organizationService.get(organizationId);
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getPlanTier(),
                organization.getTimezone(),
                organization.getInvitationTtlDays(),
                organization.getCreatedAt(),
                role);
    }

    @PatchMapping("/settings")
    public OrganizationResponse updateSettings(@RequestParam UUID organizationId,
                                               @Valid @RequestBody UpdateWorkspaceSettingsRequest request) {
        UUID actorUserId = tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        var previousOrganization = organizationService.get(organizationId);
        String previousName = previousOrganization.getName();
        String previousTimezone = previousOrganization.getTimezone();
        int previousInvitationTtlDays = previousOrganization.getInvitationTtlDays();
        var organization = organizationService.updateSettings(
                organizationId,
                request.name(),
                request.timezone(),
                request.invitationTtlDays());
        auditService.record(organizationId, "ORGANIZATION_SETTINGS_UPDATED", "organization",
                organizationId.toString(),
                buildSettingsAuditDescription(previousName, previousTimezone, previousInvitationTtlDays, organization, actorUserId));
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getPlanTier(),
                organization.getTimezone(),
                organization.getInvitationTtlDays(),
                organization.getCreatedAt(),
                userService.roleForOrganization(organizationId, actorUserId));
    }

    private static String buildSettingsAuditDescription(String previousName,
                                                        String previousTimezone,
                                                        int previousInvitationTtlDays,
                                                        com.infinitematters.bookkeeping.organization.Organization updatedOrganization,
                                                        UUID actorUserId) {
        StringBuilder description = new StringBuilder("Workspace settings updated by user " + actorUserId + ":");
        if (!previousName.equals(updatedOrganization.getName())) {
            description.append(" name '")
                    .append(previousName)
                    .append("' -> '")
                    .append(updatedOrganization.getName())
                    .append("';");
        }
        if (!previousTimezone.equals(updatedOrganization.getTimezone())) {
            description.append(" timezone ")
                    .append(previousTimezone)
                    .append(" -> ")
                    .append(updatedOrganization.getTimezone())
                    .append(";");
        }
        if (previousInvitationTtlDays != updatedOrganization.getInvitationTtlDays()) {
            description.append(" invitation TTL ")
                    .append(previousInvitationTtlDays)
                    .append(" -> ")
                    .append(updatedOrganization.getInvitationTtlDays())
                    .append(" days;");
        }
        return description.toString();
    }
}
