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

/**
 * An account permitted to moderate testimonials.
 *
 * <p>Maps the {@code admin_user} table from {@code docs/data-model.md} §2.2. The password is stored
 * only as a BCrypt hash; the hash is never returned by any endpoint and never logged.
 */
@Entity
@Table(name = "admin_user")
public class AdminUser {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Stored lower-cased so the unique index is case-insensitive. */
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private AdminRole role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * Creates an empty administrator for JPA.
     */
    protected AdminUser() {
        // for JPA
    }

    /**
     * Creates an administrator.
     *
     * @param id           the identifier
     * @param username     the username, stored lower-cased
     * @param passwordHash the BCrypt hash, cost 10 or above
     * @param displayName  the name shown in the admin header
     * @param role         the role
     * @param createdAt    the creation timestamp
     */
    public AdminUser(
            UUID id,
            String username,
            String passwordHash,
            String displayName,
            AdminRole role,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null").toLowerCase();
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.enabled = true;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * Records a successful authentication.
     *
     * @param at the login timestamp
     */
    public void recordLogin(Instant at) {
        this.lastLoginAt = Objects.requireNonNull(at, "at must not be null");
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AdminRole getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
