package com.loveproof.testimonials.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loveproof.testimonials.domain.AuthToken;

/**
 * The single-use token store, used for email verification and password reset.
 */
public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    /**
     * Finds a token by its hash.
     *
     * @param tokenHash the SHA-256 hash of the token
     * @return the token, or empty
     */
    Optional<AuthToken> findByTokenHash(String tokenHash);
}
