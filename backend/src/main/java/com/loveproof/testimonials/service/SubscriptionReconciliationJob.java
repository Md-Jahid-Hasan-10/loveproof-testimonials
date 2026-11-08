package com.loveproof.testimonials.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.config.StripeProperties;
import com.loveproof.testimonials.repository.AppUserRepository;

/**
 * The backstop: re-checks every live subscription against Stripe.
 *
 * <p>The webhook is the primary mechanism, and it is the only thing that
 * changes a plan in the normal case. But a delivery can be lost -- Stripe
 * retries, but a delivery can still be dropped -- and a subscription can
 * reach the end of its period without a webhook arriving. This job closes
 * that gap.
 *
 * <p>It applies the **same** transition the webhook applies
 * ({@link com.loveproof.testimonials.domain.AppUser#applySubscriptionState}),
 * so the two can never disagree about a plan. That is the whole reason the
 * transition lives on the entity rather than in each caller.
 *
 * <p>Each account is reconciled in its **own** transaction, by
 * {@link SubscriptionReconciler}. One account failing must not roll back the
 * others, and a long run must not hold a transaction open across a call to
 * Stripe.
 */
@Service
public class SubscriptionReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionReconciliationJob.class);

    private final AppUserRepository userRepository;

    private final SubscriptionReconciler reconciler;

    private final StripeProperties properties;

    /**
     * Creates the job.
     *
     * @param userRepository the account store
     * @param reconciler     the per-account reconciler
     * @param properties     the Stripe configuration
     */
    public SubscriptionReconciliationJob(
            AppUserRepository userRepository,
            SubscriptionReconciler reconciler,
            StripeProperties properties) {
        this.userRepository = userRepository;
        this.reconciler = reconciler;
        this.properties = properties;
    }

    /**
     * Re-checks every account that holds a subscription.
     *
     * <p>One account failing does not stop the others: a subscription that
     * Stripe no longer knows about is downgraded to Free, and the rest are
     * still reconciled.
     */
    @Scheduled(
            fixedDelayString = "${loveproof.billing.reconcile-interval-ms:3600000}",
            initialDelayString = "${loveproof.billing.reconcile-initial-delay-ms:60000}")
    public void reconcile() {
        if (!properties.isConfigured()) {
            // Nothing to reconcile against. Saying so once an hour beats
            // filling the log with a stack trace.
            log.debug("Skipping the reconciliation, because Stripe is not configured.");
            return;
        }

        List<UUID> accounts = accountIds();
        log.info("Reconciling {} subscriptions against Stripe.", accounts.size());

        for (UUID accountId : accounts) {
            try {
                reconciler.reconcile(accountId);
            } catch (RuntimeException ex) {
                log.warn("Could not reconcile the subscription of account {}: {}",
                        accountId, ex.getMessage());
            }
        }
    }

    /**
     * The identifiers of the accounts that hold a subscription.
     *
     * <p>Only the identifiers are read here. The accounts themselves are read
     * inside each account's own transaction, so a reconciliation never
     * writes back a stale copy of a row another request has since changed.
     *
     * @return the account identifiers
     */
    @Transactional(readOnly = true)
    public List<UUID> accountIds() {
        return userRepository.findByStripeSubscriptionIdIsNotNull().stream()
                .map(appUser -> appUser.getId())
                .toList();
    }
}
