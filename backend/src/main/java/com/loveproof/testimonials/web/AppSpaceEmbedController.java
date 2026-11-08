package com.loveproof.testimonials.web;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.config.CurrentUser;
import com.loveproof.testimonials.service.EmbedService;
import com.loveproof.testimonials.web.dto.embed.EmbedConfigResponse;
import com.loveproof.testimonials.web.dto.embed.UpdateEmbedConfigRequest;

import jakarta.validation.Valid;

/**
 * The owner's embed configuration for one Space.
 *
 * <p>Every endpoint is scoped to the signed-in account. A Space belonging to
 * somebody else is answered as "not found", so the API never confirms that
 * another account's Space exists.
 */
@RestController
@RequestMapping("/api/app/spaces/{spaceId}/embed")
public class AppSpaceEmbedController {

    private final EmbedService embedService;

    private final String publicBaseUrl;

    /**
     * Creates the controller.
     *
     * @param embedService the embed rules
     * @param appBaseUrl   the public base URL of this installation
     */
    public AppSpaceEmbedController(
            EmbedService embedService,
            @Value("${loveproof.app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.embedService = embedService;
        this.publicBaseUrl = appBaseUrl;
    }

    /**
     * Reads the embed configuration, creating it the first time.
     *
     * @param spaceId the Space
     * @return the configuration
     */
    @GetMapping
    public EmbedConfigResponse read(@PathVariable UUID spaceId) {
        return EmbedConfigResponse.from(
                embedService.getOrCreate(CurrentUser.id(), spaceId),
                publicBaseUrl,
                spaceId.toString());
    }

    /**
     * Saves the embed configuration.
     *
     * @param spaceId the Space
     * @param request the settings to change
     * @return the saved configuration
     */
    @PutMapping
    public EmbedConfigResponse update(
            @PathVariable UUID spaceId,
            @Valid @RequestBody UpdateEmbedConfigRequest request) {

        return EmbedConfigResponse.from(
                embedService.update(
                        CurrentUser.id(),
                        spaceId,
                        request.layout(),
                        request.themeMode(),
                        request.backgroundColor(),
                        request.animation()),
                publicBaseUrl,
                spaceId.toString());
    }

    /**
     * Rotates the embed token.
     *
     * <p>Rotating the token stops every snippet that used the old one from
     * working, which is how the owner revokes an embed.
     *
     * @param spaceId the Space
     * @return the configuration with its new token
     */
    @PostMapping("/rotate-token")
    public EmbedConfigResponse rotateToken(@PathVariable UUID spaceId) {
        return EmbedConfigResponse.from(
                embedService.rotateToken(CurrentUser.id(), spaceId),
                publicBaseUrl,
                spaceId.toString());
    }
}
