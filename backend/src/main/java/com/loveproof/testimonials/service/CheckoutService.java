package com.loveproof.testimonials.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.config.StripeProperties;
import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.repository.AppUserRepository;

/**
 * The actions a customer takes on their subscription.
 *
 * <p>Every method here asks Stripe to do something and then **writes
 * nothing** about the plan. The plan changes when the webhook says it has,
 * and not before. A customer who reaches the success URL has not paid
 * anything yet as far as this service is concerned -- Stripe has not
 * confirmed it -- so treating the redirect as a purchase would let anybody
 * upgrade themselves by visiting a URL.
 *
 * <p>The one thing that is written locally is the Stripe customer
 * identifier, which is needed to open the billing portal later and is not a
 * statement about the plan.
 */
@Service
public class CheckoutService {

    private final AppUserRepository userRepository;

    private final StripeGateway gateway;

    private final StripeProperties properties;

    private final String appBaseUrl;

    /**
     * Creates the service.
     *
     * @param userRepository the account store
     * @param gateway        the Stripe operations
     * @param properties     the Stripe configuration
     * @param appBaseUrl     the public base URL of this installation
     */
    public CheckoutService(
            AppUserRepository userRepository,
            StripeGateway gateway,
            StripeProperties properties,
            @Value("${loveproof.app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.userRepository = userRepository;
        this.gateway = gateway;
        this.properties = properties;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Creates a Checkout Session for the Premium plan.
     *
     * <p>The account's identifier is attached to the subscription's metadata,
     * so a webhook for the resulting subscription can find its way home even
     * before the subscription identifier has been stored locally.
     *
     * @param ownerId the signed-in account
     * @return the URL of the Checkout Session
     */
    @Transactional
    public String createCheckoutSession(UUID ownerId) {
        AppUser account = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("That account does not exist."));

        String customerId = customerFor(account);
        String successUrl = appBaseUrl + "/app/billing?checkout=success&session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = appBaseUrl + "/app/billing?checkout=cancelled";

        return gateway.createCheckoutSession(
                customerId,
                properties.getPremiumPriceId(),
                ownerId.toString(),
                successUrl,
                cancelUrl);
    }

    /**
     * Opens the Stripe billing portal.
     *
     * <p>Offered alongside the direct cancel and resume, not instead of them:
     * the portal is where a customer manages their card, and the two direct
     * actions are simpler for the two things they actually want to do.
     *
     * @param ownerId the signed-in account
     * @return the URL of the portal session
     */
    @Transactional
    public String createPortalSession(UUID ownerId) {
        AppUser account = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("That account does not exist."));

        if (account.getStripeCustomerId() == null || account.getStripeCustomerId().isBlank()) {
            throw new NotFoundException(
                    "There is no subscription to manage yet. Upgrade first, then you can "
                            + "manage your payment details here.");
        }

        return gateway.createPortalSession(account.getStripeCustomerId(), appBaseUrl + "/app/billing");
    }

    /**
     * Asks Stripe to cancel the subscription at the close of the paid period.
     *
     * <p>Nothing is written locally: the plan stays Premium until Stripe says
     * the subscription has ended, and the webhook carries that.
     *
     * @param ownerId the signed-in account
     */
    @Transactional
    public void cancel(UUID ownerId) {
        AppUser account = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("That account does not exist."));

        String subscriptionId = account.getStripeSubscriptionId();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new NotFoundException("There is no subscription to cancel.");
        }

        gateway.cancelSubscription(subscriptionId);
    }

    /**
     * Asks Stripe to keep the subscription running.
     *
     * <p>Nothing is written locally: the webhook carries the new state.
     *
     * @param ownerId the signed-in account
     */
    @Transactional
    public void resume(UUID ownerId) {
        AppUser account = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("That account does not exist."));

        String subscriptionId = account.getStripeSubscriptionId();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new NotFoundException("There is no subscription to resume.");
        }

        gateway.resumeSubscription(subscriptionId);
    }

    /**
     * The account's Stripe customer, creating one the first time.
     *
     * @param account the account
     * @return the Stripe customer identifier
     */
    private String customerFor(AppUser account) {
        if (account.getStripeCustomerId() != null && !account.getStripeCustomerId().isBlank()) {
            return account.getStripeCustomerId();
        }

        String customerId = gateway.createCustomer(account.getEmail(), account.getDisplayName());
        account.setStripeCustomerId(customerId);
        userRepository.save(account);
        return customerId;
    }
}
