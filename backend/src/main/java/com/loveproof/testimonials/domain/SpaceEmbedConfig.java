package com.loveproof.testimonials.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * How one Space's embed looks and behaves.
 *
 * <p>There is one configuration per Space. It lives on the server rather than
 * inside the snippet so that changing the layout does not mean asking the
 * owner to paste a new snippet into their website (PRD §4.8).
 *
 * <p>The premium options are stored whether or not the account can still
 * choose them. A downgraded owner keeps the embed they already had; only
 * *changing* to a premium option is refused. The rule is therefore about
 * choosing, not about holding, which is why the check lives in the update
 * path and not in the read path.
 */
@Entity
@Table(name = "space_embed_config")
public class SpaceEmbedConfig {

    @Id
    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    @Column(name = "layout", nullable = false, length = 32)
    private String layout = "masonry";

    @Column(name = "theme_mode", nullable = false, length = 16)
    private String themeMode = "light";

    /** Null when the embed uses its own default background. */
    @Column(name = "background_color", length = 7)
    private String backgroundColor;

    @Column(name = "animation", nullable = false)
    private boolean animation;

    @Column(name = "embed_token", nullable = false, length = 64)
    private String embedToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** For JPA. */
    protected SpaceEmbedConfig() {
        // Required by the persistence provider.
    }

    /**
     * Creates a configuration.
     *
     * @param spaceId   the Space
     * @param embedToken the public token the embed script uses
     */
    public SpaceEmbedConfig(UUID spaceId, String embedToken) {
        this.spaceId = spaceId;
        this.embedToken = embedToken;
    }

    public UUID getSpaceId() {
        return spaceId;
    }

    public String getLayout() {
        return layout;
    }

    public void setLayout(String layout) {
        this.layout = layout;
    }

    public String getThemeMode() {
        return themeMode;
    }

    public void setThemeMode(String themeMode) {
        this.themeMode = themeMode;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public boolean isAnimation() {
        return animation;
    }

    public void setAnimation(boolean animation) {
        this.animation = animation;
    }

    public String getEmbedToken() {
        return embedToken;
    }

    public void setEmbedToken(String embedToken) {
        this.embedToken = embedToken;
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
