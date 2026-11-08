package com.loveproof.testimonials.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.config.CurrentUser;
import com.loveproof.testimonials.service.DashboardAnalyticsService;
import com.loveproof.testimonials.web.dto.dashboard.DashboardResponse;

/**
 * The dashboard endpoint.
 *
 * <p>Everything it returns is the signed-in account's own data. There is no
 * parameter that names another account, so there is nothing to authorise
 * beyond the session itself.
 */
@RestController
@RequestMapping("/api/app/dashboard")
public class AppDashboardController {

    private final DashboardAnalyticsService analyticsService;

    /**
     * Creates the controller.
     *
     * @param analyticsService the dashboard's read model
     */
    public AppDashboardController(DashboardAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Reads the dashboard.
     *
     * @param days how many days the chart should cover; 30 by default
     * @return the dashboard
     */
    @GetMapping
    public DashboardResponse read(
            @RequestParam(required = false) Integer days) {
        return DashboardResponse.from(analyticsService.read(CurrentUser.id(), days));
    }
}
