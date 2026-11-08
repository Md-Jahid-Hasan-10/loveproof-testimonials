package com.loveproof.testimonials.domain;

/**
 * What a delivered webhook event did.
 *
 * <p>Recorded on the ledger row so the ledger explains itself: an operator
 * reading the table can tell an event that changed an account from one that
 * named no known account, without having to correlate it with the Stripe
 * dashboard.
 */
public enum StripeEventOutcome {

    /** The event changed an account's subscription state. */
    APPLIED,

    /** The event was recorded but changed nothing. */
    IGNORED,

    /** The event named no account this installation knows about. */
    NO_ACCOUNT
}
