package com.loveproof.testimonials.web.dto.embed;

import com.loveproof.testimonials.domain.SpaceEmbedConfig;

/**
 * A Space's embed configuration, as the owner sees it.
 *
 * <p>Alongside the settings it carries the options the account may choose,
 * so the interface can mark the premium ones as locked without a second
 * request.
 */
public record EmbedConfigResponse(
        String spaceId,
        String embedToken,
        String layout,
        String themeMode,
        String backgroundColor,
        boolean animation,
        String scriptUrl,
        String dataUrl) {

    /**
     * Builds the response from a configuration.
     *
     * @param config  the configuration
     * @param baseUrl the public base URL of this installation
     * @param spaceId the Space
     * @return the response
     */
    public static EmbedConfigResponse from(
            SpaceEmbedConfig config, String baseUrl, String spaceId) {

        String root = baseUrl.replaceAll("/+$", "");

        return new EmbedConfigResponse(
                spaceId,
                config.getEmbedToken(),
                config.getLayout(),
                config.getThemeMode(),
                config.getBackgroundColor(),
                config.isAnimation(),
                root + "/embed/loveproof.js",
                root + "/api/app/public/embeds/" + config.getEmbedToken());
    }
}
