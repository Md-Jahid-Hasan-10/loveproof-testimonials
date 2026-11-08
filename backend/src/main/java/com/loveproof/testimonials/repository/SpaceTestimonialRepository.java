package com.loveproof.testimonials.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loveproof.testimonials.domain.SpaceTestimonial;

/**
 * The testimonial store, per Space.
 */
public interface SpaceTestimonialRepository
        extends JpaRepository<SpaceTestimonial, UUID>, JpaSpecificationExecutor<SpaceTestimonial> {

    /**
     * Counts the testimonials in a Space.
     *
     * @param spaceId the Space
     * @return the number of testimonials
     */
    long countBySpaceId(UUID spaceId);

    /**
     * Lists the Wall of Love entries of a Space: on the Wall of Love and
     * visible. These are exactly the testimonials an embed shows.
     *
     * @param spaceId the Space
     * @return the testimonials, newest first
     */
    List<SpaceTestimonial> findBySpaceIdAndWallOfLoveTrueAndVisibleTrueOrderBySubmittedAtDesc(UUID spaceId);

    /**
     * Lists a Space's testimonials with the owner's filters applied.
     *
     * @param spaceId the Space
     * @param pageable the page request
     * @return the page of testimonials
     */
    Page<SpaceTestimonial> findBySpaceId(UUID spaceId, Pageable pageable);

    /**
     * Finds one testimonial inside a Space.
     *
     * @param spaceId the Space
     * @param id      the testimonial
     * @return the testimonial, or empty
     */
    Optional<SpaceTestimonial> findBySpaceIdAndId(UUID spaceId, UUID id);

    /**
     * Counts the distinct submitters of a Space.
     *
     * <p>Unique submitters is the number of distinct email addresses across
     * the Space's testimonials.
     *
     * @param spaceId the Space
     * @return the number of distinct submitters
     */
    @Query("SELECT COUNT(DISTINCT lower(t.email)) FROM SpaceTestimonial t WHERE t.spaceId = :spaceId")
    long countDistinctSubmitters(@Param("spaceId") UUID spaceId);

    /**
     * Lists the submissions a Space received in a time range, for the
     * dashboard chart.
     *
     * @param spaceIds the Spaces to count across
     * @param from     the start of the range, inclusive
     * @param to       the end of the range, exclusive
     * @return the testimonials in the range
     */
    @Query("SELECT t FROM SpaceTestimonial t WHERE t.spaceId IN :spaceIds AND t.submittedAt >= :from AND t.submittedAt < :to")
    List<SpaceTestimonial> findSubmittedBetween(
            @Param("spaceIds") List<UUID> spaceIds,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
