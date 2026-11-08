package com.loveproof.testimonials.domain;

import java.util.Locale;

/**
 * The status of a Stripe subscription.
 *
 * <p>The vocabulary is Stripe's own, so the webhook's mapping is a lookup
 * rather than an interpretation. The stored value is the lowercase Stripe
 * string; the constant is uppercase because that is the Java convention, and
 * a converter bridges the two.
 *
 * <p>The plan is a pure function of the status:
 * {@link #grantsPremium()} decides it. That is what makes "cancelling keeps
 * Premium until the period ends" and "a failed renewal keeps Premium while
 * Stripe retries" fall out of one rule instead of two.
 */
public enum SubscriptionStatus {

    /** The account has never subscribed. */
    NONE,

    /** The subscription is paid for and running. */
    ACTIVE,

    /** The subscription is in its trial period. */
    TRIALING,

    /** A renewal failed and Stripe is still retrying. Access continues. */
    PAST_DUE,

    /** The subscription ended, either by cancellation or by the period running out. */
    CANCELED,

    /** The payment failed for good. */
    UNPAID,

    /** The first payment has not succeeded yet. */
    INCOMPLETE,

    /** The first payment never succeeded and the window closed. */
    INCOMPLETE_EXPIRED,

    /** The subscription is paused. */
    PAUSED;

    /**
     * Whether this status gives the account Premium access.
     *
     * <p>A past-due subscription still grants Premium: the customer has paid
     * for the period, the renewal simply failed, and Stripe is still
     * retrying. Stripping access the moment a retry fails would take
     * Premium away from a customer whose card was merely declined once.
     *
     * @return whether Premium is granted
     */
    public boolean grantsPremium() {
        return this == ACTIVE || this == TRIALING || this == PAST_DUE;
    }

    /**
     * Whether the subscription is still running in some form.
     *
     * @return whether the subscription is live
     */
    public boolean isLive() {
        return this != NONE && this != CANCELED;
    }

    /**
     * The value Stripe uses for this status.
     *
     * @return the wire value
     */
    public String wireValue() {
        if (this == NONE) {
            return "NONE";
        }
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Finds a status from the value Stripe sends.
     *
     * @param value the wire value
     * @return the status, or {@link #NONE} when the value is not recognised
     */
    public static SubscriptionStatus fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        String normalised = value.trim().toLowerCase(Locale.ROOT);
        for (SubscriptionStatus candidate : values()) {
            if (candidate != NONE && candidate.wireValue().equals(normalised)) {
                return candidate;
            }
        }
        return NONE;
    }
}
