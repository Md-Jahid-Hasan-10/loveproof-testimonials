package com.loveproof.testimonials.exception;

/**
 * Thrown when a submission arrives for a Space that is not collecting.
 *
 * <p>This is the read-only case from PRD §4.9: a downgraded account keeps its
 * Spaces, but the Spaces beyond the plan limit are kept and cannot be changed
 * — including by the Space's own public form.
 *
 * <p>The message is deliberately neutral. The submitter learns that the Space
 * is not accepting responses and nothing else; why it stopped is the owner's
 * business, and the owner sees the reason on their own Space page (PRD
 * §4.5).
 */
public class SpaceNotAcceptingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     */
    public SpaceNotAcceptingException() {
        super("This Space is not accepting responses at the moment.");
    }
}
