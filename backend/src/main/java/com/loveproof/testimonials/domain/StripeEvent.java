package com.loveproof.testimonials.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One delivered Stripe webhook event.
 *
 * <p>Rows are inserted and never updated. The primary key is Stripe's event
 * identifier, and that is the whole idempotency mechanism: a replayed
 * delivery cannot be inserted, so its transaction rolls back and no effect
 * is applied twice.
 */
@Entity
@Table(name = "stripe_event")
public class StripeEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private String eventType;

    @Column(name = "account_id", updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 32)
    private StripeEventOutcome outcome;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    /**
     * Creates an empty ledger row for JPA.
     */
    protected StripeEvent() {
        // for JPA
    }

    /**
     * Creates a ledger row.
     *
     * @param eventId   Stripe's event identifier
     * @param eventType the event type
     * @param accountId the account it was applied to, or {@code null}
     * @param outcome   what the event did
     * @param receivedAt when the event was received
     */
    public StripeEvent(
            String eventId,
            String eventType,
            UUID accountId,
            StripeEventOutcome outcome,
            Instant receivedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.accountId = accountId;
        this.outcome = outcome;
        this.receivedAt = receivedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public StripeEventOutcome getOutcome() {
        return outcome;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
