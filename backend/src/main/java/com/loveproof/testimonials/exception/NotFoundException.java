package com.loveproof.testimonials.exception;

import java.util.UUID;

/**
 * Thrown when a requested resource does not exist.
 *
 * <p>Rendered as HTTP 404 with the code {@code NOT_FOUND}.
 */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the human-readable message
     */
    public NotFoundException(String message) {
        super(message);
    }

    /**
     * Creates the exception for a missing testimonial.
     *
     * @param id the testimonial identifier
     * @return the exception
     */
    public static NotFoundException testimonial(UUID id) {
        return new NotFoundException("No testimonial exists with id " + id + ".");
    }
}
