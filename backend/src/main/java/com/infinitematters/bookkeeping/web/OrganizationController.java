package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.OrganizationCloseTemplateItemService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.web.dto.CloseTemplateItemResponse;
import com.infinitematters.bookkeeping.web.dto.CreateCloseTemplateItemRequest;
import com.infinitematters.bookkeeping.web.dto.CreateOrganizationRequest;
import com.infinitematters.bookkeeping.web.dto.OrganizationResponse;
import com.infinitematters.bookkeeping.web.dto.UpdateWorkspaceSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final OrganizationCloseTemplateItemService organizationCloseTemplateItemService;
    private final UserService userService;
    private final AuditService auditService;
    private final RequestIdentityService requestIdentityService;
    private final TenantAccessService tenantAccessService;

    public OrganizationController(OrganizationService organizationService,
                                  OrganizationCloseTemplateItemService organizationCloseTemplateItemService,
                                  UserService userService,
                                  AuditService auditService,
                                  RequestIdentityService requestIdentityService,
                                  TenantAccessService tenantAccessService) {
        this.organizationService = organizationService;
        this.organizationCloseTemplateItemService = organizationCloseTemplateItemService;
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
                organization.getCloseMaterialityThreshold(),
                organization.getMinimumCloseNotesRequired(),
                organization.isRequireSignoffBeforeClose(),
                organization.getMinimumSignoffCount(),
                organization.isRequireOwnerSignoffBeforeClose(),
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
        var previousMaterialityThreshold = previousOrganization.getCloseMaterialityThreshold();
        int previousMinimumCloseNotesRequired = previousOrganization.getMinimumCloseNotesRequired();
        boolean previousRequireSignoffBeforeClose = previousOrganization.isRequireSignoffBeforeClose();
        int previousMinimumSignoffCount = previousOrganization.getMinimumSignoffCount();
        boolean previousRequireOwnerSignoffBeforeClose = previousOrganization.isRequireOwnerSignoffBeforeClose();
        var organization = organizationService.updateSettings(
                organizationId,
                request.name(),
                request.timezone(),
                request.invitationTtlDays(),
                request.closeMaterialityThreshold(),
                request.minimumCloseNotesRequired(),
                request.requireSignoffBeforeClose(),
                request.minimumSignoffCount(),
                request.requireOwnerSignoffBeforeClose());
        auditService.record(organizationId, "ORGANIZATION_SETTINGS_UPDATED", "organization",
                organizationId.toString(),
                buildSettingsAuditDescription(
                        previousName,
                        previousTimezone,
                        previousInvitationTtlDays,
                        previousMaterialityThreshold,
                        previousMinimumCloseNotesRequired,
                        previousRequireSignoffBeforeClose,
                        previousMinimumSignoffCount,
                        previousRequireOwnerSignoffBeforeClose,
                        organization,
                        actorUserId));
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getPlanTier(),
                organization.getTimezone(),
                organization.getInvitationTtlDays(),
                organization.getCloseMaterialityThreshold(),
                organization.getMinimumCloseNotesRequired(),
                organization.isRequireSignoffBeforeClose(),
                organization.getMinimumSignoffCount(),
                organization.isRequireOwnerSignoffBeforeClose(),
                organization.getCreatedAt(),
                userService.roleForOrganization(organizationId, actorUserId));
    }

    @GetMapping("/close-template-items")
    public List<CloseTemplateItemResponse> listCloseTemplateItems(@RequestParam UUID organizationId) {
        tenantAccessService.requireAccess(organizationId);
        return organizationCloseTemplateItemService.list(organizationId).stream()
                .map(CloseTemplateItemResponse::from)
                .toList();
    }

    @PostMapping("/close-template-items")
    public CloseTemplateItemResponse createCloseTemplateItem(@RequestParam UUID organizationId,
                                                             @Valid @RequestBody CreateCloseTemplateItemRequest request) {
        UUID actorUserId = tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        var item = organizationCloseTemplateItemService.create(organizationId, request.label(), request.guidance());
        auditService.record(
                organizationId,
                "ORGANIZATION_CLOSE_TEMPLATE_ITEM_CREATED",
                "organization_close_template_item",
                item.getId().toString(),
                "Close template item '" + item.getLabel() + "' created by user " + actorUserId);
        return CloseTemplateItemResponse.from(item);
    }

    @DeleteMapping("/close-template-items/{itemId}")
    public void deleteCloseTemplateItem(@RequestParam UUID organizationId,
                                        @PathVariable UUID itemId) {
        UUID actorUserId = tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        organizationCloseTemplateItemService.delete(organizationId, itemId);
        auditService.record(
                organizationId,
                "ORGANIZATION_CLOSE_TEMPLATE_ITEM_DELETED",
                "organization_close_template_item",
                itemId.toString(),
                "Close template item removed by user " + actorUserId);
    }

    private static String buildSettingsAuditDescription(String previousName,
                                                        String previousTimezone,
                                                        int previousInvitationTtlDays,
                                                        java.math.BigDecimal previousMaterialityThreshold,
                                                        int previousMinimumCloseNotesRequired,
                                                        boolean previousRequireSignoffBeforeClose,
                                                        int previousMinimumSignoffCount,
                                                        boolean previousRequireOwnerSignoffBeforeClose,
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
        if (previousMaterialityThreshold.compareTo(updatedOrganization.getCloseMaterialityThreshold()) != 0) {
            description.append(" close materiality ")
                    .append(previousMaterialityThreshold)
                    .append(" -> ")
                    .append(updatedOrganization.getCloseMaterialityThreshold())
                    .append(";");
        }
        if (previousMinimumCloseNotesRequired != updatedOrganization.getMinimumCloseNotesRequired()) {
            description.append(" minimum close notes ")
                    .append(previousMinimumCloseNotesRequired)
                    .append(" -> ")
                    .append(updatedOrganization.getMinimumCloseNotesRequired())
                    .append(";");
        }
        if (previousRequireSignoffBeforeClose != updatedOrganization.isRequireSignoffBeforeClose()) {
            description.append(" require signoff before close ")
                    .append(previousRequireSignoffBeforeClose)
                    .append(" -> ")
                    .append(updatedOrganization.isRequireSignoffBeforeClose())
                    .append(";");
        }
        if (previousMinimumSignoffCount != updatedOrganization.getMinimumSignoffCount()) {
            description.append(" minimum signoff count ")
                    .append(previousMinimumSignoffCount)
                    .append(" -> ")
                    .append(updatedOrganization.getMinimumSignoffCount())
                    .append(";");
        }
        if (previousRequireOwnerSignoffBeforeClose != updatedOrganization.isRequireOwnerSignoffBeforeClose()) {
            description.append(" require owner signoff before close ")
                    .append(previousRequireOwnerSignoffBeforeClose)
                    .append(" -> ")
                    .append(updatedOrganization.isRequireOwnerSignoffBeforeClose())
                    .append(";");
        }
        return description.toString();
    }
}
