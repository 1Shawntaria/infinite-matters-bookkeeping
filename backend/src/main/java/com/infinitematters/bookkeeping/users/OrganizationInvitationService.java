package com.infinitematters.bookkeeping.users;

import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class OrganizationInvitationService {
    private static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final OrganizationInvitationRepository invitationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final OrganizationInvitationDeliveryGateway invitationDeliveryGateway;

    public OrganizationInvitationService(OrganizationInvitationRepository invitationRepository,
                                         OrganizationMembershipRepository membershipRepository,
                                         OrganizationService organizationService,
                                         UserService userService,
                                         OrganizationInvitationDeliveryGateway invitationDeliveryGateway) {
        this.invitationRepository = invitationRepository;
        this.membershipRepository = membershipRepository;
        this.organizationService = organizationService;
        this.userService = userService;
        this.invitationDeliveryGateway = invitationDeliveryGateway;
    }

    @Transactional
    public CreatedInvitation createInvitation(UUID organizationId,
                                              UUID invitedByUserId,
                                              String email,
                                              UserRole role) {
        if (role == UserRole.OWNER) {
            throw new IllegalArgumentException("Owner invitations are not supported through this endpoint");
        }
        Organization organization = organizationService.get(organizationId);
        String normalizedEmail = normalizeEmail(email);

        userService.findByEmail(normalizedEmail)
                .flatMap(user -> membershipRepository.findByOrganizationIdAndUserId(organizationId, user.getId()))
                .ifPresent(existingMembership -> {
                    throw new IllegalArgumentException("That user already has workspace access");
                });

        expireStaleInvitations(organizationId);
        if (invitationRepository.existsByOrganizationIdAndEmailIgnoreCaseAndStatus(
                organizationId, normalizedEmail, OrganizationInvitationStatus.PENDING)) {
            throw new IllegalArgumentException("A pending invitation already exists for that email");
        }

        String token = generateToken();
        OrganizationInvitation invitation = new OrganizationInvitation();
        invitation.setOrganization(organization);
        invitation.setInvitedByUser(userService.get(invitedByUserId));
        invitation.setEmail(normalizedEmail);
        invitation.setRole(role);
        invitation.setTokenHash(hashToken(token));
        invitation.setStatus(OrganizationInvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(INVITATION_TTL));
        invitation = invitationRepository.save(invitation);
        invitationDeliveryGateway.sendInvitation(invitation, token);
        return new CreatedInvitation(invitation, token);
    }

    @Transactional
    public List<OrganizationInvitation> invitationsForOrganization(UUID organizationId) {
        organizationService.get(organizationId);
        return invitationRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(this::expireIfNeeded)
                .toList();
    }

    @Transactional
    public OrganizationInvitation revokeInvitation(UUID organizationId, UUID invitationId) {
        OrganizationInvitation invitation = invitationForOrganization(organizationId, invitationId);
        if (invitation.getStatus() != OrganizationInvitationStatus.PENDING) {
            throw new IllegalArgumentException("Only pending invitations can be revoked");
        }
        invitation.setStatus(OrganizationInvitationStatus.REVOKED);
        invitation.setRevokedAt(Instant.now());
        return invitationRepository.save(invitation);
    }

    @Transactional
    public CreatedInvitation resendInvitation(UUID organizationId, UUID invitationId) {
        OrganizationInvitation invitation = invitationForOrganization(organizationId, invitationId);
        if (invitation.getStatus() == OrganizationInvitationStatus.ACCEPTED) {
            throw new IllegalArgumentException("Accepted invitations cannot be resent");
        }
        if (invitation.getStatus() == OrganizationInvitationStatus.REVOKED) {
            throw new IllegalArgumentException("Revoked invitations cannot be resent");
        }

        String token = generateToken();
        invitation.setTokenHash(hashToken(token));
        invitation.setStatus(OrganizationInvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(INVITATION_TTL));
        invitation.setRevokedAt(null);
        invitation = invitationRepository.save(invitation);
        invitationDeliveryGateway.sendInvitation(invitation, token);
        return new CreatedInvitation(invitation, token);
    }

    @Transactional
    public OrganizationInvitation invitationForToken(String token) {
        return expireIfNeeded(invitationRepository.findByTokenHash(hashToken(token))
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found or no longer active")));
    }

    @Transactional
    public OrganizationInvitation acceptInvitation(String token, AppUser user) {
        OrganizationInvitation invitation = invitationForToken(token);
        if (invitation.getStatus() != OrganizationInvitationStatus.PENDING) {
            throw new IllegalArgumentException("Invitation is no longer active");
        }
        if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("Invitation email does not match the authenticated user");
        }
        userService.addMembership(invitation.getOrganization().getId(), user.getId(), invitation.getRole());
        invitation.setStatus(OrganizationInvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitation.setAcceptedByUser(user);
        return invitationRepository.save(invitation);
    }

    @Transactional
    public OrganizationInvitation invitationForOrganization(UUID organizationId, UUID invitationId) {
        OrganizationInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown invitation: " + invitationId));
        if (!invitation.getOrganization().getId().equals(organizationId)) {
            throw new IllegalArgumentException("Invitation does not belong to organization " + organizationId);
        }
        return expireIfNeeded(invitation);
    }

    private void expireStaleInvitations(UUID organizationId) {
        invitationRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).forEach(this::expireIfNeeded);
    }

    private OrganizationInvitation expireIfNeeded(OrganizationInvitation invitation) {
        if (invitation.getStatus() == OrganizationInvitationStatus.PENDING
                && invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(OrganizationInvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
        }
        return invitation;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String generateToken() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record CreatedInvitation(OrganizationInvitation invitation, String rawToken) {
    }
}
