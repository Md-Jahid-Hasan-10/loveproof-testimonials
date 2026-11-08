package com.loveproof.testimonials.web.dto.auth;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.AuthProvider;
import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.domain.SubscriptionStatus;

/**
 * The account as the client sees it.
 *
 * <p>The password hash is never included, and neither is the Stripe
 * identifiers or the Google subject.
 */
public record UserResponse(
        String id,
        String email,
        String displayName,
        boolean emailVerified,
        boolean hasPassword,
        Plan plan,
        SubscriptionStatus subscriptionStatus,
        boolean cancelAtPeriodEnd,
        boolean subscriptionPastDue,
        String currentPeriodEnd) {

    /**
     * Renders an account for the client.
     *
     * @param user the account
     * @return the response
     */
    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.isEmailVerified(),
                // Whether there is a password to change. The account settings
                // page offers the password form only when there is one, so a
                // Google-only account is not shown a form that could not
                // work. The hash itself is never sent.
                user.getAuthProvider() != AuthProvider.GOOGLE,
                user.getPlan(),
                user.getSubscriptionStatus(),
                user.isCancelAtPeriodEnd(),
                user.isSubscriptionPastDue(),
                user.getCurrentPeriodEnd() == null ? null : user.getCurrentPeriodEnd().toString());
    }
}
