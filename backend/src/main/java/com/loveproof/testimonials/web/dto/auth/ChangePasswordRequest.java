package com.loveproof.testimonials.web.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of a password change from the account settings.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "Enter your current password.")
        String currentPassword,

        @NotBlank(message = "Choose a password.")
        @Size(min = 8, message = "Your password must be at least 8 characters.")
        String newPassword) {
}
