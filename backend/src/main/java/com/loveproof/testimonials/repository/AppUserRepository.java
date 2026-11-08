package com.loveproof.testimonials.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loveproof.testimonials.domain.AppUser;

/**
 * The account store.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Finds an account by email, ignoring case.
     *
     * @param email the email address
     * @return the account, or empty
     */
    Optional<AppUser> findByEmailIgnoreCase(String email);

    /**
     * Finds an account by its Google subject identifier.
     *
     * @param googleSubject the subject identifier from Google
     * @return the account, or empty
     */
    Optional<AppUser> findByGoogleSubject(String googleSubject);

    /**
     * Whether an account already uses the email.
     *
     * @param email the email address
     * @return {@code true} when the email is taken
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Counts the accounts that use the email.
     *
     * @param email the email address
     * @return the number of matches
     */
    long countByEmailIgnoreCase(String email);
}
