package com.loveproof.testimonials.domain;

/**
 * Moderation state of a testimonial.
 *
 * <p>Orthogonal to the soft-delete flag: a deleted testimonial keeps the status it had when it was
 * deleted, so the audit history stays meaningful. See {@code docs/data-model.md} §1.
 */
public enum TestimonialStatus {

    /** Submitted but not yet reviewed. Every submission starts here. */
    PENDING,

    /** Moderated and visible on the public wall. */
    APPROVED,

    /** Moderated and excluded from the public wall. Always carries a rejection reason. */
    REJECTED
}
