package com.loveproof.testimonials.config;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The signed-in customer.
 *
 * <p>A small helper rather than a full principal class: the SaaS surface only
 * ever needs the account's identifier, which is the username Spring Security
 * already carries.
 */
public final class CurrentUser {

    private CurrentUser() {
        // A holder for static methods.
    }

    /**
     * The identifier of the signed-in customer.
     *
     * @return the account identifier
     * @throws IllegalStateException when nobody is signed in
     */
    public static UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No customer is signed in.");
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("No customer is signed in.");
        }
        return UUID.fromString(name);
    }

    /**
     * Whether a customer is signed in.
     *
     * @return {@code true} when somebody is signed in
     */
    public static boolean isSignedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && !authentication.getName().isBlank();
    }
}
