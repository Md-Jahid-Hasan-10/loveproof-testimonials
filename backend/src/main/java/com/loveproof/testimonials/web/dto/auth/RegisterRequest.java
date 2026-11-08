package com.loveproof.testimonials.web.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of a registration request.
 */
public record RegisterRequest(

        @NotBlank(message = "Enter your email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "That email address is too long.")
        String email,

        @NotBlank(message = "Choose a password.")
        @Size(min = 8, message = "Your password must be at least 8 characters.")
        String password,

        @Size(max = 100, message = "That name is too long.")
        String displayName) {
}
