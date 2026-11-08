package com.loveproof.testimonials.domain;

/**
 * The kind of moderation action recorded in the append-only audit log.
 *
 * <p>{@link #SUBMIT} is recorded on creation so the trail covers the full lifecycle, not just
 * moderation.
 */
public enum ModerationActionType {

    /** A visitor submitted the testimonial. */
    SUBMIT,

    /** A moderator approved the testimonial. */
    APPROVE,

    /** A moderator rejected the testimonial. */
    REJECT,

    /** A moderator soft-deleted the testimonial. */
    DELETE,

    /** A moderator restored a soft-deleted testimonial. */
    RESTORE
}
