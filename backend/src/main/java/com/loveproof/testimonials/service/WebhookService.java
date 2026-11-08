package com.loveproof.testimonials.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loveproof.testimonials.config.StripeProperties;
import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.StripeEvent;
import com.loveproof.testimonials.domain.StripeEventOutcome;
import com.loveproof.testimonials.domain.SubscriptionStatus;
import com.loveproof.testimonials.exception.InvalidWebhookSignatureException;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.StripeEventRepository;

/**
 * The webhook: Stripe's events, turned into subscription state.
 *
 * <p>Three rules shape everything here:
 *
 * <ul>
 *   <li>**A delivery is only acted on if its signature verifies.** The
 *       signature is the only thing that distinguishes a genuine event from
 *       a request anybody could have forged, so an unverified delivery is
 *       refused outright and nothing is written.</li>
 *   <li>**The ledger row is written first, in the same transaction as the
 *       effect.** The primary key on Stripe's event identifier is what makes
 *       a replay a no-op: the second delivery cannot be inserted, the
 *       transaction rolls back, and no effect is applied twice. The endpoint
 *       still answers 200, so Stripe does not keep retrying an event that has
 *       already been handled.</li>
 *   <li>**The status is the authority.** The plan is derived from the
 *       subscription status, never decided separately. That is what makes
 *       "cancelling keeps Premium until the period ends" and "a failed
 *       renewal keeps Premium" fall out of one rule.</li>
 * </ul>
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    /** The event that means a subscription was created or changed. */
    static final String SUBSCRIPTION_UPDATED = "customer.subscription.updated";

    /** The event that means a subscription was created. */
    static final String SUBSCRIPTION_CREATED = "customer.subscription.created";

    /** The event that means a subscription has ended. */
    static final String SUBSCRIPTION_DELETED = "customer.subscription.deleted";

    /** The event that means a Checkout Session completed. */
    static final String CHECKOUT_COMPLETED = "checkout.session.completed";

    /** The event that means a payment failed. */
    static final String PAYMENT_FAILED = "invoice.payment_failed";

    /** The event that means a payment succeeded. */
    static final String PAYMENT_SUCCEEDED = "invoice.payment_succeeded";

    private final StripeSignatureVerifier verifier;

    private final StripeProperties properties;

    private final StripeEventRepository eventRepository;

    private final AppUserRepository userRepository;

    private final StripeGateway gateway;

    private final ObjectMapper mapper;

    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param verifier        the signature check
     * @param properties      the Stripe configuration
     * @param eventRepository the webhook ledger
     * @param userRepository  the account store
     * @param gateway         the Stripe operations
     * @param mapper          the JSON reader
     * @param clock           the clock, for the ledger's timestamps
     */
    public WebhookService(
            StripeSignatureVerifier verifier,
            StripeProperties properties,
            StripeEventRepository eventRepository,
            AppUserRepository userRepository,
            StripeGateway gateway,
            ObjectMapper mapper,
            Clock clock) {
        this.verifier = verifier;
        this.properties = properties;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.gateway = gateway;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Processes a delivery.
     *
     * @param payload the exact bytes of the body
     * @param signatureHeader the {@code Stripe-Signature} header
     * @throws InvalidWebhookSignatureException when the signature does not
     *         verify, in which case nothing is written
     */
    @Transactional
    public void process(byte[] payload, String signatureHeader) {
        if (!verifier.isValid(payload, signatureHeader, properties.getWebhookSecret())) {
            throw new InvalidWebhookSignatureException(
                    "The webhook signature is not valid, so the delivery was refused.");
        }

        JsonNode event = read(payload);
        String eventId = text(event, "id");
        String eventType = text(event, "type");

        if (eventId == null || eventId.isBlank()) {
            // Without an identifier there is nothing to make the delivery
            // idempotent, so it is refused rather than acted on.
            throw new InvalidWebhookSignatureException(
                    "The webhook event carried no identifier, so the delivery was refused.");
        }

        if (eventRepository.existsById(eventId)) {
            // A replay. The event has already been handled, so this delivery
            // changes nothing -- and answering 200 tells Stripe to stop
            // retrying.
            log.info("Ignoring a replayed Stripe event ({}).", eventId);
            return;
        }

        JsonNode object = event.path("data").path("object");
        Applied applied = apply(eventType, object);

        // The ledger row is written in the same transaction as the effect. If
        // the insert fails -- because another delivery of the same event
        // arrived first -- the whole transaction rolls back and the effect is
        // not applied twice.
        eventRepository.save(new StripeEvent(
                eventId,
                eventType == null ? "unknown" : eventType,
                applied.accountId(),
                applied.outcome(),
                clock.instant()));

        log.info("Handled the Stripe event {} ({}).", eventId, eventType);
    }

    /**
     * What an event did, and to whom.
     *
     * @param outcome   what the event did
     * @param accountId the account it was applied to, or {@code null}
     */
    private record Applied(StripeEventOutcome outcome, UUID accountId) {
    }

    /**
     * Acts on an event.
     *
     * @param eventType the event type
     * @param object    the event's subject
     * @return what the event did
     */
    private Applied apply(String eventType, JsonNode object) {
        if (eventType == null) {
            return new Applied(StripeEventOutcome.IGNORED, null);
        }

        switch (eventType) {
            case CHECKOUT_COMPLETED:
                return applyCheckout(object);
            case SUBSCRIPTION_CREATED:
            case SUBSCRIPTION_UPDATED:
                return applySubscription(object);
            case SUBSCRIPTION_DELETED:
                return applyDeleted(object);
            case PAYMENT_FAILED:
            case PAYMENT_SUCCEEDED:
                // The invoice events are recorded but change nothing. The
                // subscription event that accompanies them is what decides
                // the plan, so the two cannot disagree about it.
                return new Applied(StripeEventOutcome.IGNORED, null);
            default:
                return new Applied(StripeEventOutcome.IGNORED, null);
        }
    }

    /**
     * Acts on a completed Checkout Session.
     *
     * <p>The session names the subscription but not its state, so the
     * subscription is read from Stripe. That makes the event self-sufficient:
     * the delivery carries everything needed to reach the right state.
     *
     * @param object the Checkout Session
     * @return what the event did
     */
    private Applied applyCheckout(JsonNode object) {
        String subscriptionId = text(object, "subscription");
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return new Applied(StripeEventOutcome.IGNORED, null);
        }

        StripeGateway.SubscriptionSnapshot snapshot = gateway.retrieveSubscription(subscriptionId);
        return applySubscription(snapshot);
    }

    /**
     * Acts on a subscription event.
     *
     * @param object the subscription
     * @return what the event did
     */
    private Applied applySubscription(JsonNode object) {
        String subscriptionId = text(object, "id");
        String status = text(object, "status");
        boolean cancelAtPeriodEnd = object.path("cancel_at_period_end").asBoolean(false);
        Instant periodEnd = null;
        long epoch = object.path("current_period_end").asLong(0L);
        if (epoch > 0) {
            periodEnd = Instant.ofEpochSecond(epoch);
        }

        return applySubscription(
                new StripeGateway.SubscriptionSnapshot(subscriptionId, status, cancelAtPeriodEnd, periodEnd));
    }

    /**
     * Acts on a subscription event.
     *
     * @param snapshot the subscription's state
     * @return what the event did
     */
    private Applied applySubscription(StripeGateway.SubscriptionSnapshot snapshot) {
        if (snapshot.id() == null || snapshot.id().isBlank()) {
            return new Applied(StripeEventOutcome.IGNORED, null);
        }

        Optional<AppUser> account = findAccount(snapshot.id(), null);
        if (account.isEmpty()) {
            log.warn("A Stripe event named the subscription {}, which no account owns.",
                    snapshot.id());
            return new Applied(StripeEventOutcome.NO_ACCOUNT, null);
        }

        AppUser owner = account.get();
        owner.applySubscriptionState(
                SubscriptionStatus.fromWireValue(snapshot.status()),
                snapshot.cancelAtPeriodEnd(),
                snapshot.currentPeriodEnd());
        if (owner.getStripeSubscriptionId() == null || owner.getStripeSubscriptionId().isBlank()) {
            owner.setStripeSubscriptionId(snapshot.id());
        }
        userRepository.save(owner);

        return new Applied(StripeEventOutcome.APPLIED, owner.getId());
    }

    /**
     * Acts on a subscription that has ended.
     *
     * <p>The subscription's own status is the authority even here: an ended
     * subscription is {@code canceled} whether it was cancelled by the
     * customer or ran out because the period ended after a cancellation.
     *
     * @param object the subscription
     * @return what the event did
     */
    private Applied applyDeleted(JsonNode object) {
        String subscriptionId = text(object, "id");
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return new Applied(StripeEventOutcome.IGNORED, null);
        }

        Optional<AppUser> account = findAccount(subscriptionId, null);
        if (account.isEmpty()) {
            return new Applied(StripeEventOutcome.NO_ACCOUNT, null);
        }

        AppUser owner = account.get();
        owner.applySubscriptionState(SubscriptionStatus.CANCELED, false, owner.getCurrentPeriodEnd());
        userRepository.save(owner);

        return new Applied(StripeEventOutcome.APPLIED, owner.getId());
    }

    /**
     * Finds the account a subscription belongs to.
     *
     * <p>The subscription identifier is the first choice, because it is
     * unique. The customer identifier is the fallback, for the case where
     * the subscription has not been stored yet.
     *
     * @param subscriptionId the Stripe subscription
     * @param customerId     the Stripe customer, when it is known
     * @return the account, or empty
     */
    private Optional<AppUser> findAccount(String subscriptionId, String customerId) {
        Optional<AppUser> bySubscription = userRepository.findByStripeSubscriptionId(subscriptionId);
        if (bySubscription.isPresent()) {
            return bySubscription;
        }
        if (customerId != null && !customerId.isBlank()) {
            return userRepository.findByStripeCustomerId(customerId);
        }
        return Optional.empty();
    }

    /**
     * Reads the delivery's JSON.
     *
     * @param payload the exact bytes of the body
     * @return the parsed event
     */
    private JsonNode read(byte[] payload) {
        try {
            return mapper.readTree(payload);
        } catch (java.io.IOException ex) {
            throw new InvalidWebhookSignatureException(
                    "The webhook body could not be read, so the delivery was refused.");
        }
    }

    /**
     * Reads a text field.
     *
     * @param node  the node
     * @param field the field's name
     * @return the field's value, or {@code null}
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
