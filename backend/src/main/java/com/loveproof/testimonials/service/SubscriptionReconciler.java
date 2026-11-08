package com.loveproof.testimonials.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.SubscriptionStatus;
import com.loveproof.testimonials.repository.AppUserRepository;

/**
 * Reconciles one account's subscription against Stripe.
 *
 * <p>This is a separate bean from {@link SubscriptionReconciliationJob} for
 * one reason: the job calls it from a loop, and a call that stays inside the
 * same object would bypass the transaction proxy and run with no
 * transaction at all. Here each account is reconciled in its own
 * transaction, so one failure cannot roll back the others.
 */
@Service
public class SubscriptionReconciler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionReconciler.class);

    private final AppUserRepository userRepository;

    private final StripeGateway gateway;

    /**
     * Creates the reconciler.
     *
     * @param userRepository the account store
     * @param gateway        the Stripe operations
     */
    public SubscriptionReconciler(AppUserRepository userRepository, StripeGateway gateway) {
        this.userRepository = userRepository;
        this.gateway = gateway;
    }

    /**
     * Re-checks one account's subscription.
     *
     * @param accountId the account
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcile(UUID accountId) {
        AppUser account = userRepository.findById(accountId).orElse(null);
        if (account == null) {
            return;
        }

        String subscriptionId = account.getStripeSubscriptionId();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return;
        }

        StripeGateway.SubscriptionSnapshot snapshot = gateway.retrieveSubscription(subscriptionId);
        if (snapshot == null) {
            return;
        }

        SubscriptionStatus status = SubscriptionStatus.fromWireValue(snapshot.status());
        Instant periodEnd = snapshot.currentPeriodEnd();

        account.applySubscriptionState(status, snapshot.cancelAtPeriodEnd(), periodEnd);
        userRepository.save(account);

        log.info("Reconciled the subscription of account {} to {}.",
                account.getId(), status.wireValue());
    }
}
