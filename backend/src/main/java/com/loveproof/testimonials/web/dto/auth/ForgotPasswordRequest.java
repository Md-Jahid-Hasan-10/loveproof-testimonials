package com.loveproof.testimonials.web.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * The body of a "I forgot my password" request.
 */
public record ForgotPasswordRequest(

        @NotBlank(message = "Enter your email address.")
        @Email(message = "Enter a valid email address.")
        String email) {
}
