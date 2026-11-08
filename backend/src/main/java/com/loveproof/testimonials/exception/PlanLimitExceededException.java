package com.loveproof.testimonials.exception;

/**
 * Thrown when an action would take the account past a plan limit.
 *
 * <p>The message is safe to show the user: it says what was reached and what
 * to do about it, without leaking anything about the account.
 */
public class PlanLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the message shown to the user
     */
    public PlanLimitExceededException(String message) {
        super(message);
    }
}
