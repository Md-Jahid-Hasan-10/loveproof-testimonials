package com.loveproof.testimonials.web.dto.embed;

import java.util.List;

import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.domain.SpaceEmbedConfig;
import com.loveproof.testimonials.domain.SpaceTestimonial;
import com.loveproof.testimonials.service.EmbedService;

/**
 * The public data an embed renders.
 *
 * <p>This is the whole of what a stranger learns about a Space: the title,
 * the layout the owner chose, and the testimonials they put on the Wall of
 * Love. There is no owner identifier, no slug, and no email address
 * anywhere in it (PRD §4.8).
 */
public record PublicEmbedResponse(
        String title,
        String subtitle,
        String layout,
        String themeMode,
        String backgroundColor,
        boolean animation,
        List<EmbedTestimonialResponse> testimonials) {

    /**
     * Builds the response from the embed data.
     *
     * @param data the embed data
     * @return the response
     */
    public static PublicEmbedResponse from(EmbedService.EmbedData data) {
        Space space = data.space();
        SpaceEmbedConfig config = data.config();

        List<EmbedTestimonialResponse> testimonials = data.testimonials().stream()
                .map(testimonial -> EmbedTestimonialResponse.from(testimonial, space))
                .toList();

        return new PublicEmbedResponse(
                space.getTitle(),
                space.getSubtitle(),
                config.getLayout(),
                config.getThemeMode(),
                config.getBackgroundColor(),
                config.isAnimation(),
                testimonials);
    }

    /**
     * Builds the response from a Space and its configuration.
     *
     * @param space        the Space
     * @param config       the configuration
     * @param testimonials the Wall of Love entries
     * @return the response
     */
    public static PublicEmbedResponse of(
            Space space,
            SpaceEmbedConfig config,
            List<SpaceTestimonial> testimonials) {

        return from(new EmbedService.EmbedData(space, config, testimonials));
    }
}
