package com.loveproof.testimonials.web.dto.space;

import java.time.Instant;
import java.util.UUID;

import com.loveproof.testimonials.domain.Space;

/**
 * A Space as the interface sees it.
 *
 * <p>The response carries the public URL as well as the slug, so the
 * interface never has to know how the address is put together.
 *
 * @param id                   the identifier
 * @param title                the title
 * @param subtitle             the subtitle
 * @param slug                 the public URL segment
 * @param publicUrl            the public URL of the Space
 * @param fieldCompany         whether the company field is shown
 * @param fieldCompanyRequired whether the company field is required
 * @param fieldSocial          whether the social field is shown
 * @param fieldSocialRequired  whether the social field is required
 * @param fieldWebsite         whether the website field is shown
 * @param fieldWebsiteRequired whether the website field is required
 * @param fieldJobTitle        whether the job title field is shown
 * @param fieldJobTitleRequired whether the job title field is required
 * @param ratingEnabled        whether ratings are on
 * @param theme                the theme key
 * @param slugLocked           whether the address can still change
 * @param editable             whether the owner may change it
 * @param createdAt            when it was created
 * @param updatedAt            when it was last changed
 */
public record SpaceResponse(
        UUID id,
        String title,
        String subtitle,
        String slug,
        String publicUrl,
        boolean fieldCompany,
        boolean fieldCompanyRequired,
        boolean fieldSocial,
        boolean fieldSocialRequired,
        boolean fieldWebsite,
        boolean fieldWebsiteRequired,
        boolean fieldJobTitle,
        boolean fieldJobTitleRequired,
        boolean ratingEnabled,
        String theme,
        boolean slugLocked,
        boolean editable,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Builds the response.
     *
     * @param space    the Space
     * @param editable whether the owner may change it
     * @param baseUrl  the public base URL of the installation
     * @return the response
     */
    public static SpaceResponse from(Space space, boolean editable, String baseUrl) {
        return new SpaceResponse(
                space.getId(),
                space.getTitle(),
                space.getSubtitle(),
                space.getSlug(),
                baseUrl + "/s/" + space.getSlug(),
                space.isFieldCompany(),
                space.isFieldCompanyRequired(),
                space.isFieldSocial(),
                space.isFieldSocialRequired(),
                space.isFieldWebsite(),
                space.isFieldWebsiteRequired(),
                space.isFieldJobTitle(),
                space.isFieldJobTitleRequired(),
                space.isRatingEnabled(),
                space.getTheme(),
                space.isSlugLocked(),
                editable,
                space.getCreatedAt(),
                space.getUpdatedAt());
    }
}
