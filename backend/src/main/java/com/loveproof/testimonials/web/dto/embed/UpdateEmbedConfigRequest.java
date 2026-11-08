package com.loveproof.testimonials.web.dto.embed;

import jakarta.validation.constraints.Pattern;

/**
 * The body of an embed configuration save.
 *
 * <p>Every field is optional: a missing one means "leave this alone", so a
 * request that only changes the layout does not reset the background colour.
 */
public record UpdateEmbedConfigRequest(

        String layout,

        String themeMode,

        @Pattern(
                regexp = "^#[0-9a-fA-F]{6}$",
                message = "The background colour must be a hex colour, such as #ffffff.")
        String backgroundColor,

        Boolean animation) {
}
