package com.loveproof.testimonials.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A submitted testimonial and its moderation state.
 *
 * <p>Maps the {@code testimonial} table exactly as defined in {@code docs/data-model.md} §2.1.
 * Hibernate runs with {@code ddl-auto=validate}, so the mapping and the Flyway migration must agree
 * on every column, type and nullability.
 *
 * <p>Instances are only ever created and mutated inside the service layer; controllers return DTOs
 * and never expose this type.
 *
 * <p>The status transitions live on this entity because they are invariants of the row itself: a
 * rejected testimonial must carry a reason, a pending one must not carry a moderation timestamp, and
 * consent can never be withdrawn.
 */
@Entity
@Table(name = "testimonial")
public class Testimonial {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "role", length = 120)
    private String role;

    @Column(name = "company", length = 120)
    private String company;

    @Column(name = "rating", nullable = false)
    private short rating;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TestimonialStatus status;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;

    @Column(name = "consent_text_version", nullable = false, length = 32)
    private String consentTextVersion;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_fingerprint", nullable = false, length = 64)
    private String contentFingerprint;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "submitter_ip_hash", length = 64)
    private String submitterIpHash;

    /**
     * Creates a pending testimonial.
     *
     * <p>Used by JPA and by tests that need a fully-formed row. Callers in the service layer should
     * prefer the factory there, which also applies the normalisation rules.
     */
    protected Testimonial() {
        // for JPA
    }

    /**
     * Creates a pending testimonial from the raw, already-validated field values.
     *
     * @param id                   the identifier
     * @param name                 the submitter's name, trimmed
     * @param role                 the job title, or {@code null}
     * @param company              the company name, or {@code null}
     * @param rating               the 1-5 rating
     * @param content              the testimonial text, trimmed
     * @param consentTextVersion   the identifier of the consent wording shown to the visitor
     * @param submittedAt          the submission timestamp
     * @param contentFingerprint   the SHA-256 fingerprint backing duplicate detection
     */
    public Testimonial(
            UUID id,
            String name,
            String role,
            String company,
            int rating,
            String content,
            String consentTextVersion,
            Instant submittedAt,
            String contentFingerprint) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.role = role;
        this.company = company;
        this.rating = (short) rating;
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.status = TestimonialStatus.PENDING;
        this.consentGiven = true;
        this.consentTextVersion = Objects.requireNonNull(consentTextVersion, "consentTextVersion must not be null");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        this.updatedAt = submittedAt;
        this.contentFingerprint = Objects.requireNonNull(contentFingerprint, "contentFingerprint must not be null");
    }

    /**
     * Applies a moderation decision that moves the testimonial out of {@link TestimonialStatus#PENDING}.
     *
     * <p>Clears any previous rejection reason so a re-approved testimonial carries no stale reason.
     *
     * @param newStatus    the status to move to
     * @param moderatedBy  the acting administrator, or {@code null} for a system action
     * @param moderatedAt  the decision timestamp
     * @param reason       the rejection reason, required when {@code newStatus} is
     *                     {@link TestimonialStatus#REJECTED} and ignored otherwise
     */
    public void moderate(TestimonialStatus newStatus, UUID moderatedBy, Instant moderatedAt, String reason) {
        if (newStatus == TestimonialStatus.PENDING) {
            throw new IllegalArgumentException("A moderation decision cannot move a testimonial back to PENDING.");
        }
        if (newStatus == TestimonialStatus.REJECTED && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A rejection must carry a reason.");
        }
        this.status = newStatus;
        this.moderatedAt = Objects.requireNonNull(moderatedAt, "moderatedAt must not be null");
        this.moderatedBy = moderatedBy;
        this.rejectionReason = newStatus == TestimonialStatus.REJECTED ? reason : null;
        this.updatedAt = moderatedAt;
    }

    /**
     * Marks the testimonial as soft-deleted.
     *
     * <p>The moderation status is deliberately left untouched: deletion and moderation are orthogonal.
     *
     * @param deletedBy  the acting administrator, or {@code null}
     * @param deletedAt  the deletion timestamp
     */
    public void softDelete(UUID deletedBy, Instant deletedAt) {
        this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt must not be null");
        this.deletedBy = deletedBy;
        this.updatedAt = deletedAt;
    }

    /**
     * Clears the soft-delete marker, returning the testimonial to its previous moderation status.
     *
     * @param restoredAt  the restore timestamp
     */
    public void restore(Instant restoredAt) {
        this.deletedAt = null;
        this.deletedBy = null;
        this.updatedAt = Objects.requireNonNull(restoredAt, "restoredAt must not be null");
    }

    /** @return whether the testimonial has been soft-deleted */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** @return whether the testimonial is visible on the public wall */
    public boolean isLiveAndApproved() {
        return status == TestimonialStatus.APPROVED && deletedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getCompany() {
        return company;
    }

    public short getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }

    public TestimonialStatus getStatus() {
        return status;
    }

    public boolean isConsentGiven() {
        return consentGiven;
    }

    public String getConsentTextVersion() {
        return consentTextVersion;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getModeratedAt() {
        return moderatedAt;
    }

    public UUID getModeratedBy() {
        return moderatedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public String getSubmitterIpHash() {
        return submitterIpHash;
    }

    /**
     * Sets the salted hash of the submitter's IP address.
     *
     * @param submitterIpHash the hash, or {@code null} when the address is unavailable
     */
    public void setSubmitterIpHash(String submitterIpHash) {
        this.submitterIpHash = submitterIpHash;
    }

    /**
     * Marks the row as updated.
     *
     * @param updatedAt the new update timestamp
     */
    public void touch(Instant updatedAt) {
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
