package com.loveproof.testimonials.exception;

/**
 * Thrown when a request conflicts with the current state of a resource.
 *
 * <p>Used for duplicate submissions and for acting on an already-deleted testimonial. Rendered as
 * HTTP 409 with the code {@code CONFLICT}.
 */
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the human-readable message
     */
    public ConflictException(String message) {
        super(message);
    }
}
