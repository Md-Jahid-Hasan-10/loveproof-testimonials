package com.loveproof.testimonials.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.config.StripeProperties;
import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.domain.SubscriptionStatus;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.repository.AppUserRepository;

/**
 * The billing read model.
 *
 * <p>This service only reads. It never writes a plan and never changes a
 * subscription: the webhook is the only thing that does that. The billing
 * page therefore reports what is true rather than deciding what should be.
 */
@Service
public class BillingService {

    private final AppUserRepository userRepository;

    private final StripeProperties stripeProperties;

    /**
     * Creates the service.
     *
     * @param userRepository   the account store
     * @param stripeProperties the Stripe configuration
     */
    public BillingService(AppUserRepository userRepository, StripeProperties stripeProperties) {
        this.userRepository = userRepository;
        this.stripeProperties = stripeProperties;
    }

    /**
     * Reads the signed-in account's billing state.
     *
     * @param ownerId the account
     * @return the state
     */
    @Transactional(readOnly = true)
    public BillingState stateFor(UUID ownerId) {
        AppUser account = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("That account does not exist."));

        return new BillingState(
                account.getPlan(),
                account.getSubscriptionStatus(),
                account.isCancelAtPeriodEnd(),
                account.isSubscriptionPastDue(),
                account.getCurrentPeriodEnd(),
                stripeProperties.isConfigured(),
                PlanCatalog.all());
    }

    /**
     * The signed-in account's billing state.
     *
     * @param plan                   the current plan
     * @param status                 the subscription status
     * @param cancelAtPeriodEnd      whether the subscription ends at the close
     *                               of the paid period
     * @param pastDue                whether a renewal failed and Stripe is
     *                               still retrying
     * @param currentPeriodEnd       when the paid period ends
     * @param billingConfigured      whether Stripe is configured on this
     *                               installation
     * @param plans                  the plans, with their features and limits
     */
    public record BillingState(
            Plan plan,
            SubscriptionStatus status,
            boolean cancelAtPeriodEnd,
            boolean pastDue,
            Instant currentPeriodEnd,
            boolean billingConfigured,
            List<PlanCatalog.PlanOption> plans) {
    }
}
