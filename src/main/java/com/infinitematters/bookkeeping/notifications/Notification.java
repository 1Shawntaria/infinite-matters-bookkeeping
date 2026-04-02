package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "workflow_task_id")
    private WorkflowTask workflowTask;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCategory category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_state", nullable = false, length = 16)
    private NotificationDeliveryState deliveryState;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "reference_type", length = 64)
    private String referenceType;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "last_failure_code", length = 64)
    private String lastFailureCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "dead_letter_resolution_status", length = 32)
    private DeadLetterResolutionStatus deadLetterResolutionStatus;

    @Column(name = "dead_letter_resolution_note", length = 1000)
    private String deadLetterResolutionNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "dead_letter_resolution_reason_code", length = 64)
    private DeadLetterResolutionReasonCode deadLetterResolutionReasonCode;

    @Column(name = "dead_letter_resolved_at")
    private Instant deadLetterResolvedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dead_letter_resolved_by_user_id")
    private AppUser deadLetterResolvedByUser;

    @Column(name = "provider_name", length = 128)
    private String providerName;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

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

    public WorkflowTask getWorkflowTask() {
        return workflowTask;
    }

    public void setWorkflowTask(WorkflowTask workflowTask) {
        this.workflowTask = workflowTask;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public void setCategory(NotificationCategory category) {
        this.category = category;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public NotificationDeliveryState getDeliveryState() {
        return deliveryState;
    }

    public void setDeliveryState(NotificationDeliveryState deliveryState) {
        this.deliveryState = deliveryState;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public void setScheduledFor(Instant scheduledFor) {
        this.scheduledFor = scheduledFor;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAttemptedAt() {
        return lastAttemptedAt;
    }

    public void setLastAttemptedAt(Instant lastAttemptedAt) {
        this.lastAttemptedAt = lastAttemptedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getLastFailureCode() {
        return lastFailureCode;
    }

    public void setLastFailureCode(String lastFailureCode) {
        this.lastFailureCode = lastFailureCode;
    }

    public DeadLetterResolutionStatus getDeadLetterResolutionStatus() {
        return deadLetterResolutionStatus;
    }

    public void setDeadLetterResolutionStatus(DeadLetterResolutionStatus deadLetterResolutionStatus) {
        this.deadLetterResolutionStatus = deadLetterResolutionStatus;
    }

    public String getDeadLetterResolutionNote() {
        return deadLetterResolutionNote;
    }

    public void setDeadLetterResolutionNote(String deadLetterResolutionNote) {
        this.deadLetterResolutionNote = deadLetterResolutionNote;
    }

    public DeadLetterResolutionReasonCode getDeadLetterResolutionReasonCode() {
        return deadLetterResolutionReasonCode;
    }

    public void setDeadLetterResolutionReasonCode(DeadLetterResolutionReasonCode deadLetterResolutionReasonCode) {
        this.deadLetterResolutionReasonCode = deadLetterResolutionReasonCode;
    }

    public Instant getDeadLetterResolvedAt() {
        return deadLetterResolvedAt;
    }

    public void setDeadLetterResolvedAt(Instant deadLetterResolvedAt) {
        this.deadLetterResolvedAt = deadLetterResolvedAt;
    }

    public AppUser getDeadLetterResolvedByUser() {
        return deadLetterResolvedByUser;
    }

    public void setDeadLetterResolvedByUser(AppUser deadLetterResolvedByUser) {
        this.deadLetterResolvedByUser = deadLetterResolvedByUser;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public String resolvedRecipientEmail() {
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            return recipientEmail;
        }
        return user != null ? user.getEmail() : null;
    }
}
