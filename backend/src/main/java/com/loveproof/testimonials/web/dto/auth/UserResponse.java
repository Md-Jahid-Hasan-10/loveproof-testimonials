package com.loveproof.testimonials.web.dto.auth;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Plan;

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
        Plan plan,
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
                user.getPlan(),
                user.isCancelAtPeriodEnd(),
                user.isSubscriptionPastDue(),
                user.getCurrentPeriodEnd() == null ? null : user.getCurrentPeriodEnd().toString());
    }
}
