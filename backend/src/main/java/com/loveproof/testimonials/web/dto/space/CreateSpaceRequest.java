package com.loveproof.testimonials.web.dto.space;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The request to create a Space.
 *
 * <p>The slug is optional: when it is left out the backend derives one from
 * the title, which is what most people want.
 *
 * @param title    the Space title
 * @param subtitle the Space subtitle
 * @param slug     the requested public URL segment, or {@code null}
 * @param theme    the requested theme, or {@code null} for the default
 */
public record CreateSpaceRequest(
        @NotBlank(message = "A title is required.")
        @Size(max = 120, message = "A title cannot be longer than 120 characters.")
        String title,

        @Size(max = 255, message = "A subtitle cannot be longer than 255 characters.")
        String subtitle,

        @Size(max = 80, message = "An address cannot be longer than 80 characters.")
        String slug,

        @Size(max = 32, message = "That theme name is not valid.")
        String theme) {
}
