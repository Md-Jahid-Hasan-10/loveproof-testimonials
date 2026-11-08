package com.loveproof.testimonials.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.exception.ConflictException;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.exception.PlanLimitExceededException;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.SpaceRepository;

/**
 * The Space rules.
 *
 * <p>Every method is scoped to the signed-in account. A Space belonging to
 * somebody else is answered as "not found" rather than "forbidden", so the
 * API never confirms that another account's Space exists (PRD 4.2).
 */
@Service
public class SpaceService {

    /** The longest slug the schema allows. */
    private static final int MAX_SLUG_LENGTH = 80;

    private final SpaceRepository spaceRepository;

    private final AppUserRepository userRepository;

    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param spaceRepository the Space store
     * @param userRepository  the account store
     * @param clock           the clock, for the update timestamps
     */
    public SpaceService(SpaceRepository spaceRepository, AppUserRepository userRepository, Clock clock) {
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Lists the Spaces an account owns, newest first.
     *
     * @param ownerId the owning account
     * @return the Spaces
     */
    @Transactional(readOnly = true)
    public List<Space> list(UUID ownerId) {
        return spaceRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId).stream().toList();
    }

    /**
     * Counts the Spaces an account owns.
     *
     * @param ownerId the owning account
     * @return the number of Spaces
     */
    @Transactional(readOnly = true)
    public long count(UUID ownerId) {
        return spaceRepository.countByOwnerId(ownerId);
    }

    /**
     * Reads one of the account's Spaces.
     *
     * @param ownerId the owning account
     * @param spaceId the Space
     * @return the Space
     * @throws NotFoundException when the account does not own that Space
     */
    @Transactional(readOnly = true)
    public Space get(UUID ownerId, UUID spaceId) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new NotFoundException("That Space does not exist."));
        if (!space.getOwnerId().equals(ownerId)) {
            // The Space exists but belongs to somebody else. Answering 404
            // rather than 403 keeps the API from confirming that it exists.
            throw new NotFoundException("That Space does not exist.");
        }
        return space;
    }

    /**
     * Creates a Space.
     *
     * <p>The plan limit is checked here rather than in the interface: a Free
     * account may own three Spaces, and the third-plus-fourth refusal has to
     * come from the server or it is one request away from being bypassed
     * (PRD 4.2).
     *
     * @param ownerId  the owning account
     * @param title    the Space title
     * @param subtitle the Space subtitle
     * @param slug     the requested slug, or {@code null} to derive one
     * @param theme    the requested theme
     * @return the new Space
     * @throws PlanLimitExceededException when the account already owns as many
     *         Spaces as its plan allows
     * @throws ConflictException when the slug is already taken
     */
    @Transactional
    public Space create(UUID ownerId, String title, String subtitle, String slug, String theme) {
        AppUser owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("That account does not exist."));

        long owned = spaceRepository.countByOwnerId(ownerId);
        int limit = PlanLimits.spacesFor(owner.getPlan());
        if (owned >= limit) {
            throw new PlanLimitExceededException(
                    "You have reached the limit of " + limit + " Spaces on the "
                            + owner.getPlan().name().toLowerCase(Locale.ROOT) + " plan. "
                            + "Upgrade to Premium to create more.");
        }

        String resolvedSlug = slug == null || slug.isBlank()
                ? uniqueSlug(slugFrom(title))
                : normaliseSlug(slug);
        if (spaceRepository.existsBySlug(resolvedSlug)) {
            throw new ConflictException("That address is already taken. Please choose another one.");
        }

        Space space = new Space(ownerId, title.trim(), resolvedSlug);
        space.setSubtitle(blankToNull(subtitle));
        space.setTheme(resolveTheme(owner.getPlan(), theme));
        space.setCreatedAt(clock.instant());
        space.setUpdatedAt(clock.instant());

        return spaceRepository.save(space);
    }

    /**
     * Updates a Space.
     *
     * @param ownerId the owning account
     * @param spaceId the Space
     * @param changes what to change
     * @return the updated Space
     * @throws NotFoundException when the account does not own that Space
     * @throws PlanLimitExceededException when the Space is read-only
     * @throws ConflictException when the slug is taken, or is already locked
     */
    @Transactional
    public Space update(UUID ownerId, UUID spaceId, SpaceChanges changes) {
        requireEditable(ownerId, spaceId);
        Space space = get(ownerId, spaceId);
        AppUser owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("That account does not exist."));

        if (changes.title() != null) {
            space.setTitle(changes.title().trim());
        }
        if (changes.subtitle() != null) {
            space.setSubtitle(blankToNull(changes.subtitle()));
        }
        if (changes.slug() != null) {
            String requested = normaliseSlug(changes.slug());
            if (!requested.equals(space.getSlug())) {
                if (space.isSlugLocked()) {
                    throw new ConflictException(
                            "This Space has already received a testimonial, so its address "
                                    + "can no longer be changed.");
                }
                if (spaceRepository.existsBySlug(requested)) {
                    throw new ConflictException(
                            "That address is already taken. Please choose another one.");
                }
                space.setSlug(requested);
            }
        }
        if (changes.theme() != null) {
            space.setTheme(resolveTheme(owner.getPlan(), changes.theme()));
        }
        if (changes.ratingEnabled() != null) {
            space.setRatingEnabled(changes.ratingEnabled());
        }
        if (changes.fieldCompany() != null) {
            space.setFieldCompany(changes.fieldCompany());
            if (!changes.fieldCompany()) {
                space.setFieldCompanyRequired(false);
            }
        }
        if (changes.fieldCompanyRequired() != null) {
            space.setFieldCompanyRequired(changes.fieldCompanyRequired() && space.isFieldCompany());
        }
        if (changes.fieldSocial() != null) {
            space.setFieldSocial(changes.fieldSocial());
            if (!changes.fieldSocial()) {
                space.setFieldSocialRequired(false);
            }
        }
        if (changes.fieldSocialRequired() != null) {
            space.setFieldSocialRequired(changes.fieldSocialRequired() && space.isFieldSocial());
        }
        if (changes.fieldWebsite() != null) {
            space.setFieldWebsite(changes.fieldWebsite());
            if (!changes.fieldWebsite()) {
                space.setFieldWebsiteRequired(false);
            }
        }
        if (changes.fieldWebsiteRequired() != null) {
            space.setFieldWebsiteRequired(changes.fieldWebsiteRequired() && space.isFieldWebsite());
        }
        if (changes.fieldJobTitle() != null) {
            space.setFieldJobTitle(changes.fieldJobTitle());
            if (!changes.fieldJobTitle()) {
                space.setFieldJobTitleRequired(false);
            }
        }
        if (changes.fieldJobTitleRequired() != null) {
            space.setFieldJobTitleRequired(changes.fieldJobTitleRequired() && space.isFieldJobTitle());
        }

        space.setUpdatedAt(clock.instant());
        return spaceRepository.save(space);
    }

    /**
     * Deletes a Space and everything in it.
     *
     * <p>The testimonials go with it: the foreign key cascades, so a deleted
     * Space leaves no orphan submissions behind (PRD 4.2).
     *
     * @param ownerId the owning account
     * @param spaceId the Space
     * @throws NotFoundException when the account does not own that Space
     * @throws PlanLimitExceededException when the Space is read-only
     */
    @Transactional
    public void delete(UUID ownerId, UUID spaceId) {
        requireEditable(ownerId, spaceId);
        Space space = get(ownerId, spaceId);
        spaceRepository.delete(space);
    }

    /**
     * Whether the account may still change this Space.
     *
     * <p>A downgraded account whose Space count exceeds the Free limit keeps
     * its three oldest Spaces editable and the rest read-only (OPEN-B).
     *
     * @param ownerId the owning account
     * @param spaceId the Space
     * @return whether the Space is editable
     */
    @Transactional(readOnly = true)
    public boolean isEditable(UUID ownerId, UUID spaceId) {
        Space space = get(ownerId, spaceId);
        AppUser owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("That account does not exist."));

        int limit = PlanLimits.spacesFor(owner.getPlan());
        long owned = spaceRepository.countByOwnerId(ownerId);
        if (owned <= limit) {
            return true;
        }

        // Over the limit: the oldest Spaces stay editable and the newer ones
        // are read-only.
        List<Space> oldestFirst = spaceRepository.findByOwnerIdOrderByCreatedAtAscIdAsc(ownerId);
        return oldestFirst.stream()
                .limit(limit)
                .anyMatch(candidate -> candidate.getId().equals(spaceId));
    }

    /**
     * Refuses the call when the Space is read-only.
     *
     * <p>Read-only is a consequence of the plan, not of ownership: a
     * downgraded account keeps all of its Spaces but may only change the
     * ones its plan still covers (OPEN-B, PRD §4.9). Every write path goes
     * through here, because a rule that only some paths honour is a rule
     * that the API will eventually leak around.
     *
     * @param ownerId the owning account
     * @param spaceId the Space
     * @throws PlanLimitExceededException when the Space is read-only
     */
    @Transactional(readOnly = true)
    public void requireEditable(UUID ownerId, UUID spaceId) {
        if (!isEditable(ownerId, spaceId)) {
            throw new PlanLimitExceededException(
                    "This Space is read-only, because your plan does not include this many "
                            + "Spaces. Your three oldest Spaces stay editable.");
        }
    }

    /**
     * Resolves the theme, refusing a premium theme on a Free account.
     *
     * @param plan  the account's plan
     * @param theme the requested theme
     * @return the theme key
     * @throws ConflictException when the theme needs Premium and the account
     *         does not have it
     */
    private String resolveTheme(Plan plan, String theme) {
        if (theme == null || theme.isBlank()) {
            return ThemeCatalog.DEFAULT_THEME;
        }
        String key = theme.trim().toLowerCase(Locale.ROOT);
        if (ThemeCatalog.find(key).isEmpty()) {
            throw new ConflictException("That theme does not exist.");
        }
        if (!ThemeCatalog.isAvailable(plan, key)) {
            throw new ConflictException(
                    "That theme needs a Premium plan. Upgrade to use it.");
        }
        return key;
    }

    /**
     * Derives a slug from a title.
     *
     * <p>Accents are folded, so "Café" becomes "cafe" rather than "caf".
     *
     * @param title the title
     * @return the slug
     */
    static String slugFrom(String title) {
        String folded = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
        String slug = folded.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            // A title made entirely of characters that cannot appear in a
            // slug still needs one, so fall back to something valid.
            return "space";
        }
        return slug.length() > MAX_SLUG_LENGTH ? slug.substring(0, MAX_SLUG_LENGTH) : slug;
    }

    /**
     * Normalises a slug the user typed.
     *
     * @param slug the typed slug
     * @return the slug in the form the schema requires
     */
    static String normaliseSlug(String slug) {
        String trimmed = slug.trim().toLowerCase(Locale.ROOT);
        String normalised = trimmed.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (normalised.isEmpty()) {
            throw new ConflictException("That address cannot be empty.");
        }
        return normalised.length() > MAX_SLUG_LENGTH
                ? normalised.substring(0, MAX_SLUG_LENGTH)
                : normalised;
    }

    /**
     * Finds a free slug, appending a number when the obvious one is taken.
     *
     * @param base the preferred slug
     * @return a slug nobody is using
     */
    private String uniqueSlug(String base) {
        if (!spaceRepository.existsBySlug(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = base + "-" + suffix;
            if (!spaceRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("That address is already taken. Please choose another one.");
    }

    /**
     * Turns a blank string into {@code null}.
     *
     * @param value the value
     * @return the trimmed value, or {@code null}
     */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * The fields a caller may change on a Space.
     *
     * <p>Every field is optional: a {@code null} means "leave this alone",
     * which is what lets a partial update change the title without also
     * resetting the form configuration.
     *
     * @param title               the new title
     * @param subtitle            the new subtitle
     * @param slug                the new slug
     * @param theme               the new theme
     * @param ratingEnabled       whether ratings are on
     * @param fieldCompany        whether the company field is shown
     * @param fieldCompanyRequired whether the company field is required
     * @param fieldSocial         whether the social field is shown
     * @param fieldSocialRequired whether the social field is required
     * @param fieldWebsite        whether the website field is shown
     * @param fieldWebsiteRequired whether the website field is required
     * @param fieldJobTitle       whether the job title field is shown
     * @param fieldJobTitleRequired whether the job title field is required
     */
    public record SpaceChanges(
            String title,
            String subtitle,
            String slug,
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
}
