package com.loveproof.testimonials.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health endpoint.
 *
 * <p>Distinct from Spring Actuator's {@code /actuator/health}, which is an operational probe. This
 * endpoint is part of the public API surface under {@code /api}, so the frontend and any external
 * checker can confirm the API is up without depending on the actuator dependency.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * Liveness check for the API itself.
     *
     * @return the service name, status and current server time
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "loveproof-testimonials",
                "status", "UP",
                "timestamp", Instant.now().toString());
    }
}
