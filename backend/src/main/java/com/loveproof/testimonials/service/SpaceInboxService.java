package com.loveproof.testimonials.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.domain.SpaceTestimonial;
import com.loveproof.testimonials.exception.ConflictException;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.repository.SpaceRepository;
import com.loveproof.testimonials.repository.SpaceTestimonialRepository;

/**
 * The owner's inbox for one Space, and the curation actions on it.
 *
 * <p>Curation is the owner's alone. Adding a testimonial to the Wall of Love
 * is always an explicit action, whatever the submitter consented to
 * (PRD §4.7) -- consent is the submitter's permission to be published at all,
 * not a request to be featured.
 *
 * <p>A read-only Space can still be read. Every mutating action is refused
 * there, and refused on the server rather than only in the interface
 * (PRD §4.7, §4.9).
 */
@Service
public class SpaceInboxService {

    private final SpaceRepository spaceRepository;
    private final SpaceTestimonialRepository testimonialRepository;
    private final SpaceService spaceService;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param spaceRepository       the Space store
     * @param testimonialRepository the testimonial store
     * @param spaceService          the Space rules, including the downgrade rule
     * @param clock                 the clock, for the update timestamps
     */
    public SpaceInboxService(
            SpaceRepository spaceRepository,
            SpaceTestimonialRepository testimonialRepository,
            SpaceService spaceService,
            Clock clock) {
        this.spaceRepository = spaceRepository;
        this.testimonialRepository = testimonialRepository;
        this.spaceService = spaceService;
        this.clock = clock;
    }

    /**
     * Reads the inbox of one Space.
     *
     * @param ownerId   the signed-in account
     * @param spaceId   the Space
     * @param search    the free-text search, or {@code null}
     * @param favorite  only favourites, or {@code null} for both
     * @param wallOfLove only Wall of Love entries, or {@code null} for both
     * @param hidden    only hidden ones, or {@code null} for both
     * @param rating    only this rating, or {@code null} for all
     * @param page      the 0-based page number
     * @param size      the page size
     * @return the page of testimonials
     */
    @Transactional(readOnly = true)
    public Page<SpaceTestimonial> list(
            UUID ownerId,
            UUID spaceId,
            String search,
            Boolean favorite,
            Boolean wallOfLove,
            Boolean hidden,
            Integer rating,
            int page,
            int size) {

        spaceService.get(ownerId, spaceId);

        String normalisedSearch = normaliseOptional(search);
        String pattern = normalisedSearch == null
                ? null
                : "%" + normalisedSearch.toLowerCase(Locale.ROOT) + "%";

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));

        return testimonialRepository.findInboxFiltered(
                spaceId, pattern, favorite, wallOfLove, hidden, rating, pageable);
    }

    /**
     * Changes a testimonial's curation flags.
     *
     * <p>Each flag is optional: a missing one means "leave this alone", so a
     * request that only favourites a testimonial does not touch its Wall of
     * Love or visibility state.
     *
     * @param ownerId    the signed-in account
     * @param spaceId    the Space
     * @param id         the testimonial
     * @param favorite   the new favourite state, or {@code null}
     * @param wallOfLove the new Wall of Love state, or {@code null}
     * @param visible    the new visibility state, or {@code null}
     * @return the updated testimonial
     */
    @Transactional
    public SpaceTestimonial curate(
            UUID ownerId,
            UUID spaceId,
            UUID id,
            Boolean favorite,
            Boolean wallOfLove,
            Boolean visible) {

        SpaceTestimonial testimonial = requireEditable(ownerId, spaceId, id);

        if (favorite != null) {
            testimonial.setFavorite(favorite);
        }
        if (wallOfLove != null) {
            testimonial.setWallOfLove(wallOfLove);
        }
        if (visible != null) {
            testimonial.setVisible(visible);
        }

        testimonial.setUpdatedAt(clock.instant());
        return testimonialRepository.save(testimonial);
    }

    /**
     * Edits the text of a testimonial.
     *
     * @param ownerId the signed-in account
     * @param spaceId the Space
     * @param id      the testimonial
     * @param content the new text
     * @return the updated testimonial
     */
    @Transactional
    public SpaceTestimonial edit(UUID ownerId, UUID spaceId, UUID id, String content) {
        SpaceTestimonial testimonial = requireEditable(ownerId, spaceId, id);
        String trimmed = content == null ? "" : content.strip();
        if (trimmed.isEmpty()) {
            throw new ConflictException("The testimonial text cannot be empty.");
        }
        if (trimmed.length() > 2000) {
            throw new ConflictException("The testimonial text must be at most 2000 characters.");
        }
        testimonial.setContent(trimmed);
        testimonial.setUpdatedAt(clock.instant());
        return testimonialRepository.save(testimonial);
    }

    /**
     * Deletes a testimonial permanently.
     *
     * @param ownerId the signed-in account
     * @param spaceId the Space
     * @param id      the testimonial
     */
    @Transactional
    public void delete(UUID ownerId, UUID spaceId, UUID id) {
        SpaceTestimonial testimonial = requireEditable(ownerId, spaceId, id);
        testimonialRepository.delete(testimonial);
    }

    /**
     * Loads a testimonial the owner is allowed to change.
     *
     * <p>The Space has to belong to the account, and it has to be editable:
     * a read-only Space can be read but not changed.
     *
     * @param ownerId the signed-in account
     * @param spaceId the Space
     * @param id      the testimonial
     * @return the testimonial
     * @throws NotFoundException when the Space or the testimonial does not
     *         belong to the account
     * @throws ConflictException when the Space is read-only
     */
    private SpaceTestimonial requireEditable(UUID ownerId, UUID spaceId, UUID id) {
        Space space = spaceService.get(ownerId, spaceId);

        if (!spaceService.isEditable(ownerId, spaceId)) {
            throw new ConflictException(
                    "This Space is read-only, so its testimonials cannot be changed.");
        }

        return testimonialRepository.findBySpaceIdAndId(spaceId, id)
                .orElseThrow(() -> new NotFoundException("That testimonial does not exist."));
    }

    /**
     * Normalises an optional free-text field.
     *
     * @param value the raw value
     * @return the normalised value, or {@code null}
     */
    private static String normaliseOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
