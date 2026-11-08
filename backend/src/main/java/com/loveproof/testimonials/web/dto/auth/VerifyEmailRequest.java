package com.loveproof.testimonials.web.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * The body of an email verification.
 */
public record VerifyEmailRequest(

        @NotBlank(message = "This link is missing its token.")
        String token) {
}
