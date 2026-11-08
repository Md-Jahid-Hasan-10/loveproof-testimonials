package com.loveproof.testimonials.domain;

/**
 * The subscription plan.
 *
 * <p>The plan decides which limits apply and which features are unlocked. It is
 * only ever changed by a confirmed Stripe webhook -- nothing in the application
 * may upgrade an account on its own.
 */
public enum Plan {

    /** The default plan. Three Spaces, a hundred testimonials each, basic themes, masonry embed. */
    FREE,

    /** The paid plan. Twenty-five Spaces, a thousand testimonials each, premium themes and embed options. */
    PREMIUM;

    /**
     * Whether this plan includes the given one.
     *
     * @param required the plan a feature requires
     * @return {@code true} when this plan is at least as good as {@code required}
     */
    public boolean includes(Plan required) {
        return this.ordinal() >= required.ordinal();
    }
}
