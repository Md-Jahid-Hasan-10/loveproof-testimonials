package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.StripeEventRepository;
import com.loveproof.testimonials.service.StripeGateway;
import com.loveproof.testimonials.support.PostgresIntegrationTest;
import com.loveproof.testimonials.support.WebhookDelivery;

/**
 * Verifies that the webhook endpoint only acts on a delivery it can trust.
 *
 * <p>The signature is the *only* thing that distinguishes a genuine Stripe
 * event from a request anybody could have forged, so an unverified delivery
 * must change nothing at all -- not the plan, and not the ledger.
 */
@AutoConfigureMockMvc
@Import(StripeWebhookSignatureTest.FakeGatewayConfiguration.class)
@TestPropertySource(properties = {
        "loveproof.stripe.secret-key=sk_test_fake",
        "loveproof.stripe.webhook-secret=whsec_test_secret",
        "loveproof.stripe.premium-price-id=price_test_fake" })
class StripeWebhookSignatureTest extends PostgresIntegrationTest {

    private static final String EMAIL = "ada@example.com";

    private static final String PASSWORD = "a-good-password";

    private static final String SUBSCRIPTION = "sub_signature";

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
    }

    @Test
    @DisplayName("a correctly signed delivery is accepted and changes the plan")
    void aCorrectlySignedDeliveryIsAcceptedAndChangesThePlan() throws Exception {
        AppUser account = makeAccount();
        byte[] body = WebhookDelivery.subscriptionEvent(
                "evt_signed", SUBSCRIPTION, "active", false, periodEnd());

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.sign(body)))
                .andExpect(status().isOk());

        assertThat(planOf(EMAIL)).isEqualTo(Plan.PREMIUM);
        assertThat(userRepository.findById(account.getId()).orElseThrow().getStripeSubscriptionId())
                .isEqualTo(SUBSCRIPTION);
    }

    @Test
    @DisplayName("a delivery signed with the wrong secret is refused and changes nothing")
    void aDeliverySignedWithTheWrongSecretIsRefusedAndChangesNothing() throws Exception {
        makeAccount();
        byte[] body = WebhookDelivery.subscriptionEvent(
                "evt_wrong_secret", SUBSCRIPTION, "active", false, periodEnd());

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.signWith(body, "whsec_someone_elses")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(planOf(EMAIL)).isEqualTo(Plan.FREE);
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    @DisplayName("a tampered body is refused")
    void aTamperedBodyIsRefused() throws Exception {
        makeAccount();
        byte[] original = WebhookDelivery.subscriptionEvent(
                "evt_tampered", SUBSCRIPTION, "active", false, periodEnd());
        String signature = WebhookDelivery.sign(original);

        // The body is changed after it was signed.
        byte[] tampered = WebhookDelivery.subscriptionEvent(
                "evt_tampered", SUBSCRIPTION, "canceled", false, periodEnd());

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tampered)
                        .header("Stripe-Signature", signature))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(planOf(EMAIL)).isEqualTo(Plan.FREE);
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    @DisplayName("a delivery older than the tolerance is refused")
    void aDeliveryOlderThanTheToleranceIsRefused() throws Exception {
        makeAccount();
        byte[] body = WebhookDelivery.subscriptionEvent(
                "evt_old", SUBSCRIPTION, "active", false, periodEnd());

        long tenMinutesAgo = Instant.now().minusSeconds(600).getEpochSecond();

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.sign(body, tenMinutesAgo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(planOf(EMAIL)).isEqualTo(Plan.FREE);
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    @DisplayName("a delivery without a signature header is refused")
    void aDeliveryWithoutASignatureHeaderIsRefused() throws Exception {
        makeAccount();
        byte[] body = WebhookDelivery.subscriptionEvent(
                "evt_no_header", SUBSCRIPTION, "active", false, periodEnd());

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(planOf(EMAIL)).isEqualTo(Plan.FREE);
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    @DisplayName("a body with characters outside ASCII is accepted when it is signed")
    void aBodyWithCharactersOutsideAsciiIsAcceptedWhenItIsSigned() throws Exception {
        makeAccount();

        // The signature covers the exact bytes, so a body that is not valid
        // UTF-8 would be rejected if it were decoded and re-encoded.
        byte[] body = ("{\"id\":\"evt_utf8\",\"type\":\"customer.subscription.updated\","
                + "\"data\":{\"object\":{\"id\":\"" + SUBSCRIPTION + "\","
                + "\"status\":\"active\",\"note\":\"café — naïve\"}}}").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.sign(body)))
                .andExpect(status().isOk());

        assertThat(planOf(EMAIL)).isEqualTo(Plan.PREMIUM);
    }

    @Test
    @DisplayName("the webhook needs no session and no CSRF token")
    void theWebhookNeedsNoSessionAndNoCsrfToken() throws Exception {
        makeAccount();
        byte[] body = WebhookDelivery.subscriptionEvent(
                "evt_no_csrf", SUBSCRIPTION, "active", false, periodEnd());

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.sign(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an event with no identifier is refused")
    void anEventWithNoIdentifierIsRefused() throws Exception {
        makeAccount();
        byte[] body = "{\"type\":\"customer.subscription.updated\",\"data\":{\"object\":{}}}"
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Stripe-Signature", WebhookDelivery.sign(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(planOf(EMAIL)).isEqualTo(Plan.FREE);
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
     * The account's plan.
     *
     * @param email the account's email address
     * @return the plan
     */
    private Plan planOf(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getPlan();
    }

    /**
     * Creates an account that owns the subscription.
     *
     * @return the account
     */
    private AppUser makeAccount() {
        AppUser account = userRepository.findByEmailIgnoreCase(EMAIL)
                .orElseGet(() -> userRepository.save(new AppUser(EMAIL, "{noop}", "Test User")));
        account.setStripeCustomerId("cus_signature");
        account.setStripeSubscriptionId(SUBSCRIPTION);
        return userRepository.save(account);
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
