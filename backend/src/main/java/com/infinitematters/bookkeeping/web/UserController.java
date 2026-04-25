package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.notifications.NotificationService;
import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.OrganizationInvitationService;
import com.infinitematters.bookkeeping.web.dto.AddMembershipRequest;
import com.infinitematters.bookkeeping.web.dto.AddMembershipByEmailRequest;
import com.infinitematters.bookkeeping.web.dto.CreateInvitationRequest;
import com.infinitematters.bookkeeping.web.dto.CreateUserRequest;
import com.infinitematters.bookkeeping.web.dto.MembershipDetailResponse;
import com.infinitematters.bookkeeping.web.dto.MembershipResponse;
import com.infinitematters.bookkeeping.web.dto.OrganizationInvitationResponse;
import com.infinitematters.bookkeeping.web.dto.OrganizationResponse;
import com.infinitematters.bookkeeping.web.dto.UpdateMembershipRoleRequest;
import com.infinitematters.bookkeeping.web.dto.UserResponse;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.users.UserRole;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final TenantAccessService tenantAccessService;
    private final RequestIdentityService requestIdentityService;
    private final AuditService auditService;
    private final OrganizationInvitationService invitationService;
    private final NotificationService notificationService;
    private final String frontendBaseUrl;

    public UserController(UserService userService,
                          TenantAccessService tenantAccessService,
                          RequestIdentityService requestIdentityService,
                          AuditService auditService,
                          OrganizationInvitationService invitationService,
                          NotificationService notificationService,
                          @Value("${bookkeeping.frontend.base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.userService = userService;
        this.tenantAccessService = tenantAccessService;
        this.requestIdentityService = requestIdentityService;
        this.auditService = auditService;
        this.invitationService = invitationService;
        this.notificationService = notificationService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @PostMapping
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(userService.create(request.email(), request.fullName(), request.password()));
    }

    @PostMapping("/memberships")
    public MembershipResponse addMembership(@Valid @RequestBody AddMembershipRequest request) {
        if (request.role() == UserRole.OWNER) {
            throw new IllegalArgumentException("Owner memberships cannot be created through this endpoint");
        }
        UUID actorUserId = tenantAccessService.requireRole(request.organizationId(), Set.of(UserRole.OWNER, UserRole.ADMIN));
        MembershipResponse response = MembershipResponse.from(userService.addMembership(
                request.organizationId(),
                request.userId(),
                request.role()));
        auditService.record(request.organizationId(), "ORGANIZATION_MEMBERSHIP_UPDATED",
                "organization_membership", response.id().toString(),
                "Membership upserted by user " + actorUserId + " for user " + request.userId() + " as " + request.role());
        return response;
    }

    @PostMapping("/memberships/by-email")
    public MembershipResponse addMembershipByEmail(@Valid @RequestBody AddMembershipByEmailRequest request) {
        if (request.role() == UserRole.OWNER) {
            throw new IllegalArgumentException("Owner memberships cannot be created through this endpoint");
        }
        UUID actorUserId = tenantAccessService.requireRole(request.organizationId(), Set.of(UserRole.OWNER, UserRole.ADMIN));
        MembershipResponse response = MembershipResponse.from(userService.addMembershipByEmail(
                request.organizationId(),
                request.email(),
                request.role()));
        auditService.record(request.organizationId(), "ORGANIZATION_MEMBERSHIP_UPDATED",
                "organization_membership", response.id().toString(),
                "Membership upserted by user " + actorUserId + " for email " + request.email() + " as " + request.role());
        return response;
    }

    @GetMapping("/memberships")
    public List<MembershipDetailResponse> listMemberships(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return userService.membershipsForOrganization(organizationId).stream()
                .map(MembershipDetailResponse::from)
                .toList();
    }

    @PostMapping("/invitations")
    public OrganizationInvitationResponse createInvitation(@Valid @RequestBody CreateInvitationRequest request) {
        UUID actorUserId = tenantAccessService.requireRole(request.organizationId(), Set.of(UserRole.OWNER, UserRole.ADMIN));
        OrganizationInvitationService.CreatedInvitation createdInvitation = invitationService.createInvitation(
                request.organizationId(),
                actorUserId,
                request.email(),
                request.role());
        auditService.record(request.organizationId(), "ORGANIZATION_INVITATION_CREATED",
                "organization_invitation", createdInvitation.invitation().getId().toString(),
                "Invitation created by user " + actorUserId + " for " + request.email() + " as " + request.role());
        return OrganizationInvitationResponse.from(
                createdInvitation.invitation(),
                inviteUrl(createdInvitation.rawToken()),
                latestDelivery(createdInvitation.invitation().getOrganization().getId(),
                        createdInvitation.invitation().getId()));
    }

    @GetMapping("/invitations")
    public List<OrganizationInvitationResponse> listInvitations(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return invitationService.invitationsForOrganization(organizationId).stream()
                .map(invitation -> OrganizationInvitationResponse.from(
                        invitation,
                        null,
                        latestDelivery(organizationId, invitation.getId())))
                .toList();
    }

    @PatchMapping("/memberships/{membershipId}")
    public MembershipDetailResponse updateMembershipRole(@RequestParam UUID organizationId,
                                                         @PathVariable UUID membershipId,
                                                         @Valid @RequestBody UpdateMembershipRoleRequest request) {
        UUID actorUserId = tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        UserRole actorRole = userService.roleForOrganization(organizationId, actorUserId);
        UserRole targetRole = userService.membershipForOrganization(organizationId, membershipId).getRole();
        if (targetRole == UserRole.OWNER && actorRole != UserRole.OWNER) {
            throw new com.infinitematters.bookkeeping.security.AccessDeniedException(
                    "Only owners can manage owner memberships");
        }
        MembershipDetailResponse response = MembershipDetailResponse.from(
                userService.updateMembershipRole(organizationId, membershipId, request.role()));
        auditService.record(organizationId, "ORGANIZATION_MEMBERSHIP_ROLE_UPDATED",
                "organization_membership", response.id().toString(),
                "Membership role updated by user " + actorUserId + " to " + request.role());
        return response;
    }

    @DeleteMapping("/memberships/{membershipId}")
    public void removeMembership(@RequestParam UUID organizationId,
                                 @PathVariable UUID membershipId) {
        UUID actorUserId = tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        UserRole actorRole = userService.roleForOrganization(organizationId, actorUserId);
        MembershipDetailResponse membership = MembershipDetailResponse.from(
                userService.membershipForOrganization(organizationId, membershipId));
        if (membership.role() == UserRole.OWNER && actorRole != UserRole.OWNER) {
            throw new com.infinitematters.bookkeeping.security.AccessDeniedException(
                    "Only owners can manage owner memberships");
        }
        userService.removeMembership(organizationId, membershipId);
        auditService.record(organizationId, "ORGANIZATION_MEMBERSHIP_REMOVED",
                "organization_membership", membership.id().toString(),
                "Membership removed by user " + actorUserId + " for user " + membership.user().id());
    }

    @DeleteMapping("/invitations/{invitationId}")
    public OrganizationInvitationResponse revokeInvitation(@RequestParam UUID organizationId,
                                                           @PathVariable UUID invitationId) {
        UUID actorUserId = tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        OrganizationInvitationResponse response = OrganizationInvitationResponse.from(
                invitationService.revokeInvitation(organizationId, invitationId),
                null,
                latestDelivery(organizationId, invitationId));
        auditService.record(organizationId, "ORGANIZATION_INVITATION_REVOKED",
                "organization_invitation", response.id().toString(),
                "Invitation revoked by user " + actorUserId + " for " + response.email());
        return response;
    }

    @GetMapping("/organizations")
    public List<OrganizationResponse> listOrganizationsForCurrentUser() {
        UUID userId = requestIdentityService.requireUserId();
        return userService.membershipsForUser(userId)
                .stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    private String inviteUrl(String token) {
        return frontendBaseUrl.replaceAll("/+$", "") + "/invite/" + token;
    }

    private com.infinitematters.bookkeeping.web.dto.InvitationDeliverySummary latestDelivery(UUID organizationId,
                                                                                              UUID invitationId) {
        return notificationService
                .latestForReference(organizationId, "organization_invitation", invitationId.toString())
                .map(com.infinitematters.bookkeeping.web.dto.InvitationDeliverySummary::from)
                .orElse(null);
    }
}
