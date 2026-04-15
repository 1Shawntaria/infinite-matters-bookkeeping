package com.infinitematters.bookkeeping.transactions;

import com.infinitematters.bookkeeping.domain.Category;
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
import java.util.UUID;

@Entity
@Table(name = "categorization_decisions")
public class CategorizationDecision {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private BookkeepingTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_category", nullable = false, length = 64)
    private Category proposedCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_category", length = 64)
    private Category finalCategory;

    @Column(nullable = false, length = 32)
    private String route;

    @Column(name = "confidence_score", nullable = false)
    private double confidenceScore;

    @Column(name = "confidence_reason", nullable = false, length = 500)
    private String confidenceReason;

    @Column(nullable = false, length = 1000)
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DecisionStatus status;

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

    public BookkeepingTransaction getTransaction() {
        return transaction;
    }

    public void setTransaction(BookkeepingTransaction transaction) {
        this.transaction = transaction;
    }

    public Category getProposedCategory() {
        return proposedCategory;
    }

    public void setProposedCategory(Category proposedCategory) {
        this.proposedCategory = proposedCategory;
    }

    public Category getFinalCategory() {
        return finalCategory;
    }

    public void setFinalCategory(Category finalCategory) {
        this.finalCategory = finalCategory;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getConfidenceReason() {
        return confidenceReason;
    }

    public void setConfidenceReason(String confidenceReason) {
        this.confidenceReason = confidenceReason;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public DecisionStatus getStatus() {
        return status;
    }

    public void setStatus(DecisionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
