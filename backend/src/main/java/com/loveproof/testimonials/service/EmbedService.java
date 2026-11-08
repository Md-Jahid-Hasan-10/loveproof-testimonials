package com.loveproof.testimonials.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.domain.SpaceEmbedConfig;
import com.loveproof.testimonials.domain.SpaceTestimonial;
import com.loveproof.testimonials.exception.ConflictException;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.SpaceEmbedConfigRepository;
import com.loveproof.testimonials.repository.SpaceRepository;
import com.loveproof.testimonials.repository.SpaceTestimonialRepository;

/**
 * The embed configuration, and the public data the embed renders.
 *
 * <p>Two rules shape everything here:
 *
 * <ul>
 *   <li>The embed shows only the testimonials that are on the Wall of Love
 *       <em>and</em> visible. A hidden testimonial leaves the embed without
 *       the owner regenerating anything (PRD §4.8).</li>
 *   <li>The embed never exposes an email address. The owner's inbox carries
 *       the email so they can recognise a submission; the embed does not,
 *       because the embed ends up on somebody else's website.</li>
 * </ul>
 */
@Service
public class EmbedService {

    /** How many bytes of randomness the embed token carries. */
    private static final int TOKEN_BYTES = 32;

    private final SpaceRepository spaceRepository;
    private final SpaceEmbedConfigRepository configRepository;
    private final SpaceTestimonialRepository testimonialRepository;
    private final AppUserRepository userRepository;
    private final SpaceService spaceService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates the service.
     *
     * @param spaceRepository       the Space store
     * @param configRepository      the embed configuration store
     * @param testimonialRepository the testimonial store
     * @param userRepository        the account store
     * @param spaceService          the Space rules
     * @param clock                 the clock, for the update timestamps
     */
    public EmbedService(
            SpaceRepository spaceRepository,
            SpaceEmbedConfigRepository configRepository,
            SpaceTestimonialRepository testimonialRepository,
            AppUserRepository userRepository,
            SpaceService spaceService,
            Clock clock) {
        this.spaceRepository = spaceRepository;
        this.configRepository = configRepository;
        this.testimonialRepository = testimonialRepository;
        this.userRepository = userRepository;
        this.spaceService = spaceService;
        this.clock = clock;
    }

    /**
     * Reads a Space's embed configuration, creating it the first time.
     *
     * <p>The configuration exists from the moment the owner opens the embed
     * page, so the snippet is ready to copy without a separate "create"
     * step.
     *
     * @param ownerId the signed-in account
     * @param spaceId the Space
     * @return the configuration
     */
    @Transactional
    public SpaceEmbedConfig getOrCreate(UUID ownerId, UUID spaceId) {
        Space space = spaceService.get(ownerId, spaceId);

        return configRepository.findById(spaceId)
                .orElseGet(() -> {
                    SpaceEmbedConfig created = new SpaceEmbedConfig(
                            space.getId(), newToken());
                    created.setCreatedAt(clock.instant());
                    created.setUpdatedAt(clock.instant());
                    return configRepository.save(created);
                });
    }

    /**
     * Saves a Space's embed configuration.
     *
     * <p>The premium options are refused for a Free account. A configuration
     * that already uses them keeps them: the check is about choosing an
     * option, not about holding one, so a downgraded owner's embed keeps
     * rendering the way it did (PRD §4.8).
     *
     * @param ownerId         the signed-in account
     * @param spaceId         the Space
     * @param layout          the layout, or {@code null} to leave it alone
     * @param themeMode       the theme mode, or {@code null} to leave it alone
     * @param backgroundColor the background colour, or {@code null} to leave
     *                        it alone
     * @param animation       whether the animation is on, or {@code null} to
     *                        leave it alone
     * @return the saved configuration
     */
    @Transactional
    public SpaceEmbedConfig update(
            UUID ownerId,
            UUID spaceId,
            String layout,
            String themeMode,
            String backgroundColor,
            Boolean animation) {

        spaceService.requireEditable(ownerId, spaceId);

        SpaceEmbedConfig config = getOrCreate(ownerId, spaceId);
        AppUser owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("That account does not exist."));
        Plan plan = owner.getPlan();

        if (layout != null) {
            String normalised = EmbedCatalog.normalise(layout);
            if (!EmbedCatalog.isLayoutAvailable(plan, normalised)) {
                throw new ConflictException(
                        "The carousel needs a Premium plan. Upgrade to use it.");
            }
            config.setLayout(normalised);
        }

        if (themeMode != null) {
            String normalised = EmbedCatalog.normalise(themeMode);
            if (EmbedCatalog.findThemeMode(normalised).isEmpty()) {
                throw new ConflictException("That theme mode does not exist.");
            }
            config.setThemeMode(normalised);
        }

        if (backgroundColor != null) {
            if (!EmbedCatalog.isValidColor(backgroundColor)) {
                throw new ConflictException("That background colour is not a colour.");
            }
            if (!EmbedCatalog.isBackgroundAvailable(plan)) {
                throw new ConflictException(
                        "A background colour needs a Premium plan. Upgrade to use it.");
            }
            config.setBackgroundColor(backgroundColor);
        }

        if (animation != null) {
            if (animation && !EmbedCatalog.isAnimationAvailable(plan)) {
                throw new ConflictException(
                        "The animation needs a Premium plan. Upgrade to use it.");
            }
            config.setAnimation(animation);
        }

        config.setUpdatedAt(clock.instant());
        return configRepository.save(config);
    }

    /**
     * Rotates a Space's embed token.
     *
     * <p>The token is what the snippet uses to fetch its data. Rotating it
     * makes every snippet that used the old token stop working, which is the
     * point: it is how the owner revokes an embed they no longer want.
     *
     * @param ownerId the signed-in account
     * @param spaceId the Space
     * @return the configuration with its new token
     */
    @Transactional
    public SpaceEmbedConfig rotateToken(UUID ownerId, UUID spaceId) {
        spaceService.requireEditable(ownerId, spaceId);

        SpaceEmbedConfig config = getOrCreate(ownerId, spaceId);
        config.setEmbedToken(newToken());
        config.setUpdatedAt(clock.instant());
        return configRepository.save(config);
    }

    /**
     * Reads the public data an embed renders.
     *
     * <p>The token is the only thing needed: an embed is public, and the token
     * is what makes it unguessable. A token that belongs to no Space answers
     * "not found", so a wrong token is indistinguishable from a deleted one.
     *
     * @param token the embed token
     * @return the Space, its configuration and the testimonials to render
     */
    @Transactional(readOnly = true)
    public EmbedData readPublicData(String token) {
        SpaceEmbedConfig config = configRepository.findByEmbedToken(token)
                .orElseThrow(() -> new NotFoundException("That embed does not exist."));

        Space space = spaceRepository.findById(config.getSpaceId())
                .orElseThrow(() -> new NotFoundException("That embed does not exist."));

        List<SpaceTestimonial> testimonials = testimonialRepository
                .findBySpaceIdAndWallOfLoveTrueAndVisibleTrueOrderBySubmittedAtDesc(
                        space.getId());

        return new EmbedData(space, config, testimonials);
    }

    /**
     * Builds a fresh, unguessable embed token.
     *
     * @return the token
     */
    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * What an embed renders.
     *
     * @param space         the Space the testimonials belong to
     * @param config        the embed configuration
     * @param testimonials  the Wall of Love entries, newest first
     */
    public record EmbedData(
            Space space,
            SpaceEmbedConfig config,
            List<SpaceTestimonial> testimonials) {
    }
}
