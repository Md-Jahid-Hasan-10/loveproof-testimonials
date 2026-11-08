package com.loveproof.testimonials.web.dto.dashboard;

import java.util.List;

import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.service.DashboardAnalyticsService;

/**
 * The dashboard, as the client sees it.
 *
 * <p>The Stripe identifiers are never included: they are the server's to
 * keep, and the dashboard needs nothing from them.
 */
public record DashboardResponse(
        long totalSpaces,
        long totalTestimonials,
        long uniqueSubmitters,
        List<DashboardAnalyticsService.DailyCount> daily,
        int rangeDays,
        List<DashboardAnalyticsService.RecentSubmission> recent,
        Plan plan,
        int spacesLimit) {

    /**
     * Renders the dashboard for the client.
     *
     * @param dashboard the read model
     * @return the response
     */
    public static DashboardResponse from(DashboardAnalyticsService.Dashboard dashboard) {
        return new DashboardResponse(
                dashboard.totalSpaces(),
                dashboard.totalTestimonials(),
                dashboard.uniqueSubmitters(),
                dashboard.daily(),
                dashboard.rangeDays(),
                dashboard.recent(),
                dashboard.plan(),
                dashboard.spacesLimit());
    }
}
