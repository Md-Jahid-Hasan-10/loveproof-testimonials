package com.loveproof.testimonials.domain;

/**
 * How an account signs in.
 *
 * <p>An account may end up with both methods available -- registering with a
 * password and later signing in with Google keeps one account, per OPEN-A.
 */
public enum AuthProvider {

    /** Email and password. */
    PASSWORD,

    /** Google. The account is verified from the outset. */
    GOOGLE
}
