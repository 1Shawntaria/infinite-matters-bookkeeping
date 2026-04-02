package com.infinitematters.bookkeeping.workflows;

import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.notifications.Notification;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransaction;
import com.infinitematters.bookkeeping.users.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "workflow_tasks")
public class WorkflowTask {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transaction_id")
    private BookkeepingTransaction transaction;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "notification_id")
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private WorkflowTaskType taskType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assigned_to_user_id")
    private AppUser assignedToUser;

    @Column(name = "related_period_start")
    private LocalDate relatedPeriodStart;

    @Column(name = "related_period_end")
    private LocalDate relatedPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private WorkflowTaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkflowTaskStatus status;

    @Column(name = "resolution_comment", length = 1000)
    private String resolutionComment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resolved_by_user_id")
    private AppUser resolvedByUser;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
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

    public BookkeepingTransaction getTransaction() {
        return transaction;
    }

    public void setTransaction(BookkeepingTransaction transaction) {
        this.transaction = transaction;
    }

    public Notification getNotification() {
        return notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public WorkflowTaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(WorkflowTaskType taskType) {
        this.taskType = taskType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public AppUser getAssignedToUser() {
        return assignedToUser;
    }

    public void setAssignedToUser(AppUser assignedToUser) {
        this.assignedToUser = assignedToUser;
    }

    public LocalDate getRelatedPeriodStart() {
        return relatedPeriodStart;
    }

    public void setRelatedPeriodStart(LocalDate relatedPeriodStart) {
        this.relatedPeriodStart = relatedPeriodStart;
    }

    public LocalDate getRelatedPeriodEnd() {
        return relatedPeriodEnd;
    }

    public void setRelatedPeriodEnd(LocalDate relatedPeriodEnd) {
        this.relatedPeriodEnd = relatedPeriodEnd;
    }

    public WorkflowTaskPriority getPriority() {
        return priority;
    }

    public void setPriority(WorkflowTaskPriority priority) {
        this.priority = priority;
    }

    public WorkflowTaskStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowTaskStatus status) {
        this.status = status;
    }

    public String getResolutionComment() {
        return resolutionComment;
    }

    public void setResolutionComment(String resolutionComment) {
        this.resolutionComment = resolutionComment;
    }

    public AppUser getResolvedByUser() {
        return resolvedByUser;
    }

    public void setResolvedByUser(AppUser resolvedByUser) {
        this.resolvedByUser = resolvedByUser;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
