package com.loveproof.testimonials.service;

import com.loveproof.testimonials.domain.Plan;

/**
 * The limits and features each plan carries.
 *
 * <p>The numbers live here, in one place, because they are checked in several
 * places: Space creation, public submission, and embed configuration saves.
 * Every check goes through this class so the limits cannot drift apart.
 */
public final class PlanLimits {

    /** How many Spaces a Free account may own. */
    public static final int FREE_SPACES = 3;

    /** How many Spaces a Premium account may own. */
    public static final int PREMIUM_SPACES = 25;

    /** How many testimonials a Free Space may collect. */
    public static final int FREE_TESTIMONIALS_PER_SPACE = 100;

    /** How many testimonials a Premium Space may collect. */
    public static final int PREMIUM_TESTIMONIALS_PER_SPACE = 1_000;

    private PlanLimits() {
        // A holder for constants.
    }

    /**
     * How many Spaces an account on the given plan may own.
     *
     * @param plan the account's plan
     * @return the Space limit
     */
    public static int spacesFor(Plan plan) {
        return plan == Plan.PREMIUM ? PREMIUM_SPACES : FREE_SPACES;
    }

    /**
     * How many testimonials a Space on the given plan may collect.
     *
     * @param plan the owner's plan
     * @return the testimonial limit per Space
     */
    public static int testimonialsPerSpaceFor(Plan plan) {
        return plan == Plan.PREMIUM ? PREMIUM_TESTIMONIALS_PER_SPACE : FREE_TESTIMONIALS_PER_SPACE;
    }
}
