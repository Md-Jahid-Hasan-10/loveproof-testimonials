package com.loveproof.testimonials.web.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * The body of a sign-in request.
 */
public record SignInRequest(

        @NotBlank(message = "Enter your email address.")
        String email,

        @NotBlank(message = "Enter your password.")
        String password) {
}
