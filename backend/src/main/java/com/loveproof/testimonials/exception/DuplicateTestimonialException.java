package com.loveproof.testimonials.exception;

/**
 * Thrown when an identical testimonial was submitted inside the duplicate window.
 *
 * <p>Rendered as HTTP 409 with the code {@code DUPLICATE_SUBMISSION}.
 */
public class DuplicateTestimonialException extends ConflictException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the human-readable message
     */
    public DuplicateTestimonialException(String message) {
        super(message);
    }
}
