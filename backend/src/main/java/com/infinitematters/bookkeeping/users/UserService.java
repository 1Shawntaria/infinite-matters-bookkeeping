package com.infinitematters.bookkeeping.users;

import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.security.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    private final AppUserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository userRepository,
                       OrganizationMembershipRepository membershipRepository,
                       OrganizationService organizationService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser create(String email, String fullName, String password) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new DuplicateUserException(email);
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    public AppUser get(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + userId));
    }

    public OrganizationMembership addMembership(UUID organizationId, UUID userId, UserRole role) {
        Organization organization = organizationService.get(organizationId);
        AppUser user = get(userId);

        return membershipRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .map(existingMembership -> {
                    existingMembership.setRole(role);
                    return membershipRepository.save(existingMembership);
                })
                .orElseGet(() -> {
                    OrganizationMembership membership = new OrganizationMembership();
                    membership.setOrganization(organization);
                    membership.setUser(user);
                    membership.setRole(role);
                    return membershipRepository.save(membership);
                });
    }

    public List<OrganizationMembership> membershipsForUser(UUID userId) {
        return membershipRepository.findByUserId(userId);
    }

    public List<OrganizationMembership> membershipsForOrganization(UUID organizationId) {
        organizationService.get(organizationId);
        return membershipRepository.findByOrganizationIdOrderByCreatedAtAsc(organizationId);
    }

    public OrganizationMembership addMembershipByEmail(UUID organizationId, String email, UserRole role) {
        return addMembership(organizationId, getByEmail(email).getId(), role);
    }

    public boolean hasAccess(UUID organizationId, UUID userId) {
        return membershipRepository.findByOrganizationIdAndUserId(organizationId, userId).isPresent();
    }

    public AppUser getByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user email: " + email));
    }

    public Optional<AppUser> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    public AppUser authenticate(String email, String password) {
        AppUser user;
        try {
            user = getByEmail(email);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid email or password");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AccessDeniedException("Invalid email or password");
        }
        return user;
    }

    public UserRole roleForOrganization(UUID organizationId, UUID userId) {
        return membershipRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .map(OrganizationMembership::getRole)
                .orElseThrow(() -> new IllegalArgumentException("No membership for user " + userId));
    }

    public List<AppUser> membersForOrganizationWithRoles(UUID organizationId, List<UserRole> roles) {
        return membershipRepository.findByOrganizationIdAndRoleIn(organizationId, roles).stream()
                .map(OrganizationMembership::getUser)
                .toList();
    }

    public OrganizationMembership updateMembershipRole(UUID organizationId, UUID membershipId, UserRole role) {
        OrganizationMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown membership: " + membershipId));
        if (!membership.getOrganization().getId().equals(organizationId)) {
            throw new IllegalArgumentException("Membership does not belong to organization " + organizationId);
        }
        if (membership.getRole() == UserRole.OWNER || role == UserRole.OWNER) {
            throw new IllegalArgumentException("Owner memberships cannot be reassigned through this endpoint");
        }
        membership.setRole(role);
        return membershipRepository.save(membership);
    }

    public AppUser updatePassword(UUID userId, String newPassword) {
        AppUser user = get(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }
}
