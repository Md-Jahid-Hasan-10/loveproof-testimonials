package com.loveproof.testimonials.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.config.CurrentUser;
import com.loveproof.testimonials.domain.SpaceTestimonial;
import com.loveproof.testimonials.service.SpaceInboxService;
import com.loveproof.testimonials.web.dto.PageResponse;
import com.loveproof.testimonials.web.dto.space.CurationRequest;
import com.loveproof.testimonials.web.dto.space.EditTestimonialRequest;
import com.loveproof.testimonials.web.dto.space.SpaceTestimonialResponse;

import jakarta.validation.Valid;

/**
 * The owner's inbox for one Space.
 *
 * <p>Every endpoint here is scoped to the signed-in account. A Space
 * belonging to somebody else is answered as "not found", so the API never
 * confirms that another account's Space exists.
 */
@RestController
@RequestMapping("/api/app/spaces/{spaceId}/testimonials")
public class AppSpaceInboxController {

    private final SpaceInboxService inboxService;

    /**
     * Creates the controller.
     *
     * @param inboxService the inbox rules
     */
    public AppSpaceInboxController(SpaceInboxService inboxService) {
        this.inboxService = inboxService;
    }

    /**
     * Reads the inbox: favourites first, then newest first.
     *
     * @param spaceId    the Space
     * @param search     the free-text search over the name, email and text
     * @param favorite   only favourites, when {@code true}; only the others,
     *                   when {@code false}
     * @param wallOfLove only Wall of Love entries, when {@code true}
     * @param hidden     only hidden ones, when {@code true}
     * @param rating     only this rating
     * @param page       the 0-based page number
     * @param size       the page size
     * @return the page of testimonials
     */
    @GetMapping
    public PageResponse<SpaceTestimonialResponse> list(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) Boolean wallOfLove,
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<SpaceTestimonialResponse> result = PageResponse.from(
                inboxService.list(
                        CurrentUser.id(),
                        spaceId,
                        search,
                        favorite,
                        wallOfLove,
                        hidden,
                        rating,
                        page,
                        size),
                SpaceTestimonialResponse::from);

        return result;
    }

    /**
     * Changes a testimonial's curation flags.
     *
     * @param spaceId the Space
     * @param id      the testimonial
     * @param request the flags to change
     * @return the updated testimonial
     */
    @PatchMapping("/{id}")
    public SpaceTestimonialResponse curate(
            @PathVariable UUID spaceId,
            @PathVariable UUID id,
            @RequestBody CurationRequest request) {

        SpaceTestimonial updated = inboxService.curate(
                CurrentUser.id(),
                spaceId,
                id,
                request.favorite(),
                request.wallOfLove(),
                request.visible());

        return SpaceTestimonialResponse.from(updated);
    }

    /**
     * Edits the text of a testimonial.
     *
     * @param spaceId the Space
     * @param id      the testimonial
     * @param request the new text
     * @return the updated testimonial
     */
    @PutMapping("/{id}")
    public SpaceTestimonialResponse edit(
            @PathVariable UUID spaceId,
            @PathVariable UUID id,
            @Valid @RequestBody EditTestimonialRequest request) {

        SpaceTestimonial updated = inboxService.edit(
                CurrentUser.id(), spaceId, id, request.content());
        return SpaceTestimonialResponse.from(updated);
    }

    /**
     * Deletes a testimonial permanently.
     *
     * @param spaceId the Space
     * @param id      the testimonial
     * @return no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID spaceId, @PathVariable UUID id) {
        inboxService.delete(CurrentUser.id(), spaceId, id);
        return ResponseEntity.noContent().build();
    }
}
