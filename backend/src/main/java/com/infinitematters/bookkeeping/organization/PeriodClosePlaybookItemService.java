package com.infinitematters.bookkeeping.organization;

import com.infinitematters.bookkeeping.security.AccessDeniedException;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.users.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PeriodClosePlaybookItemService {
    private final OrganizationService organizationService;
    private final OrganizationCloseTemplateItemService templateItemService;
    private final PeriodClosePlaybookItemRepository repository;
    private final UserService userService;

    public PeriodClosePlaybookItemService(OrganizationService organizationService,
                                          OrganizationCloseTemplateItemService templateItemService,
                                          PeriodClosePlaybookItemRepository repository,
                                          UserService userService) {
        this.organizationService = organizationService;
        this.templateItemService = templateItemService;
        this.repository = repository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<PeriodClosePlaybookItemView> list(UUID organizationId, YearMonth month) {
        organizationService.get(organizationId);
        List<OrganizationCloseTemplateItem> templates = templateItemService.list(organizationId);
        Map<UUID, PeriodClosePlaybookItem> statesByTemplateId = repository
                .findByOrganizationIdAndMonthOrderByCreatedAtAsc(organizationId, month.toString())
                .stream()
                .collect(Collectors.toMap(item -> item.getTemplateItem().getId(), Function.identity()));

        return templates.stream()
                .sorted(Comparator.comparingInt(OrganizationCloseTemplateItem::getSortOrder))
                .map(template -> PeriodClosePlaybookItemView.from(template, statesByTemplateId.get(template.getId())))
                .toList();
    }

    @Transactional
    public PeriodClosePlaybookItemView assign(UUID organizationId,
                                              UUID templateItemId,
                                              YearMonth month,
                                              UUID assigneeUserId,
                                              UUID approverUserId) {
        OrganizationCloseTemplateItem templateItem = templateItemService.get(organizationId, templateItemId);
        PeriodClosePlaybookItem item = loadOrCreate(organizationId, templateItem, month);
        item.setAssigneeUser(resolveWorkspaceUser(organizationId, assigneeUserId));
        item.setApproverUser(resolveWorkspaceUser(organizationId, approverUserId));
        return PeriodClosePlaybookItemView.from(templateItem, repository.save(item));
    }

    @Transactional
    public PeriodClosePlaybookItemView markComplete(UUID organizationId,
                                                    UUID templateItemId,
                                                    YearMonth month,
                                                    boolean completed,
                                                    UUID actorUserId) {
        OrganizationCloseTemplateItem templateItem = templateItemService.get(organizationId, templateItemId);
        PeriodClosePlaybookItem item = loadOrCreate(organizationId, templateItem, month);
        assertCompletionAccess(organizationId, item, actorUserId);
        if (completed) {
            item.setCompletedAt(Instant.now());
            item.setCompletedByUser(userService.get(actorUserId));
        } else {
            item.setCompletedAt(null);
            item.setCompletedByUser(null);
            item.setApprovedAt(null);
            item.setApprovedByUser(null);
        }
        return PeriodClosePlaybookItemView.from(templateItem, repository.save(item));
    }

    @Transactional
    public PeriodClosePlaybookItemView markApproved(UUID organizationId,
                                                    UUID templateItemId,
                                                    YearMonth month,
                                                    boolean approved,
                                                    UUID actorUserId) {
        OrganizationCloseTemplateItem templateItem = templateItemService.get(organizationId, templateItemId);
        PeriodClosePlaybookItem item = loadOrCreate(organizationId, templateItem, month);
        assertApprovalAccess(organizationId, item, actorUserId);
        if (item.getCompletedAt() == null) {
            throw new IllegalArgumentException("Playbook item must be completed before it can be approved");
        }
        if (approved) {
            item.setApprovedAt(Instant.now());
            item.setApprovedByUser(userService.get(actorUserId));
        } else {
            item.setApprovedAt(null);
            item.setApprovedByUser(null);
        }
        return PeriodClosePlaybookItemView.from(templateItem, repository.save(item));
    }

    @Transactional(readOnly = true)
    public boolean allRequiredItemsSatisfied(UUID organizationId, YearMonth month) {
        Organization organization = organizationService.get(organizationId);
        if (!organization.isRequireTemplateCompletionBeforeClose()) {
            return true;
        }
        return list(organizationId, month).stream().allMatch(PeriodClosePlaybookItemView::isSatisfied);
    }

    @Transactional(readOnly = true)
    public long outstandingRequiredItems(UUID organizationId, YearMonth month) {
        return list(organizationId, month).stream().filter(item -> !item.isSatisfied()).count();
    }

    private PeriodClosePlaybookItem loadOrCreate(UUID organizationId,
                                                 OrganizationCloseTemplateItem templateItem,
                                                 YearMonth month) {
        return repository.findByOrganizationIdAndTemplateItemIdAndMonth(
                        organizationId,
                        templateItem.getId(),
                        month.toString())
                .orElseGet(() -> {
                    PeriodClosePlaybookItem created = new PeriodClosePlaybookItem();
                    created.setOrganization(organizationService.get(organizationId));
                    created.setTemplateItem(templateItem);
                    created.setMonth(month.toString());
                    return created;
                });
    }

    private AppUser resolveWorkspaceUser(UUID organizationId, UUID userId) {
        if (userId == null) {
            return null;
        }
        if (!userService.hasAccess(organizationId, userId)) {
            throw new IllegalArgumentException("Assigned user does not belong to this organization");
        }
        return userService.get(userId);
    }

    private void assertCompletionAccess(UUID organizationId, PeriodClosePlaybookItem item, UUID actorUserId) {
        UserRole actorRole = userService.roleForOrganization(organizationId, actorUserId);
        if (actorRole == UserRole.OWNER || actorRole == UserRole.ADMIN) {
            return;
        }
        if (item.getAssigneeUser() == null || !item.getAssigneeUser().getId().equals(actorUserId)) {
            throw new AccessDeniedException("Only the assigned operator or an admin can complete this playbook item");
        }
    }

    private void assertApprovalAccess(UUID organizationId, PeriodClosePlaybookItem item, UUID actorUserId) {
        UserRole actorRole = userService.roleForOrganization(organizationId, actorUserId);
        if (actorRole == UserRole.OWNER || actorRole == UserRole.ADMIN) {
            return;
        }
        if (item.getApproverUser() == null || !item.getApproverUser().getId().equals(actorUserId)) {
            throw new AccessDeniedException("Only the assigned approver or an admin can approve this playbook item");
        }
    }

    public record PeriodClosePlaybookItemView(
            UUID id,
            UUID templateItemId,
            String month,
            String label,
            String guidance,
            int sortOrder,
            UserAssignmentSummary assignee,
            UserAssignmentSummary approver,
            Instant completedAt,
            UserAssignmentSummary completedBy,
            Instant approvedAt,
            UserAssignmentSummary approvedBy,
            Instant createdAt) {

        public boolean isCompleted() {
            return completedAt != null;
        }

        public boolean isApproved() {
            return approvedAt != null;
        }

        public boolean isSatisfied() {
            return completedAt != null && (approver == null || approvedAt != null);
        }

        static PeriodClosePlaybookItemView from(OrganizationCloseTemplateItem template,
                                                PeriodClosePlaybookItem state) {
            return new PeriodClosePlaybookItemView(
                    state != null ? state.getId() : null,
                    template.getId(),
                    state != null ? state.getMonth() : null,
                    template.getLabel(),
                    template.getGuidance(),
                    template.getSortOrder(),
                    summarize(state != null ? state.getAssigneeUser() : null),
                    summarize(state != null ? state.getApproverUser() : null),
                    state != null ? state.getCompletedAt() : null,
                    summarize(state != null ? state.getCompletedByUser() : null),
                    state != null ? state.getApprovedAt() : null,
                    summarize(state != null ? state.getApprovedByUser() : null),
                    state != null ? state.getCreatedAt() : template.getCreatedAt());
        }

        private static UserAssignmentSummary summarize(AppUser user) {
            if (user == null) {
                return null;
            }
            return new UserAssignmentSummary(
                    user.getId(),
                    user.getEmail(),
                    Objects.requireNonNullElse(user.getFullName(), user.getEmail()));
        }
    }

    public record UserAssignmentSummary(UUID id, String email, String fullName) {
    }
}
