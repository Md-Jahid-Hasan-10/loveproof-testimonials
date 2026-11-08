package com.loveproof.testimonials.domain;

/**
 * What an {@code auth_token} is for.
 */
public enum AuthTokenPurpose {

    /** Proves the user owns the email address on the account. */
    VERIFY_EMAIL,

    /** Lets the user choose a new password. */
    RESET_PASSWORD
}
