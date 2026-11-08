package com.loveproof.testimonials.web.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of a password reset.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "This link is missing its token.")
        String token,

        @NotBlank(message = "Choose a password.")
        @Size(min = 8, message = "Your password must be at least 8 characters.")
        String password) {
}
