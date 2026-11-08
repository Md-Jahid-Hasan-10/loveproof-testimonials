package com.loveproof.testimonials.service;

import java.util.List;

import com.loveproof.testimonials.domain.Plan;

/**
 * The plans, with the features and limits each one carries.
 *
 * <p>The catalogue lives in the backend rather than in the frontend because
 * the limits are the server's to enforce. The numbers are read from
 * {@link PlanLimits} rather than written out here, so the page a customer
 * reads and the limit the server enforces cannot drift apart.
 *
 * <p>Mirrors {@link EmbedCatalog} and {@link ThemeCatalog}: a catalogue of
 * what exists, and one place that says what each option needs.
 */
public final class PlanCatalog {

    /** What the Premium plan costs, as shown on the billing page. */
    public static final String PREMIUM_PRICE = "$9.99/month";

    /**
     * One plan.
     *
     * @param plan                   the plan
     * @param label                  the name shown in the interface
     * @param price                  what the plan costs
     * @param spaces                 how many Spaces the plan allows
     * @param testimonialsPerSpace   how many testimonials each Space allows
     * @param features               what the plan includes
     */
    public record PlanOption(
            Plan plan,
            String label,
            String price,
            int spaces,
            int testimonialsPerSpace,
            List<String> features) {
    }

    /** Every plan, in the order the picker shows them. */
    private static final List<PlanOption> PLANS = List.of(
            new PlanOption(
                    Plan.FREE,
                    "Free",
                    "Free",
                    PlanLimits.FREE_SPACES,
                    PlanLimits.FREE_TESTIMONIALS_PER_SPACE,
                    List.of(
                            "3 Spaces",
                            "100 testimonials per Space",
                            "Masonry embed",
                            "Light and dark mode")),
            new PlanOption(
                    Plan.PREMIUM,
                    "Premium",
                    PREMIUM_PRICE,
                    PlanLimits.PREMIUM_SPACES,
                    PlanLimits.PREMIUM_TESTIMONIALS_PER_SPACE,
                    List.of(
                            "25 Spaces",
                            "1,000 testimonials per Space",
                            "Premium themes",
                            "Carousel embed",
                            "Background colour",
                            "Animation")));

    private PlanCatalog() {
        // A holder for constants.
    }

    /**
     * Every plan.
     *
     * @return the plans
     */
    public static List<PlanOption> all() {
        return PLANS;
    }

    /**
     * Looks a plan up.
     *
     * @param plan the plan
     * @return the plan's details
     */
    public static PlanOption forPlan(Plan plan) {
        return PLANS.stream()
                .filter(candidate -> candidate.plan() == plan)
                .findFirst()
                .orElse(PLANS.get(0));
    }
}
