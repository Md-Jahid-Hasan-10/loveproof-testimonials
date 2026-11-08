package com.loveproof.testimonials.domain;

/**
 * Role of an administrator.
 *
 * <p>Only {@link #ADMIN} may log in to the admin area in v1 (assumption A-3). {@link #MODERATOR}
 * exists in the schema so the split can be introduced later without a migration.
 */
public enum AdminRole {

    ADMIN,
    MODERATOR
}
