package com.loveproof.testimonials.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.domain.SpaceTestimonial;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.exception.SpaceNotAcceptingException;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.SpaceRepository;
import com.loveproof.testimonials.repository.SpaceTestimonialRepository;

/**
 * The rules for a public submission into a Space.
 *
 * <p>Three things are decided here rather than in the interface, because all
 * three are rules about the world rather than about the request:
 *
 * <ul>
 *   <li>Whether the Space is still collecting. A Space that a downgraded
 *       account can no longer edit is kept but not collected (PRD §4.5,
 *       §4.9).</li>
 *   <li>Which fields the submission is allowed to carry. A Space that does not
 *       ask for a company name does not store one, whatever the request
 *       contains.</li>
 *   <li>When the slug locks. The address stops moving the moment the Space
 *       receives its first testimonial.</li>
 * </ul>
 *
 * <p>The submitter's email is stored but never returned to the public: the
 * response carries the testimonial's identity, not the person behind it.
 */
@Service
public class SpaceSubmissionService {

    /** The longest accepted testimonial text, matching the database constraint. */
    static final int MAX_CONTENT_LENGTH = 2000;

    /** The shortest accepted testimonial text. */
    static final int MIN_CONTENT_LENGTH = 1;

    /** The longest accepted name. */
    static final int MAX_NAME_LENGTH = 100;

    private final SpaceRepository spaceRepository;
    private final SpaceTestimonialRepository testimonialRepository;
    private final AppUserRepository userRepository;
    private final SpaceService spaceService;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param spaceRepository       the Space store
     * @param testimonialRepository the testimonial store
     * @param userRepository        the account store
     * @param spaceService          the Space rules
     * @param clock                 the clock, for the submission timestamp
     */
    public SpaceSubmissionService(
            SpaceRepository spaceRepository,
            SpaceTestimonialRepository testimonialRepository,
            AppUserRepository userRepository,
            SpaceService spaceService,
            Clock clock) {
        this.spaceRepository = spaceRepository;
        this.testimonialRepository = testimonialRepository;
        this.userRepository = userRepository;
        this.spaceService = spaceService;
        this.clock = clock;
    }

    /**
     * Finds the Space behind a public URL segment.
     *
     * @param slug the slug
     * @return the Space
     * @throws NotFoundException when no Space has that slug
     */
    @Transactional(readOnly = true)
    public Space findBySlug(String slug) {
        return spaceRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("This page does not exist."));
    }

    /**
     * Whether the Space is still collecting submissions.
     *
     * <p>A Space stops collecting when its owner has more Spaces than their
     * plan allows: the oldest few stay editable and the rest are kept but
     * read-only, which includes not collecting (OPEN-B, PRD §4.9).
     *
     * <p>It also stops collecting once the Space holds as many testimonials
     * as the owner's plan allows. The limit is applied here, in the one place
     * that decides whether a Space accepts, so that the public page's
     * {@code accepting} flag and the refusal of a submission can never
     * disagree.
     *
     * @param space the Space
     * @return whether submissions are accepted
     */
    @Transactional(readOnly = true)
    public boolean isAccepting(Space space) {
        AppUser owner = userRepository.findById(space.getOwnerId()).orElse(null);
        if (owner == null) {
            // An account that no longer exists cannot have an editable Space.
            return false;
        }
        if (!spaceService.isEditable(owner.getId(), space.getId())) {
            return false;
        }
        int limit = PlanLimits.testimonialsPerSpaceFor(owner.getPlan());
        return testimonialRepository.countBySpaceId(space.getId()) < limit;
    }

    /**
     * Records a submission.
     *
     * <p>The Space's own configuration decides which optional fields are
     * stored: a field the Space does not ask for is dropped rather than
     * rejected, so a stale form in a visitor's browser cannot cause a
     * failure. Fields the Space marks required are checked here, because the
     * interface's check is only for fast feedback.
     *
     * @param slug      the Space's slug
     * @param name      the submitter's name
     * @param email     the submitter's email
     * @param content   the testimonial text
     * @param rating    the rating, or {@code null} when the Space has no ratings
     * @param company   the company name, or {@code null}
     * @param socialLink the social link, or {@code null}
     * @param website   the website, or {@code null}
     * @param jobTitle  the job title, or {@code null}
     * @param consent   whether the submitter consented to publication
     * @param honeypot  the hidden field; must be empty
     * @return the stored testimonial
     * @throws NotFoundException when the slug does not exist
     * @throws SpaceNotAcceptingException when the Space is not collecting
     * @throws IllegalArgumentException when a required field is missing
     */
    @Transactional
    public SpaceTestimonial submit(
            String slug,
            String name,
            String email,
            String content,
            Integer rating,
            String company,
            String socialLink,
            String website,
            String jobTitle,
            boolean consent,
            String honeypot) {

        // The honeypot is a field that is hidden from people and visible to
        // bots. A filled one means the submission came from a bot, so it is
        // accepted and discarded: the bot sees a success and moves on, and
        // nothing reaches the owner's inbox.
        if (honeypot != null && !honeypot.isBlank()) {
            return null;
        }

        Space space = findBySlug(slug);

        if (!isAccepting(space)) {
            throw new SpaceNotAcceptingException();
        }

        String trimmedName = requireText(name, "Name is required.", MAX_NAME_LENGTH);
        String trimmedEmail = requireText(email, "Email is required.", 254);
        String trimmedContent = requireText(content, "Testimonial text is required.", MAX_CONTENT_LENGTH);

        if (!trimmedContent.isEmpty() && trimmedContent.length() < MIN_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Testimonial text is required.");
        }

        if (space.isRatingEnabled()) {
            if (rating == null) {
                throw new IllegalArgumentException("Rating is required.");
            }
            if (rating < 1 || rating > 5) {
                throw new IllegalArgumentException("Rating must be between 1 and 5.");
            }
        } else {
            // The Space has ratings turned off: a rating in the request is not
            // an error, it is simply not collected.
            rating = null;
        }

        String trimmedCompany = space.isFieldCompany()
                ? optionalText(company)
                : null;
        String trimmedSocial = space.isFieldSocial()
                ? optionalText(socialLink)
                : null;
        String trimmedWebsite = space.isFieldWebsite()
                ? optionalText(website)
                : null;
        String trimmedJobTitle = space.isFieldJobTitle()
                ? optionalText(jobTitle)
                : null;

        if (space.isFieldCompanyRequired() && trimmedCompany == null) {
            throw new IllegalArgumentException("Company name is required.");
        }
        if (space.isFieldSocialRequired() && trimmedSocial == null) {
            throw new IllegalArgumentException("Social media links are required.");
        }
        if (space.isFieldWebsiteRequired() && trimmedWebsite == null) {
            throw new IllegalArgumentException("Website URL is required.");
        }
        if (space.isFieldJobTitleRequired() && trimmedJobTitle == null) {
            throw new IllegalArgumentException("Job title is required.");
        }

        Instant now = clock.instant();

        SpaceTestimonial testimonial = new SpaceTestimonial(
                space.getId(), trimmedName, trimmedEmail, trimmedContent);
        testimonial.setRating(rating);
        testimonial.setCompany(trimmedCompany);
        testimonial.setSocialLink(trimmedSocial);
        testimonial.setWebsite(trimmedWebsite);
        testimonial.setJobTitle(trimmedJobTitle);
        testimonial.setConsentGiven(consent);
        testimonial.setSubmittedAt(now);
        testimonial.setUpdatedAt(now);
        // A submission is visible by default: there is no approval step
        // (PRD §4.7). It is not on the Wall of Love and not a favorite until
        // the owner says so.
        testimonial.setVisible(true);

        SpaceTestimonial saved = testimonialRepository.save(testimonial);

        // The address stops moving once the Space has collected something:
        // links to this Space have already been shared by then.
        if (!space.isSlugLocked()) {
            space.setSlugLocked(true);
            space.setUpdatedAt(now);
            spaceRepository.save(space);
        }

        return saved;
    }

    /**
     * Normalises an optional free-text field.
     *
     * @param value the raw value
     * @return the trimmed value, or {@code null} when it is empty
     */
    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Requires a field to be present.
     *
     * @param value    the raw value
     * @param message  the message when it is missing
     * @param maxLength the longest accepted value
     * @return the trimmed value
     * @throws IllegalArgumentException when it is missing or too long
     */
    private static String requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    message.replace("is required.", "is too long."));
        }
        return trimmed;
    }
}
