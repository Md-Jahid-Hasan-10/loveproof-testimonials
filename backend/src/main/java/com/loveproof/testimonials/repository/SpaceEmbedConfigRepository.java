package com.loveproof.testimonials.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loveproof.testimonials.domain.SpaceEmbedConfig;

/**
 * The embed configuration store.
 */
public interface SpaceEmbedConfigRepository extends JpaRepository<SpaceEmbedConfig, UUID> {

    /**
     * Finds a configuration by its public token.
     *
     * @param embedToken the token
     * @return the configuration, or empty
     */
    Optional<SpaceEmbedConfig> findByEmbedToken(String embedToken);

    /**
     * Whether a token is already taken.
     *
     * @param embedToken the token
     * @return {@code true} when it is taken
     */
    boolean existsByEmbedToken(String embedToken);
}
