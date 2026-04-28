package com.infinitematters.bookkeeping.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false, length = 32)
    private PlanTier planTier;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "invitation_ttl_days", nullable = false)
    private int invitationTtlDays = 7;

    @Column(name = "close_materiality_threshold", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal closeMaterialityThreshold = new java.math.BigDecimal("500.00");

    @Column(name = "minimum_close_notes_required", nullable = false)
    private int minimumCloseNotesRequired = 1;

    @Column(name = "require_signoff_before_close", nullable = false)
    private boolean requireSignoffBeforeClose = true;

    @Column(name = "minimum_signoff_count", nullable = false)
    private int minimumSignoffCount = 1;

    @Column(name = "require_owner_signoff_before_close", nullable = false)
    private boolean requireOwnerSignoffBeforeClose = false;

    @Column(name = "require_template_completion_before_close", nullable = false)
    private boolean requireTemplateCompletionBeforeClose = true;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PlanTier getPlanTier() {
        return planTier;
    }

    public void setPlanTier(PlanTier planTier) {
        this.planTier = planTier;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getInvitationTtlDays() {
        return invitationTtlDays;
    }

    public void setInvitationTtlDays(int invitationTtlDays) {
        this.invitationTtlDays = invitationTtlDays;
    }

    public java.math.BigDecimal getCloseMaterialityThreshold() {
        return closeMaterialityThreshold;
    }

    public void setCloseMaterialityThreshold(java.math.BigDecimal closeMaterialityThreshold) {
        this.closeMaterialityThreshold = closeMaterialityThreshold;
    }

    public int getMinimumCloseNotesRequired() {
        return minimumCloseNotesRequired;
    }

    public void setMinimumCloseNotesRequired(int minimumCloseNotesRequired) {
        this.minimumCloseNotesRequired = minimumCloseNotesRequired;
    }

    public boolean isRequireSignoffBeforeClose() {
        return requireSignoffBeforeClose;
    }

    public void setRequireSignoffBeforeClose(boolean requireSignoffBeforeClose) {
        this.requireSignoffBeforeClose = requireSignoffBeforeClose;
    }

    public int getMinimumSignoffCount() {
        return minimumSignoffCount;
    }

    public void setMinimumSignoffCount(int minimumSignoffCount) {
        this.minimumSignoffCount = minimumSignoffCount;
    }

    public boolean isRequireOwnerSignoffBeforeClose() {
        return requireOwnerSignoffBeforeClose;
    }

    public void setRequireOwnerSignoffBeforeClose(boolean requireOwnerSignoffBeforeClose) {
        this.requireOwnerSignoffBeforeClose = requireOwnerSignoffBeforeClose;
    }

    public boolean isRequireTemplateCompletionBeforeClose() {
        return requireTemplateCompletionBeforeClose;
    }

    public void setRequireTemplateCompletionBeforeClose(boolean requireTemplateCompletionBeforeClose) {
        this.requireTemplateCompletionBeforeClose = requireTemplateCompletionBeforeClose;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
