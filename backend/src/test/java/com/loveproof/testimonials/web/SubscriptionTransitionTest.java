package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.domain.SubscriptionStatus;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.StripeEventRepository;
import com.loveproof.testimonials.service.StripeGateway;
import com.loveproof.testimonials.support.PostgresIntegrationTest;
import com.loveproof.testimonials.support.WebhookDelivery;

/**
 * Walks the whole plan-transition state machine.
 *
 * <p>One rule produces every row: <em>the plan is a function of the
 * subscription status</em>. These tests drive the webhook with hand-written
 * event bodies -- one per status Stripe can report -- and check the plan,
 * the stored status, the past-due flag and the cancellation flag that come
 * out.
 *
 * <p>The two cases the PRD separates are here too: a period that ran out
 * after a cancellation, and a payment that failed for good.
 */
@AutoConfigureMockMvc
@Import(SubscriptionTransitionTest.FakeGatewayConfiguration.class)
@TestPropertySource(properties = {
        "loveproof.stripe.secret-key=sk_test_fake",
        "loveproof.stripe.webhook-secret=whsec_test_secret",
        "loveproof.stripe.premium-price-id=price_test_fake" })
class SubscriptionTransitionTest extends PostgresIntegrationTest {

    private static final String EMAIL = "ada@example.com";

    private static final String SUBSCRIPTION = "sub_transition";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private StripeEventRepository eventRepository;

    @Autowired
    private FakeStripeGateway gateway;

    @BeforeEach
    void clearEverything() {
        userRepository.deleteAll();
        eventRepository.deleteAll();
        gateway.reset();
        makeAccount();
    }

    @Test
    @DisplayName("an active subscription makes the account Premium")
    void anActiveSubscriptionMakesTheAccountPremium() throws Exception {
        deliver("evt_active", "active", false);

        AppUser account = account();
        assertThat(account.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(account.isSubscriptionPastDue()).isFalse();
        assertThat(account.isCancelAtPeriodEnd()).isFalse();
    }

    @Test
    @DisplayName("a trialing subscription makes the account Premium")
    void aTrialingSubscriptionMakesTheAccountPremium() throws Exception {
        deliver("evt_trialing", "trialing", false);

        AppUser account = account();
        assertThat(account.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.TRIALING);
    }

    @Test
    @DisplayName("a past-due subscription keeps Premium and is flagged")
    void aPastDueSubscriptionKeepsPremiumAndIsFlagged() throws Exception {
        deliver("evt_past_due", "past_due", false);

        AppUser account = account();
        // A failed renewal keeps access while Stripe retries.
        assertThat(account.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(account.isSubscriptionPastDue()).isTrue();
    }

    @Test
    @DisplayName("a canceled subscription takes the account back to Free")
    void aCanceledSubscriptionTakesTheAccountBackToFree() throws Exception {
        deliver("evt_canceled", "canceled", false);

        AppUser account = account();
        assertThat(account.getPlan()).isEqualTo(Plan.FREE);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(account.isSubscriptionPastDue()).isFalse();
    }

    @Test
    @DisplayName("an unpaid subscription takes the account back to Free")
    void anUnpaidSubscriptionTakesTheAccountBackToFree() throws Exception {
        deliver("evt_unpaid", "unpaid", false);

        AppUser account = account();
        // A final failure, as opposed to a period that ran out after a
        // cancellation. Both end at Free; only the status tells them apart.
        assertThat(account.getPlan()).isEqualTo(Plan.FREE);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.UNPAID);
    }

    @Test
    @DisplayName("an incomplete subscription leaves the account on Free")
    void anIncompleteSubscriptionLeavesTheAccountOnFree() throws Exception {
        deliver("evt_incomplete", "incomplete", false);

        AppUser account = account();
        assertThat(account.getPlan()).isEqualTo(Plan.FREE);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.INCOMPLETE);
    }

    @Test
    @DisplayName("an expired incomplete subscription leaves the account on Free")
    void anExpiredIncompleteSubscriptionLeavesTheAccountOnFree() throws Exception {
        deliver("evt_incomplete_expired", "incomplete_expired", false);

        AppUser account = account();
        assertThat(account.getPlan()).isEqualTo(Plan.FREE);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.INCOMPLETE_EXPIRED);
    }

    @Test
    @DisplayName("a paused subscription leaves the account on Free")
    void aPausedSubscriptionLeavesTheAccountOnFree() throws Exception {
        deliver("evt_paused", "paused", false);

        AppUser account = account();
        assertThat(account.getPlan()).isEqualTo(Plan.FREE);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PAUSED);
    }

    @Test
    @DisplayName("cancelling at the period end keeps the account Premium")
    void cancellingAtThePeriodEndKeepsTheAccountPremium() throws Exception {
        deliver("evt_cancelling", "active", true);

        AppUser account = account();
        // The subscription is still running, so the access continues. Stripe
        // reports the end of the period later.
        assertThat(account.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(account.isCancelAtPeriodEnd()).isTrue();
        assertThat(account.getCurrentPeriodEnd()).isNotNull();
    }

    @Test
    @DisplayName("a deleted subscription takes the account back to Free")
    void aDeletedSubscriptionTakesTheAccountBackToFree() throws Exception {
        byte[] body = WebhookDelivery.deletedEvent("evt_deleted", SUBSCRIPTION);

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.sign(body)))
                .andExpect(status().isOk());

        AppUser account = account();
        assertThat(account.getPlan()).isEqualTo(Plan.FREE);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(account.isCancelAtPeriodEnd()).isFalse();
    }

    @Test
    @DisplayName("a completed checkout reads the subscription and applies its state")
    void aCompletedCheckoutReadsTheSubscriptionAndAppliesItsState() throws Exception {
        // The Checkout Session names the subscription but not its state, so
        // the webhook reads the subscription from Stripe.
        gateway.snapshot = new StripeGateway.SubscriptionSnapshot(
                SUBSCRIPTION, "active", false, Instant.now().plusSeconds(86400));

        byte[] body = WebhookDelivery.checkoutEvent("evt_checkout", SUBSCRIPTION);

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.sign(body)))
                .andExpect(status().isOk());

        AppUser account = account();
        assertThat(account.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(gateway.retrieved).contains(SUBSCRIPTION);
    }

    @Test
    @DisplayName("an invoice event changes nothing")
    void anInvoiceEventChangesNothing() throws Exception {
        deliver("evt_invoice", "active", false);
        assertThat(account().getPlan()).isEqualTo(Plan.PREMIUM);

        byte[] body = WebhookDelivery.eventOfType("evt_invoice_failed", "invoice.payment_failed");

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.sign(body)))
                .andExpect(status().isOk());

        // The invoice event is recorded but changes nothing: the subscription
        // event that accompanies it is what decides the plan.
        AppUser account = account();
        assertThat(account.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(account.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(account.isSubscriptionPastDue()).isFalse();
    }

    @Test
    @DisplayName("an event this installation does not act on is recorded and changes nothing")
    void anEventThisInstallationDoesNotActOnIsRecordedAndChangesNothing() throws Exception {
        byte[] body = WebhookDelivery.eventOfType("evt_charge_refunded", "charge.refunded");

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.sign(body)))
                .andExpect(status().isOk());

        assertThat(account().getPlan()).isEqualTo(Plan.FREE);
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("an event for an unknown subscription is recorded and changes nothing")
    void anEventForAnUnknownSubscriptionIsRecordedAndChangesNothing() throws Exception {
        byte[] body = WebhookDelivery.subscriptionEvent(
                "evt_unknown", "sub_nobody_owns", "active", false, periodEnd());

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.sign(body)))
                .andExpect(status().isOk());

        assertThat(account().getPlan()).isEqualTo(Plan.FREE);
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    /**
     * Delivers a signed subscription event.
     *
     * @param eventId          the event's identifier
     * @param status           Stripe's subscription status
     * @param cancelAtPeriodEnd whether the subscription ends at the close of
     *                          the paid period
     * @throws Exception when the request fails
     */
    private void deliver(String eventId, String status, boolean cancelAtPeriodEnd) throws Exception {
        byte[] body = WebhookDelivery.subscriptionEvent(
                eventId, SUBSCRIPTION, status, cancelAtPeriodEnd, periodEnd());

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.sign(body)))
                .andExpect(status().isOk());
    }

    /**
     * The period end used by the events.
     *
     * @return the epoch second
     */
    private static long periodEnd() {
        return Instant.now().plusSeconds(30 * 24 * 3600).getEpochSecond();
    }

    /**
     * The account.
     *
     * @return the account
     */
    private AppUser account() {
        return userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
    }

    /**
     * Creates an account that owns the subscription.
     */
    private void makeAccount() {
        AppUser account = userRepository.findByEmailIgnoreCase(EMAIL)
                .orElseGet(() -> userRepository.save(new AppUser(EMAIL, "{noop}", "Test User")));
        account.setStripeCustomerId("cus_transition");
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

        /** The subscriptions that were read. */
        final java.util.List<String> retrieved = new java.util.ArrayList<>();

        /**
         * Forgets everything.
         */
        void reset() {
            snapshot = new SubscriptionSnapshot("sub_test", "active", false, Instant.now());
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
