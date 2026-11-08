package com.loveproof.testimonials.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A collection page.
 *
 * <p>A Space is owned by exactly one {@link AppUser}, has its own public URL
 * ({@code /s/{slug}}), its own form configuration and its own testimonials.
 *
 * <p>The optional fields each carry two flags: whether they are shown at all
 * and whether they must be answered. The fixed fields -- name and email -- are
 * always shown and always required, so they are not stored.
 */
@Entity
@Table(name = "space")
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "subtitle", length = 255)
    private String subtitle;

    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "field_company", nullable = false)
    private boolean fieldCompany;

    @Column(name = "field_company_required", nullable = false)
    private boolean fieldCompanyRequired;

    @Column(name = "field_social", nullable = false)
    private boolean fieldSocial;

    @Column(name = "field_social_required", nullable = false)
    private boolean fieldSocialRequired;

    @Column(name = "field_website", nullable = false)
    private boolean fieldWebsite;

    @Column(name = "field_website_required", nullable = false)
    private boolean fieldWebsiteRequired;

    @Column(name = "field_job_title", nullable = false)
    private boolean fieldJobTitle;

    @Column(name = "field_job_title_required", nullable = false)
    private boolean fieldJobTitleRequired;

    @Column(name = "rating_enabled", nullable = false)
    private boolean ratingEnabled;

    @Column(name = "theme", nullable = false, length = 32)
    private String theme = "basic";

    /** Set once the Space has its first submission; the slug is then fixed. */
    @Column(name = "slug_locked", nullable = false)
    private boolean slugLocked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** For JPA. */
    protected Space() {
        // Required by the persistence provider.
    }

    /**
     * Creates a Space.
     *
     * @param ownerId the owning account
     * @param title   the Space title
     * @param slug    the public URL segment
     */
    public Space(UUID ownerId, String title, String slug) {
        this.ownerId = ownerId;
        this.title = title;
        this.slug = slug;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getSlug() {
        return slug;
    }

    /**
     * Changes the slug.
     *
     * <p>The caller is responsible for checking that the slug is not locked;
     * the database does not enforce that, because the rule is a product rule
     * rather than a storage invariant.
     *
     * @param slug the new public URL segment
     */
    public void setSlug(String slug) {
        this.slug = slug;
    }

    public boolean isSlugLocked() {
        return slugLocked;
    }

    public void setSlugLocked(boolean slugLocked) {
        this.slugLocked = slugLocked;
    }

    public boolean isFieldCompany() {
        return fieldCompany;
    }

    public void setFieldCompany(boolean fieldCompany) {
        this.fieldCompany = fieldCompany;
    }

    public boolean isFieldCompanyRequired() {
        return fieldCompanyRequired;
    }

    public void setFieldCompanyRequired(boolean fieldCompanyRequired) {
        this.fieldCompanyRequired = fieldCompanyRequired;
    }

    public boolean isFieldSocial() {
        return fieldSocial;
    }

    public void setFieldSocial(boolean fieldSocial) {
        this.fieldSocial = fieldSocial;
    }

    public boolean isFieldSocialRequired() {
        return fieldSocialRequired;
    }

    public void setFieldSocialRequired(boolean fieldSocialRequired) {
        this.fieldSocialRequired = fieldSocialRequired;
    }

    public boolean isFieldWebsite() {
        return fieldWebsite;
    }

    public void setFieldWebsite(boolean fieldWebsite) {
        this.fieldWebsite = fieldWebsite;
    }

    public boolean isFieldWebsiteRequired() {
        return fieldWebsiteRequired;
    }

    public void setFieldWebsiteRequired(boolean fieldWebsiteRequired) {
        this.fieldWebsiteRequired = fieldWebsiteRequired;
    }

    public boolean isFieldJobTitle() {
        return fieldJobTitle;
    }

    public void setFieldJobTitle(boolean fieldJobTitle) {
        this.fieldJobTitle = fieldJobTitle;
    }

    public boolean isFieldJobTitleRequired() {
        return fieldJobTitleRequired;
    }

    public void setFieldJobTitleRequired(boolean fieldJobTitleRequired) {
        this.fieldJobTitleRequired = fieldJobTitleRequired;
    }

    public boolean isRatingEnabled() {
        return ratingEnabled;
    }

    public void setRatingEnabled(boolean ratingEnabled) {
        this.ratingEnabled = ratingEnabled;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
