package com.loveproof.testimonials.web.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of a profile update.
 */
public record UpdateProfileRequest(

        @NotBlank(message = "Enter a name.")
        @Size(max = 100, message = "That name is too long.")
        String displayName) {
}
