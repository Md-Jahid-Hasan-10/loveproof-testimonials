package com.loveproof.testimonials.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.domain.SubscriptionStatus;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the reconciliation job.
 *
 * <p>The webhook is the primary mechanism, but a delivery can be lost and a
 * period can end without one arriving. The job is the backstop: it reads
 * every live subscription from Stripe and applies the **same** transition
 * the webhook applies, so the two can never disagree.
 */
@SpringBootTest
@Import(SubscriptionReconciliationJobTest.FakeGatewayConfiguration.class)
@TestPropertySource(properties = {
        "loveproof.stripe.secret-key=sk_test_fake",
        "loveproof.stripe.webhook-secret=whsec_test_secret",
        "loveproof.stripe.premium-price-id=price_test_fake" })
class SubscriptionReconciliationJobTest extends PostgresIntegrationTest {

    private static final String EMAIL = "ada@example.com";

    private static final String SUBSCRIPTION = "sub_reconcile";

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private SubscriptionReconciliationJob job;

    @Autowired
    private SubscriptionReconciler reconciler;

    @Autowired
    private FakeStripeGateway gateway;

    @BeforeEach
    void clearEverything() {
        userRepository.deleteAll();
        gateway.reset();
    }

    @Test
    @DisplayName("the job downgrades an account whose subscription Stripe now reports as canceled")
    void theJobDowngradesAnAccountWhoseSubscriptionStripeNowReportsAsCanceled() {
        makeAccount();

        gateway.snapshot = new StripeGateway.SubscriptionSnapshot(
                SUBSCRIPTION, "canceled", false, Instant.now().minusSeconds(3600));

        job.reconcile();

        AppUser account = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(account.getPlan()).isEqualTo(Plan.FREE);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    @DisplayName("the job upgrades an account whose subscription Stripe reports as active")
    void theJobUpgradesAnAccountWhoseSubscriptionStripeReportsAsActive() {
        makeAccount();

        gateway.snapshot = new StripeGateway.SubscriptionSnapshot(
                SUBSCRIPTION, "active", false, Instant.now().plusSeconds(86400));

        job.reconcile();

        AppUser account = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(account.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("the job keeps an account Premium when the subscription is still running but ending")
    void theJobKeepsAnAccountPremiumWhenTheSubscriptionIsStillRunningButEnding() {
        makeAccount();

        gateway.snapshot = new StripeGateway.SubscriptionSnapshot(
                SUBSCRIPTION, "active", true, Instant.now().plusSeconds(86400));

        job.reconcile();

        AppUser account = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(account.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(account.isCancelAtPeriodEnd()).isTrue();
    }

    @Test
    @DisplayName("the job leaves an account without a subscription alone")
    void theJobLeavesAnAccountWithoutASubscriptionAlone() {
        userRepository.save(new AppUser(EMAIL, "{noop}", "Test User"));

        job.reconcile();

        AppUser account = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(account.getPlan()).isEqualTo(Plan.FREE);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.NONE);
        assertThat(gateway.retrieved).isEmpty();
    }

    @Test
    @DisplayName("one failing subscription does not stop the others")
    void oneFailingSubscriptionDoesNotStopTheOthers() {
        AppUser first = userRepository.save(new AppUser("first@example.com", "{noop}", "First"));
        first.setStripeSubscriptionId("sub_broken");
        userRepository.save(first);

        AppUser second = userRepository.save(new AppUser("second@example.com", "{noop}", "Second"));
        second.setStripeSubscriptionId("sub_fine");
        userRepository.save(second);

        gateway.failFor = "sub_broken";
        gateway.snapshot = new StripeGateway.SubscriptionSnapshot(
                "sub_fine", "active", false, Instant.now().plusSeconds(86400));

        job.reconcile();

        assertThat(userRepository.findByEmailIgnoreCase("first@example.com").orElseThrow().getPlan())
                .isEqualTo(Plan.FREE);
        assertThat(userRepository.findByEmailIgnoreCase("second@example.com").orElseThrow().getPlan())
                .isEqualTo(Plan.PREMIUM);
    }

    @Test
    @DisplayName("the reconciler writes the new state in its own transaction")
    void theReconcilerWritesTheNewStateInItsOwnTransaction() {
        AppUser account = userRepository.save(new AppUser(EMAIL, "{noop}", "Test User"));
        account.setStripeSubscriptionId(SUBSCRIPTION);
        userRepository.save(account);

        gateway.snapshot = new StripeGateway.SubscriptionSnapshot(
                SUBSCRIPTION, "active", false, Instant.now().plusSeconds(86400));

        reconciler.reconcile(account.getId());

        // The write is committed, not left open for an outer transaction to
        // roll back. The reconciler runs in its own transaction precisely so
        // that one account's failure cannot undo another's write.
        AppUser reloaded = userRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getPlan()).isEqualTo(Plan.PREMIUM);
    }

    @Test
    @DisplayName("reconciling an account that has gone changes nothing")
    void reconcilingAnAccountThatHasGoneChangesNothing() {
        job.reconcile();

        // There is no account to reconcile, so there is nothing to write and
        // nothing to fail.
        assertThat(gateway.retrieved).isEmpty();
    }

    /**
     * Creates an account that holds a subscription.
     */
    private void makeAccount() {
        AppUser account = userRepository.findByEmailIgnoreCase(EMAIL)
                .orElseGet(() -> userRepository.save(new AppUser(EMAIL, "{noop}", "Test User")));
        account.setStripeCustomerId("cus_reconcile");
        account.setStripeSubscriptionId(SUBSCRIPTION);
        userRepository.save(account);
    }

    /**
     * Replaces the real gateway with one that answers locally.
     */
    @TestConfiguration
    static class FakeGatewayConfiguration {

        /**
         * The fake gateway.
         *
         * @return the gateway
         */
        @Bean
        @Primary
        FakeStripeGateway fakeStripeGateway() {
            return new FakeStripeGateway();
        }
    }

    /**
     * A gateway that answers without a network.
     */
    static class FakeStripeGateway implements StripeGateway {

        /** The subscription the next read returns. */
        SubscriptionSnapshot snapshot = new SubscriptionSnapshot(
                "sub_test", "active", false, Instant.now());

        /** The subscription that cannot be read. */
        String failFor;

        /** The subscriptions that were read. */
        final java.util.List<String> retrieved = new java.util.ArrayList<>();

        /**
         * Forgets everything.
         */
        void reset() {
            snapshot = new SubscriptionSnapshot("sub_test", "active", false, Instant.now());
            failFor = null;
            retrieved.clear();
        }

        @Override
        public String createCustomer(String email, String name) {
            return "cus_test";
        }

        @Override
        public String createCheckoutSession(
                String customerId, String priceId, String accountId,
                String successUrl, String cancelUrl) {
            return "https://checkout.stripe.test/session";
        }

        @Override
        public String createPortalSession(String customerId, String returnUrl) {
            return "https://billing.stripe.test/session";
        }

        @Override
        public SubscriptionSnapshot retrieveSubscription(String subscriptionId) {
            retrieved.add(subscriptionId);
            if (subscriptionId.equals(failFor)) {
                throw new IllegalStateException("Stripe is unreachable for now.");
            }
            return snapshot;
        }

        @Override
        public void cancelSubscription(String subscriptionId) {
            // Nothing to cancel.
        }

        @Override
        public void resumeSubscription(String subscriptionId) {
            // Nothing to resume.
        }
    }
}
