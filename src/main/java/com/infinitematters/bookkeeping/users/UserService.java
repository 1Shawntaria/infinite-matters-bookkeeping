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

    public AppUser updatePassword(UUID userId, String newPassword) {
        AppUser user = get(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }
}
