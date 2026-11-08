package com.loveproof.testimonials.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loveproof.testimonials.domain.AdminUser;

/**
 * Persistence for {@link AdminUser}.
 */
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    /**
     * Finds an administrator by username, case-insensitively.
     *
     * <p>Usernames are stored lower-cased, so the lookup lower-cases its argument too.
     *
     * @param username the username
     * @return the administrator, or empty when no such account exists
     */
    Optional<AdminUser> findByUsername(String username);
}
