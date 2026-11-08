package com.loveproof.testimonials.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only audit entry recording one moderation decision.
 *
 * <p>Maps the {@code moderation_action} table from {@code docs/data-model.md} §2.3. Rows are
 * inserted and never updated or deleted.
 *
 * <p>The actor's username is denormalised into {@code admin_username} so the trail survives the
 * removal of the administrator account.
 */
@Entity
@Table(name = "moderation_action")
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "testimonial_id", nullable = false, updatable = false)
    private UUID testimonialId;

    @Column(name = "admin_user_id", updatable = false)
    private UUID adminUserId;

    @Column(name = "admin_username", nullable = false, length = 50, updatable = false)
    private String adminUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16, updatable = false)
    private ModerationActionType action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 16, updatable = false)
    private TestimonialStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 16, updatable = false)
    private TestimonialStatus newStatus;

    @Column(name = "reason", length = 255, updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Creates an empty audit entry for JPA.
     */
    protected ModerationAction() {
        // for JPA
    }

    /**
     * Creates an audit entry.
     *
     * @param testimonialId  the testimonial acted on
     * @param adminUserId   the acting administrator, or {@code null} when the account was removed
     * @param adminUsername the denormalised username of the actor
     * @param action        the kind of action
     * @param previousStatus the status before the action, or {@code null}
     * @param newStatus     the status after the action, or {@code null}
     * @param reason        the optional reason
     * @param createdAt     the action timestamp
     */
    public ModerationAction(
            UUID testimonialId,
            UUID adminUserId,
            String adminUsername,
            ModerationActionType action,
            TestimonialStatus previousStatus,
            TestimonialStatus newStatus,
            String reason,
            Instant createdAt) {
        this.testimonialId = Objects.requireNonNull(testimonialId, "testimonialId must not be null");
        this.adminUserId = adminUserId;
        this.adminUsername = Objects.requireNonNull(adminUsername, "adminUsername must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public Long getId() {
        return id;
    }

    public UUID getTestimonialId() {
        return testimonialId;
    }

    public UUID getAdminUserId() {
        return adminUserId;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public ModerationActionType getAction() {
        return action;
    }

    public TestimonialStatus getPreviousStatus() {
        return previousStatus;
    }

    public TestimonialStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
