package com.loveproof.testimonials.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns the scheduled jobs on.
 *
 * <p>Kept in its own class so the scheduling is visible in one place: the
 * reconciliation job is the only scheduled task in the application.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
