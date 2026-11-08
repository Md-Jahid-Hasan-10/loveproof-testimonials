package com.loveproof.testimonials.web.dto.billing;

import java.util.List;

import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.domain.SubscriptionStatus;
import com.loveproof.testimonials.service.BillingService;
import com.loveproof.testimonials.service.PlanCatalog;

/**
 * The billing state, as the client sees it.
 *
 * <p>The Stripe identifiers are never included: they are the server's to
 * keep, and nothing in the interface needs them.
 */
public record BillingResponse(
        Plan plan,
        SubscriptionStatus subscriptionStatus,
        boolean cancelAtPeriodEnd,
        boolean pastDue,
        String currentPeriodEnd,
        boolean billingConfigured,
        List<PlanCatalog.PlanOption> plans) {

    /**
     * Renders the billing state for the client.
     *
     * @param state the state
     * @return the response
     */
    public static BillingResponse from(BillingService.BillingState state) {
        return new BillingResponse(
                state.plan(),
                state.status(),
                state.cancelAtPeriodEnd(),
                state.pastDue(),
                state.currentPeriodEnd() == null ? null : state.currentPeriodEnd().toString(),
                state.billingConfigured(),
                state.plans());
    }
}
