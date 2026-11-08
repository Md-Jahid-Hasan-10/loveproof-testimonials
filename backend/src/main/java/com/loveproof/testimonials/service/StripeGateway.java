package com.loveproof.testimonials.service;

import java.time.Instant;

/**
 * The Stripe operations the billing feature needs.
 *
 * <p>This is the seam between the billing rules and Stripe itself. The rules
 * are written against this interface, so they can be exercised without a
 * network and without an SDK, and so that the shape of what Stripe is asked
 * for is written down in one place rather than spread across the callers.
 */
public interface StripeGateway {

    /**
     * Creates a Stripe customer.
     *
     * @param email the customer's email address
     * @param name  the customer's name
     * @return the customer's identifier
     */
    String createCustomer(String email, String name);

    /**
     * Creates a Checkout Session for the Premium plan.
     *
     * <p>The account's identifier is attached to the subscription's metadata,
     * which is how a webhook for a subscription created here finds the
     * account it belongs to.
     *
     * @param customerId   the Stripe customer
     * @param priceId      the price of the Premium plan
     * @param accountId    the LoveProof account
     * @param successUrl   where to send the customer after a successful payment
     * @param cancelUrl    where to send the customer if they back out
     * @return the Checkout Session's URL
     */
    String createCheckoutSession(
            String customerId,
            String priceId,
            String accountId,
            String successUrl,
            String cancelUrl);

    /**
     * Creates a Billing Portal Session.
     *
     * @param customerId the Stripe customer
     * @param returnUrl  where to send the customer when they are done
     * @return the portal URL
     */
    String createPortalSession(String customerId, String returnUrl);

    /**
     * Reads a subscription's current state.
     *
     * @param subscriptionId the Stripe subscription
     * @return the subscription's state
     */
    SubscriptionSnapshot retrieveSubscription(String subscriptionId);

    /**
     * Asks Stripe to cancel a subscription at the close of the paid period.
     *
     * @param subscriptionId the Stripe subscription
     */
    void cancelSubscription(String subscriptionId);

    /**
     * Asks Stripe to keep a subscription running past the period it would end.
     *
     * @param subscriptionId the Stripe subscription
     */
    void resumeSubscription(String subscriptionId);

    /**
     * A subscription's state, as Stripe reports it.
     *
     * @param id                 the subscription identifier
     * @param status             the status, in Stripe's own vocabulary
     * @param cancelAtPeriodEnd  whether the subscription ends at the close of
     *                           the paid period
     * @param currentPeriodEnd   when the paid period ends
     */
    record SubscriptionSnapshot(
            String id,
            String status,
            boolean cancelAtPeriodEnd,
            Instant currentPeriodEnd) {
    }
}
