package com.loveproof.testimonials.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A single-use, expiring token for email verification or password reset.
 *
 * <p>The token itself is stored as a SHA-256 hash. The plaintext exists only
 * in the emailed link, so a leak of this table does not hand over working
 * links.
 */
@Entity
@Table(name = "auth_token")
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 16)
    private AuthTokenPurpose purpose;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** For JPA. */
    protected AuthToken() {
        // Required by the persistence provider.
    }

    /**
     * Creates a token.
     *
     * @param userId    the account the token belongs to
     * @param purpose   what the token is for
     * @param tokenHash the SHA-256 hash of the token
     * @param expiresAt when the token stops working
     */
    public AuthToken(UUID userId, AuthTokenPurpose purpose, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AuthTokenPurpose getPurpose() {
        return purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Whether the token may still be used at the given moment.
     *
     * @param now the current time
     * @return {@code true} when the token is unused and unexpired
     */
    public boolean isUsableAt(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
