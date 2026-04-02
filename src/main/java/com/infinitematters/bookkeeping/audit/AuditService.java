package com.infinitematters.bookkeeping.audit;

import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditEventRepository repository;
    private final RequestIdentityService requestIdentityService;
    private final UserService userService;
    private final OrganizationService organizationService;

    public AuditService(AuditEventRepository repository,
                        RequestIdentityService requestIdentityService,
                        UserService userService,
                        OrganizationService organizationService) {
        this.repository = repository;
        this.requestIdentityService = requestIdentityService;
        this.userService = userService;
        this.organizationService = organizationService;
    }

    @Transactional
    public void record(UUID organizationId, String eventType, String entityType, String entityId, String details) {
        AuditEvent event = new AuditEvent();
        if (organizationId != null) {
            Organization organization = organizationService.get(organizationId);
            event.setOrganization(organization);
        }

        requestIdentityService.currentUserId()
                .map(userService::get)
                .ifPresent(event::setActorUser);

        event.setEventType(eventType);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDetails(details);
        repository.save(event);
    }

    @Transactional
    public void recordForUser(UUID actorUserId,
                              UUID organizationId,
                              String eventType,
                              String entityType,
                              String entityId,
                              String details) {
        AuditEvent event = new AuditEvent();
        if (organizationId != null) {
            Organization organization = organizationService.get(organizationId);
            event.setOrganization(organization);
        }
        AppUser actor = userService.get(actorUserId);
        event.setActorUser(actor);
        event.setEventType(eventType);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDetails(details);
        repository.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEventSummary> listForOrganization(UUID organizationId) {
        organizationService.get(organizationId);
        return repository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditEventSummary> listForCurrentUserSecurity(UUID userId) {
        return repository.findByActorUserIdAndOrganizationIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .filter(event -> event.getEventType().startsWith("AUTH_")
                        || event.getEventType().startsWith("PASSWORD_RESET"))
                .map(this::toSummary)
                .toList();
    }

    private AuditEventSummary toSummary(AuditEvent event) {
        return new AuditEventSummary(
                event.getId(),
                event.getOrganization() != null ? event.getOrganization().getId() : null,
                event.getActorUser() != null ? event.getActorUser().getId() : null,
                event.getEventType(),
                event.getEntityType(),
                event.getEntityId(),
                event.getDetails(),
                event.getCreatedAt());
    }
}
