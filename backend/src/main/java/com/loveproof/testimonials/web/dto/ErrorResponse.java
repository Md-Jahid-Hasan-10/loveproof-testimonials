package com.loveproof.testimonials.web.dto;

import java.time.Instant;
import java.util.Map;

/**
 * The shared error envelope returned by every failing endpoint (FR-6.1).
 *
 * @param code        a machine-readable error code, e.g. {@code VALIDATION_FAILED}
 * @param message     a human-readable message
 * @param fieldErrors the per-field messages, empty when the failure is not field-specific
 * @param timestamp   when the error occurred
 * @param path        the request path
 */
public record ErrorResponse(
        String code,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp,
        String path) {
}
