package com.infinitematters.bookkeeping.organization;

import com.infinitematters.bookkeeping.users.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "period_close_playbook_items")
public class PeriodClosePlaybookItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_item_id", nullable = false)
    private OrganizationCloseTemplateItem templateItem;

    @Column(name = "period_month", nullable = false, length = 7)
    private String month;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_user_id")
    private AppUser assigneeUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_user_id")
    private AppUser approverUser;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_user_id")
    private AppUser completedByUser;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private AppUser approvedByUser;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public OrganizationCloseTemplateItem getTemplateItem() {
        return templateItem;
    }

    public void setTemplateItem(OrganizationCloseTemplateItem templateItem) {
        this.templateItem = templateItem;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public AppUser getAssigneeUser() {
        return assigneeUser;
    }

    public void setAssigneeUser(AppUser assigneeUser) {
        this.assigneeUser = assigneeUser;
    }

    public AppUser getApproverUser() {
        return approverUser;
    }

    public void setApproverUser(AppUser approverUser) {
        this.approverUser = approverUser;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public AppUser getCompletedByUser() {
        return completedByUser;
    }

    public void setCompletedByUser(AppUser completedByUser) {
        this.completedByUser = completedByUser;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public AppUser getApprovedByUser() {
        return approvedByUser;
    }

    public void setApprovedByUser(AppUser approvedByUser) {
        this.approvedByUser = approvedByUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
