package com.loveproof.testimonials.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A testimonial collected by one {@link Space}.
 *
 * <p>There is no approval step: a submission is visible by default, not on the
 * Wall of Love, and not a favorite. The owner curates it afterwards.
 */
@Entity
@Table(name = "space_testimonial")
public class SpaceTestimonial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "content", nullable = false)
    private String content;

    /** Null when the Space has ratings turned off. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "rating")
    private Integer rating;

    @Column(name = "company", length = 120)
    private String company;

    @Column(name = "social_link", length = 500)
    private String socialLink;

    @Column(name = "website", length = 500)
    private String website;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    /** Consent is optional and never gates display. */
    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    @Column(name = "wall_of_love", nullable = false)
    private boolean wallOfLove;

    @Column(name = "visible", nullable = false)
    private boolean visible = true;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** For JPA. */
    protected SpaceTestimonial() {
        // Required by the persistence provider.
    }

    /**
     * Creates a testimonial.
     *
     * @param spaceId the Space that collected it
     * @param name    the submitter's name
     * @param email   the submitter's email
     * @param content the testimonial text
     */
    public SpaceTestimonial(UUID spaceId, String name, String email, String content) {
        this.spaceId = spaceId;
        this.name = name;
        this.email = email;
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSpaceId() {
        return spaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getSocialLink() {
        return socialLink;
    }

    public void setSocialLink(String socialLink) {
        this.socialLink = socialLink;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public boolean isConsentGiven() {
        return consentGiven;
    }

    public void setConsentGiven(boolean consentGiven) {
        this.consentGiven = consentGiven;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public boolean isWallOfLove() {
        return wallOfLove;
    }

    public void setWallOfLove(boolean wallOfLove) {
        this.wallOfLove = wallOfLove;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
