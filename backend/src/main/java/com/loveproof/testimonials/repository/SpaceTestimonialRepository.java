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
     * The inbox of one Space: favourites first, then newest first.
     *
     * <p>The ordering is the PRD's (4.7): a favourited testimonial always
     * sorts above one that is not, and within each group the newest comes
     * first.
     *
     * @param spaceId the Space
     * @param pageable the page request
     * @return the page of testimonials
     */
    @Query("""
            SELECT t FROM SpaceTestimonial t
            WHERE t.spaceId = :spaceId
            ORDER BY t.favorite DESC, t.submittedAt DESC
            """)
    Page<SpaceTestimonial> findInbox(
            @Param("spaceId") UUID spaceId,
            Pageable pageable);

    /**
     * The inbox of one Space, with the search and filters applied.
     *
     * <p>Each filter is optional. The search covers the name, the email and
     * the text, because those are the three things an owner looks for when
     * they remember part of a submission.
     *
     * @param spaceId the Space
     * @param search  the free-text search, or {@code null}
     * @param favorite only favourites, or {@code null} for both
     * @param wallOfLove only Wall of Love entries, or {@code null} for both
     * @param hidden only hidden ones, or {@code null} for both
     * @param rating only this rating, or {@code null} for all
     * @param pageable the page request
     * @return the page of testimonials
     */
    @Query("""
            SELECT t FROM SpaceTestimonial t
            WHERE t.spaceId = :spaceId
              AND (:search IS NULL
                   OR lower(t.name) LIKE :search
                   OR lower(t.email) LIKE :search
                   OR lower(t.content) LIKE :search)
              AND (:favorite IS NULL OR t.favorite = :favorite)
              AND (:wallOfLove IS NULL OR t.wallOfLove = :wallOfLove)
              AND (:hidden IS NULL OR t.visible <> :hidden)
              AND (:rating IS NULL OR t.rating = :rating)
            ORDER BY t.favorite DESC, t.submittedAt DESC
            """)
    Page<SpaceTestimonial> findInboxFiltered(
            @Param("spaceId") UUID spaceId,
            @Param("search") String search,
            @Param("favorite") Boolean favorite,
            @Param("wallOfLove") Boolean wallOfLove,
            @Param("hidden") Boolean hidden,
            @Param("rating") Integer rating,
            Pageable pageable);

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

    /**
     * Counts every testimonial across an account's Spaces.
     *
     * <p>The dashboard's headline number. It is scoped by the Space ids
     * rather than by a join, because the Space list is already loaded and
     * the ids are the whole of the ownership check.
     *
     * @param spaceIds the Spaces to count across
     * @return the number of testimonials
     */
    @Query("SELECT COUNT(t) FROM SpaceTestimonial t WHERE t.spaceId IN :spaceIds")
    long countAcrossSpaces(@Param("spaceIds") List<UUID> spaceIds);

    /**
     * Counts the distinct submitters across an account's Spaces.
     *
     * <p>Unique submitters is the number of distinct email addresses across
     * the account's testimonials — one person who submitted to two Spaces
     * counts once.
     *
     * @param spaceIds the Spaces to count across
     * @return the number of distinct submitters
     */
    @Query("SELECT COUNT(DISTINCT lower(t.email)) FROM SpaceTestimonial t WHERE t.spaceId IN :spaceIds")
    long countDistinctSubmittersAcrossSpaces(@Param("spaceIds") List<UUID> spaceIds);

    /**
     * The most recent submissions across an account's Spaces.
     *
     * <p>The dashboard's "recent submissions" list. It is not paged because
     * the dashboard shows a fixed, short list rather than a browsable one.
     *
     * @param spaceIds the Spaces to draw from
     * @param limit    how many to return
     * @return the testimonials, newest first
     */
    @Query("SELECT t FROM SpaceTestimonial t WHERE t.spaceId IN :spaceIds ORDER BY t.submittedAt DESC")
    List<SpaceTestimonial> findRecentAcrossSpaces(
            @Param("spaceIds") List<UUID> spaceIds,
            @Param("limit") int limit);
}
