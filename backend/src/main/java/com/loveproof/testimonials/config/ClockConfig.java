package com.loveproof.testimonials.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application clock.
 *
 * <p>Injected rather than called statically so the duplicate window, the "last 7 days" count and
 * the error timestamps are deterministic under test (NFR-TEST-5).
 */
@Configuration
public class ClockConfig {

    /**
     * The UTC clock used across the application.
     *
     * @return the clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
