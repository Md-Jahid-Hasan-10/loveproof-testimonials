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
 * Verifies that a replayed event changes nothing.
 *
 * <p>Stripe retries a delivery it did not get a 2xx for, so the same event
 * arriving twice is routine rather than exceptional. The ledger's primary key
 * on the event identifier is what makes the second delivery a no-op: the
 * insert cannot succeed, so the effect is not applied a second time.
 *
 * <p>The endpoint still answers 200 on a replay. A 500 would make Stripe
 * retry forever, and the event has already been dealt with.
 */
@AutoConfigureMockMvc
@Import(WebhookIdempotencyTest.FakeGatewayConfiguration.class)
@TestPropertySource(properties = {
        "loveproof.stripe.secret-key=sk_test_fake",
        "loveproof.stripe.webhook-secret=whsec_test_secret",
        "loveproof.stripe.premium-price-id=price_test_fake" })
class WebhookIdempotencyTest extends PostgresIntegrationTest {

    private static final String EMAIL = "ada@example.com";

    private static final String SUBSCRIPTION = "sub_idempotent";

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
    @DisplayName("the same event delivered twice is recorded once")
    void theSameEventDeliveredTwiceIsRecordedOnce() throws Exception {
        byte[] body = WebhookDelivery.subscriptionEvent(
                "evt_replayed", SUBSCRIPTION, "active", false, periodEnd());
        String signature = WebhookDelivery.sign(body);

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", signature))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", signature))
                .andExpect(status().isOk());

        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(account().getPlan()).isEqualTo(Plan.PREMIUM);
    }

    @Test
    @DisplayName("a replayed event is answered with 200 so Stripe stops retrying")
    void aReplayedEventIsAnsweredWith200SoStripeStopsRetrying() throws Exception {
        byte[] body = WebhookDelivery.subscriptionEvent(
                "evt_replayed_200", SUBSCRIPTION, "active", false, periodEnd());
        String signature = WebhookDelivery.sign(body);

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", signature))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", signature))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a replay of an old event does not resurrect the old state")
    void aReplayOfAnOldEventDoesNotResurrectTheOldState() throws Exception {
        // The subscription is first active, then cancelled.
        deliver("evt_older", "active");
        deliver("evt_newer", "canceled");

        assertThat(account().getPlan()).isEqualTo(Plan.FREE);
        assertThat(account().getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELED);

        // The old event arrives again, late. It must not bring the account
        // back to Premium.
        deliver("evt_older", "active");

        assertThat(account().getPlan()).isEqualTo(Plan.FREE);
        assertThat(account().getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(eventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("the same event delivered many times is still recorded once")
    void theSameEventDeliveredManyTimesIsStillRecordedOnce() throws Exception {
        byte[] body = WebhookDelivery.subscriptionEvent(
                "evt_many", SUBSCRIPTION, "active", false, periodEnd());
        String signature = WebhookDelivery.sign(body);

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/billing/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .header("Stripe-Signature", signature))
                    .andExpect(status().isOk());
        }

        assertThat(eventRepository.count()).isEqualTo(1);
    }

    /**
     * Delivers a signed subscription event.
     *
     * @param eventId the event's identifier
     * @param status  Stripe's subscription status
     * @throws Exception when the request fails
     */
    private void deliver(String eventId, String status) throws Exception {
        byte[] body = WebhookDelivery.subscriptionEvent(
                eventId, SUBSCRIPTION, status, false, periodEnd());

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
        account.setStripeCustomerId("cus_idempotent");
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

        /**
         * Forgets everything.
         */
        void reset() {
            // Nothing to forget.
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
            return new SubscriptionSnapshot(subscriptionId, "active", false, Instant.now());
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
