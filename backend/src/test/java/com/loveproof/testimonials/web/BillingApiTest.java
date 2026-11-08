package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.service.StripeGateway;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the billing endpoints.
 *
 * <p>A fake gateway stands in for Stripe, so the tests exercise the billing
 * rules without a network. The fake records what it was asked for, which is
 * how the tests check the rule that matters most: **none of these endpoints
 * changes a plan**. The plan changes when the webhook says so, and the
 * checkout redirect is not a purchase.
 */
@AutoConfigureMockMvc
@Import(BillingApiTest.FakeGatewayConfiguration.class)
@TestPropertySource(properties = {
        "loveproof.stripe.secret-key=sk_test_fake",
        "loveproof.stripe.webhook-secret=whsec_test_secret",
        "loveproof.stripe.premium-price-id=price_test_fake" })
class BillingApiTest extends PostgresIntegrationTest {

    private static final String EMAIL = "ada@example.com";

    private static final String PASSWORD = "a-good-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private FakeStripeGateway gateway;

    @BeforeEach
    void clearEverything() {
        userRepository.deleteAll();
        gateway.reset();
    }

    @Test
    @DisplayName("the billing endpoint refuses an anonymous caller")
    void theBillingEndpointRefusesAnAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/app/billing"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("the billing state reports the current plan and both plans' limits")
    void theBillingStateReportsTheCurrentPlanAndBothPlansLimits() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/app/billing").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.subscriptionStatus").value("NONE"))
                .andExpect(jsonPath("$.billingConfigured").value(true))
                .andExpect(jsonPath("$.plans.length()").value(2))
                .andExpect(jsonPath("$.plans[0].plan").value("FREE"))
                .andExpect(jsonPath("$.plans[0].spaces").value(3))
                .andExpect(jsonPath("$.plans[0].testimonialsPerSpace").value(100))
                .andExpect(jsonPath("$.plans[1].plan").value("PREMIUM"))
                .andExpect(jsonPath("$.plans[1].spaces").value(25))
                .andExpect(jsonPath("$.plans[1].testimonialsPerSpace").value(1000));
    }

    @Test
    @DisplayName("the checkout returns a URL and does not change the plan")
    void theCheckoutReturnsAUrlAndDoesNotChangeThePlan() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/app/billing/checkout").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.test/session"));

        // The plan is unchanged: the customer has not paid anything yet.
        assertThat(planOf(EMAIL)).isEqualTo(Plan.FREE);
    }

    @Test
    @DisplayName("the checkout attaches the account to the subscription")
    void theCheckoutAttachesTheAccountToTheSubscription() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/app/billing/checkout").session(session).with(csrf()))
                .andExpect(status().isOk());

        // The account's identifier travels with the subscription, so a webhook
        // for it can find its way home.
        assertThat(gateway.lastAccountId).isNotNull();
        assertThat(gateway.lastAccountId).isEqualTo(accountIdOf(EMAIL).toString());
    }

    @Test
    @DisplayName("the checkout reuses the Stripe customer on a second attempt")
    void theCheckoutReusesTheStripeCustomerOnASecondAttempt() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/app/billing/checkout").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/app/billing/checkout").session(session).with(csrf()))
                .andExpect(status().isOk());

        assertThat(gateway.customersCreated).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelling asks Stripe to stop at the period end and does not change the plan")
    void cancellingAsksStripeToStopAtThePeriodEndAndDoesNotChangeThePlan() throws Exception {
        MockHttpSession session = signIn();
        makePremium(EMAIL);

        mockMvc.perform(post("/api/app/billing/cancel").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(gateway.lastCancelledSubscription).isEqualTo("sub_test");
        // The plan stays Premium until Stripe says the subscription ended.
        assertThat(planOf(EMAIL)).isEqualTo(Plan.PREMIUM);
    }

    @Test
    @DisplayName("resuming asks Stripe to keep the subscription running")
    void resumingAsksStripeToKeepTheSubscriptionRunning() throws Exception {
        MockHttpSession session = signIn();
        makePremium(EMAIL);

        mockMvc.perform(post("/api/app/billing/resume").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(gateway.lastResumedSubscription).isEqualTo("sub_test");
    }

    @Test
    @DisplayName("cancelling without a subscription is a 404")
    void cancellingWithoutASubscriptionIsA404() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/app/billing/cancel").session(session).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("the portal opens for an account with a subscription")
    void thePortalOpensForAnAccountWithASubscription() throws Exception {
        MockHttpSession session = signIn();
        makePremium(EMAIL);

        mockMvc.perform(post("/api/app/billing/portal").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://billing.stripe.test/session"));
    }

    @Test
    @DisplayName("a state-changing request without a CSRF token is refused")
    void aStateChangingRequestWithoutACsrfTokenIsRefused() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/app/billing/checkout").session(session))
                .andExpect(status().isForbidden());
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
     * The account's identifier.
     *
     * @param email the account's email address
     * @return the identifier
     */
    private java.util.UUID accountIdOf(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    /**
     * Puts an account on the Premium plan with a live subscription.
     *
     * @param email the account's email address
     */
    private void makePremium(String email) {
        AppUser account = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        account.setPlan(Plan.PREMIUM);
        account.setStripeCustomerId("cus_test");
        account.setStripeSubscriptionId("sub_test");
        userRepository.save(account);
    }

    /**
     * Signs in as the test customer, registering the account if needed.
     *
     * @return the session
     * @throws Exception when the sign-in fails
     */
    private MockHttpSession signIn() throws Exception {
        mockMvc.perform(post("/api/app/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD
                        + "\",\"displayName\":\"Test User\"}"));

        return (MockHttpSession) mockMvc.perform(post("/api/app/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    /**
     * The CSRF token post-processor.
     *
     * @return the post-processor
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.csrf();
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
     *
     * <p>It records what it was asked for, so a test can check that the
     * billing endpoints asked the right thing and changed nothing.
     */
    static class FakeStripeGateway implements StripeGateway {

        /** How many customers were created. */
        int customersCreated;

        /** The account identifier the last checkout carried. */
        String lastAccountId;

        /** The subscription the last cancel named. */
        String lastCancelledSubscription;

        /** The subscription the last resume named. */
        String lastResumedSubscription;

        /**
         * Forgets everything.
         */
        void reset() {
            customersCreated = 0;
            lastAccountId = null;
            lastCancelledSubscription = null;
            lastResumedSubscription = null;
        }

        @Override
        public String createCustomer(String email, String name) {
            customersCreated++;
            return "cus_test";
        }

        @Override
        public String createCheckoutSession(
                String customerId, String priceId, String accountId,
                String successUrl, String cancelUrl) {
            lastAccountId = accountId;
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
            lastCancelledSubscription = subscriptionId;
        }

        @Override
        public void resumeSubscription(String subscriptionId) {
            lastResumedSubscription = subscriptionId;
        }
    }
}
