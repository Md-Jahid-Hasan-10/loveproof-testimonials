package com.loveproof.testimonials.web.dto.space;

import jakarta.validation.constraints.Size;

/**
 * The request to update a Space.
 *
 * <p>Every field is optional and a {@code null} means "leave this alone",
 * so a partial update never resets the fields it did not mention.
 *
 * @param title                the new title
 * @param subtitle             the new subtitle
 * @param slug                 the new public URL segment
 * @param theme                the new theme
 * @param ratingEnabled        whether ratings are on
 * @param fieldCompany         whether the company field is shown
 * @param fieldCompanyRequired whether the company field is required
 * @param fieldSocial          whether the social field is shown
 * @param fieldSocialRequired  whether the social field is required
 * @param fieldWebsite         whether the website field is shown
 * @param fieldWebsiteRequired whether the website field is required
 * @param fieldJobTitle        whether the job title field is shown
 * @param fieldJobTitleRequired whether the job title field is required
 */
public record UpdateSpaceRequest(
        @Size(max = 120, message = "A title cannot be longer than 120 characters.")
        String title,

        @Size(max = 255, message = "A subtitle cannot be longer than 255 characters.")
        String subtitle,

        @Size(max = 80, message = "An address cannot be longer than 80 characters.")
        String slug,

        @Size(max = 32, message = "That theme name is not valid.")
        String theme,

        Boolean ratingEnabled,
        Boolean fieldCompany,
        Boolean fieldCompanyRequired,
        Boolean fieldSocial,
        Boolean fieldSocialRequired,
        Boolean fieldWebsite,
        Boolean fieldWebsiteRequired,
        Boolean fieldJobTitle,
        Boolean fieldJobTitleRequired) {
}
