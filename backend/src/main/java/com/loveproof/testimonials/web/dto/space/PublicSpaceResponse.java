package com.loveproof.testimonials.web.dto.space;

import com.loveproof.testimonials.domain.Space;

/**
 * What the public form needs to know about a Space.
 *
 * <p>This is deliberately not the owner's {@code SpaceResponse}: it carries
 * no owner identifier, no editable flag and no slug-lock state. A visitor
 * learns what to fill in and how the page looks, and nothing about the
 * account that owns it.
 */
public record PublicSpaceResponse(
        String slug,
        String title,
        String subtitle,
        String theme,
        boolean fieldCompany,
        boolean fieldCompanyRequired,
        boolean fieldSocial,
        boolean fieldSocialRequired,
        boolean fieldWebsite,
        boolean fieldWebsiteRequired,
        boolean fieldJobTitle,
        boolean fieldJobTitleRequired,
        boolean ratingEnabled,
        boolean accepting) {

    /**
     * Builds the response from a Space.
     *
     * @param space     the Space
     * @param accepting whether the Space is still collecting
     * @return the response
     */
    public static PublicSpaceResponse from(Space space, boolean accepting) {
        return new PublicSpaceResponse(
                space.getSlug(),
                space.getTitle(),
                space.getSubtitle(),
                space.getTheme(),
                space.isFieldCompany(),
                space.isFieldCompanyRequired(),
                space.isFieldSocial(),
                space.isFieldSocialRequired(),
                space.isFieldWebsite(),
                space.isFieldWebsiteRequired(),
                space.isFieldJobTitle(),
                space.isFieldJobTitleRequired(),
                space.isRatingEnabled(),
                accepting);
    }
}
