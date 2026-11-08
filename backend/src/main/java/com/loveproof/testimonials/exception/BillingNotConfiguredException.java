package com.loveproof.testimonials.exception;

/**
 * Thrown when a billing action is asked for but Stripe is not configured.
 *
 * <p>The message says what is missing rather than blaming the caller: the
 * caller asked for something legitimate, and the installation simply does not
 * have the keys to do it.
 */
public class BillingNotConfiguredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the message shown to the user
     */
    public BillingNotConfiguredException(String message) {
        super(message);
    }
}
