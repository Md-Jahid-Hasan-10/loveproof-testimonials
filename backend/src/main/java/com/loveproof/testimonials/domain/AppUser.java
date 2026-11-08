package com.loveproof.testimonials.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A customer account.
 *
 * <p>One account owns many {@link Space}s and sees only its own data. The
 * entity is deliberately plain: the rules live in the service layer, and the
 * database enforces the invariants the entity cannot (the unique email, the
 * unique Google subject).
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    /** Null only for a Google-only account. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 16)
    private AuthProvider authProvider = AuthProvider.PASSWORD;

    @Column(name = "google_subject", length = 255)
    private String googleSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 16)
    private Plan plan = Plan.FREE;

    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    /**
     * The subscription status, stored as Stripe's own lowercase vocabulary.
     *
     * <p>The plan is derived from this rather than being decided separately,
     * so the webhook and the reconciliation job cannot disagree about which
     * accounts are Premium.
     */
    @jakarta.persistence.Convert(converter = SubscriptionStatusConverter.class)
    @Column(name = "subscription_status", nullable = false, length = 32)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.NONE;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "subscription_past_due", nullable = false)
    private boolean subscriptionPastDue;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** For JPA. */
    protected AppUser() {
        // Required by the persistence provider.
    }

    /**
     * Creates an account that signs in with a password.
     *
     * @param email         the email address, stored lower-cased
     * @param passwordHash  the BCrypt hash of the password
     * @param displayName   the name to show
     */
    public AppUser(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public void setGoogleSubject(String googleSubject) {
        this.googleSubject = googleSubject;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    public SubscriptionStatus getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(SubscriptionStatus subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus == null ? SubscriptionStatus.NONE : subscriptionStatus;
    }

    /**
     * Applies a whole subscription state at once.
     *
     * <p>This is the only place a plan changes. The status is the single
     * input, and the plan, the past-due flag and the cancellation flag are
     * all derived from it here rather than being set by each caller. That is
     * what stops the webhook and the reconciliation job from reaching
     * different conclusions about the same subscription (PRD §4.9).
     *
     * <p>The cancellation flag is a fact about the subscription rather than
     * about the status: a subscription that is still running but ends at the
     * close of the period is still Premium, and still says so.
     *
     * @param status            the subscription status
     * @param cancelAtPeriodEnd whether the subscription ends at the close of
     *                          the paid period
     * @param periodEnd         when the paid period ends
     */
    public void applySubscriptionState(
            SubscriptionStatus status,
            boolean cancelAtPeriodEnd,
            Instant periodEnd) {
        SubscriptionStatus resolved = status == null ? SubscriptionStatus.NONE : status;

        this.subscriptionStatus = resolved;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.currentPeriodEnd = periodEnd;
        this.subscriptionPastDue = resolved == SubscriptionStatus.PAST_DUE;
        this.plan = resolved.grantsPremium() ? Plan.PREMIUM : Plan.FREE;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) {
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public boolean isSubscriptionPastDue() {
        return subscriptionPastDue;
    }

    public void setSubscriptionPastDue(boolean subscriptionPastDue) {
        this.subscriptionPastDue = subscriptionPastDue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
