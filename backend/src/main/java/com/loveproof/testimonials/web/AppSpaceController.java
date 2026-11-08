package com.loveproof.testimonials.web;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.config.CurrentUser;
import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.service.SpaceService;
import com.loveproof.testimonials.service.ThemeCatalog;
import com.loveproof.testimonials.web.dto.space.CreateSpaceRequest;
import com.loveproof.testimonials.web.dto.space.SpaceResponse;
import com.loveproof.testimonials.web.dto.space.UpdateSpaceRequest;

import jakarta.validation.Valid;

/**
 * The Spaces endpoints for the signed-in customer.
 *
 * <p>Everything here is scoped to the signed-in account. A Space belonging to
 * somebody else is answered as "not found", so the API never confirms that
 * another account's Space exists.
 */
@RestController
@RequestMapping("/api/app/spaces")
public class AppSpaceController {

    private final SpaceService spaceService;

    private final String appBaseUrl;

    /**
     * Creates the controller.
     *
     * @param spaceService the Space rules
     * @param appBaseUrl   the public base URL of the installation
     */
    public AppSpaceController(
            SpaceService spaceService,
            @Value("${loveproof.app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.spaceService = spaceService;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Lists the signed-in customer's Spaces, newest first.
     *
     * @return the Spaces
     */
    @GetMapping
    public List<SpaceResponse> list() {
        UUID ownerId = CurrentUser.id();
        return spaceService.list(ownerId).stream()
                .map(space -> SpaceResponse.from(
                        space,
                        spaceService.isEditable(ownerId, space.getId()),
                        appBaseUrl))
                .toList();
    }

    /**
     * Reads one Space.
     *
     * @param spaceId the Space
     * @return the Space
     */
    @GetMapping("/{spaceId}")
    public SpaceResponse get(@PathVariable UUID spaceId) {
        UUID ownerId = CurrentUser.id();
        Space space = spaceService.get(ownerId, spaceId);
        return SpaceResponse.from(space, spaceService.isEditable(ownerId, spaceId), appBaseUrl);
    }

    /**
     * Creates a Space.
     *
     * @param request the new Space
     * @return the created Space
     */
    @PostMapping
    public ResponseEntity<SpaceResponse> create(@Valid @RequestBody CreateSpaceRequest request) {
        UUID ownerId = CurrentUser.id();
        Space space = spaceService.create(
                ownerId, request.title(), request.subtitle(), request.slug(), request.theme());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SpaceResponse.from(space, true, appBaseUrl));
    }

    /**
     * Updates a Space.
     *
     * @param spaceId the Space
     * @param request what to change
     * @return the updated Space
     */
    @PutMapping("/{spaceId}")
    public SpaceResponse update(
            @PathVariable UUID spaceId,
            @Valid @RequestBody UpdateSpaceRequest request) {

        UUID ownerId = CurrentUser.id();
        Space space = spaceService.update(ownerId, spaceId, new SpaceService.SpaceChanges(
                request.title(),
                request.subtitle(),
                request.slug(),
                request.theme(),
                request.ratingEnabled(),
                request.fieldCompany(),
                request.fieldCompanyRequired(),
                request.fieldSocial(),
                request.fieldSocialRequired(),
                request.fieldWebsite(),
                request.fieldWebsiteRequired(),
                request.fieldJobTitle(),
                request.fieldJobTitleRequired()));

        return SpaceResponse.from(space, spaceService.isEditable(ownerId, spaceId), appBaseUrl);
    }

    /**
     * Deletes a Space and everything in it.
     *
     * @param spaceId the Space
     * @return no content
     */
    @DeleteMapping("/{spaceId}")
    public ResponseEntity<Void> delete(@PathVariable UUID spaceId) {
        spaceService.delete(CurrentUser.id(), spaceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * The themes a Space can use.
     *
     * <p>The premium ones are listed for everyone and marked as such, so the
     * interface can explain what an upgrade buys rather than hiding them.
     *
     * @return the themes
     */
    @GetMapping("/themes")
    public List<ThemeResponse> themes() {
        return ThemeCatalog.all().stream()
                .map(theme -> new ThemeResponse(theme.key(), theme.label(), theme.premium()))
                .toList();
    }

    /**
     * One theme.
     *
     * @param key     the theme key
     * @param label   the name shown in the interface
     * @param premium whether the theme needs a Premium plan
     */
    public record ThemeResponse(String key, String label, boolean premium) {
    }
}
