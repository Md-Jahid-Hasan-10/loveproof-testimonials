package com.loveproof.testimonials.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loveproof.testimonials.domain.Testimonial;
import com.loveproof.testimonials.domain.TestimonialStatus;

/**
 * Persistence for {@link Testimonial}.
 *
 * <p>All queries bind parameters; none of them concatenate SQL.
 */
public interface TestimonialRepository extends JpaRepository<Testimonial, UUID> {

    /**
     * The public wall: approved, non-deleted, consented testimonials, newest first.
     *
     * <p>Served by the partial index {@code idx_testimonial_live_approved}. The predicate is not
     * optional — pending, rejected and deleted rows must never reach an anonymous caller.
     *
     * <p>The consent predicate is stated explicitly even though the database forbids the
     * alternative: {@code ck_testimonial_consent_required} means no row with {@code consent_given =
     * false} can exist, so the condition is redundant today. It is written out anyway because this
     * query is the last line of defence between a withdrawn consent and a published testimonial,
     * and a reader should not have to leave the query to learn that.
     *
     * <p>The rating filter is expressed as a boolean flag rather than as {@code :rating IS NULL},
     * because Hibernate cannot infer a JDBC type for a parameter that is only ever compared to
     * {@code NULL}: it binds it as {@code bytea} and PostgreSQL rejects the query.
     *
     * @param hasRating whether the rating filter applies
     * @param rating    the rating to match, when {@code hasRating} is true
     * @param pageable  the paging and sort
     * @return the matching page
     */
    @Query("""
            SELECT t FROM Testimonial t
            WHERE t.status = com.loveproof.testimonials.domain.TestimonialStatus.APPROVED
              AND t.deletedAt IS NULL
              AND t.consentGiven = true
              AND (:hasRating = false OR t.rating = :rating)
            """)
    Page<Testimonial> findLiveApproved(
            @Param("hasRating") boolean hasRating,
            @Param("rating") Integer rating,
            Pageable pageable);

    /**
     * Counts the public wall and its average rating in one round trip.
     *
     * <p>Only approved, non-deleted, consented rows contribute, so a pending or deleted submission
     * can never leak into a public count. The predicate is deliberately identical to
     * {@link #findLiveApproved(boolean, Integer, Pageable)} — a count that disagreed with the list
     * would be a bug the visitor could see.
     *
     * @return the count and the average rating, the latter {@code null} when nothing is approved
     */
    @Query("""
            SELECT COUNT(t) AS total, AVG(t.rating) AS averageRating FROM Testimonial t
            WHERE t.status = com.loveproof.testimonials.domain.TestimonialStatus.APPROVED
              AND t.deletedAt IS NULL
              AND t.consentGiven = true
            """)
    PublicRatingStats findPublicStats();

    /**
     * The aggregate of the public wall.
     *
     * @param total        the number of approved, non-deleted testimonials
     * @param averageRating the average rating, or {@code null} when nothing is approved
     */
    interface PublicRatingStats {

        /**
         * @return the number of approved, non-deleted testimonials
         */
        Long getTotal();

        /**
         * @return the average rating, or {@code null} when nothing is approved
         */
        Double getAverageRating();
    }

    /**
     * Counts the live testimonials per moderation status.
     *
     * <p>Soft-deleted rows are excluded: they are counted separately by
     * {@link #countDeleted()}.
     *
     * @return one row per status
     */
    @Query("""
            SELECT t.status AS status, COUNT(t) AS total FROM Testimonial t
            WHERE t.deletedAt IS NULL
            GROUP BY t.status
            """)
    List<StatusCount> countByStatusExcludingDeleted();

    /**
     * The count of testimonials in one moderation status.
     *
     * @param status the moderation status
     * @param total  the number of non-deleted testimonials in that status
     */
    interface StatusCount {

        /**
         * @return the moderation status
         */
        TestimonialStatus getStatus();

        /**
         * @return the number of non-deleted testimonials in that status
         */
        Long getTotal();
    }

    /**
     * Counts every soft-deleted testimonial.
     *
     * @return the deleted count
     */
    @Query("SELECT COUNT(t) FROM Testimonial t WHERE t.deletedAt IS NOT NULL")
    long countDeleted();

    /**
     * Counts the non-deleted testimonials submitted since the given instant.
     *
     * @param since the inclusive lower bound
     * @return the count
     */
    @Query("SELECT COUNT(t) FROM Testimonial t WHERE t.deletedAt IS NULL AND t.submittedAt >= :since")
    long countSubmittedSince(@Param("since") Instant since);

    /**
     * Counts the non-deleted testimonials with the given status.
     *
     * @param status the moderation status
     * @return the count
     */
    long countByStatusAndDeletedAtIsNull(TestimonialStatus status);

    /**
     * The moderation inbox: every non-deleted testimonial matching the filter.
     *
     * <p>The filters are expressed as boolean flags rather than as {@code :param IS NULL} tests.
     * Hibernate cannot infer a JDBC type for a parameter that is only ever compared to
     * {@code NULL}, so it binds it as {@code bytea} and PostgreSQL rejects the query with
     * "function lower(bytea) does not exist". The flags are unambiguous, and the search term
     * is bound as a string that is never null.
     *
     * @param hasStatus whether the status filter applies
     * @param status    the status to match, when {@code hasStatus} is true
     * @param hasQuery  whether the search term applies
     * @param query     the search term, never null
     * @param pageable  the paging and sort
     * @return the matching page
     */
    @Query("""
            SELECT t FROM Testimonial t
            WHERE t.deletedAt IS NULL
              AND (:hasStatus = false OR t.status = :status)
              AND (
                    :hasQuery = false
                    OR LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(t.company) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(t.content) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
            """)
    Page<Testimonial> findForModeration(
            @Param("hasStatus") boolean hasStatus,
            @Param("status") TestimonialStatus status,
            @Param("hasQuery") boolean hasQuery,
            @Param("query") String query,
            Pageable pageable);

    /**
     * Looks for a recent submission with the same fingerprint.
     *
     * <p>Backs FR-1.13: identical name and text submitted inside the duplicate window is rejected as
     * spam.
     *
     * @param fingerprint the SHA-256 fingerprint
     * @param since       the inclusive lower bound of the duplicate window
     * @return whether a duplicate exists
     */
    @Query("""
            SELECT COUNT(t) FROM Testimonial t
            WHERE t.contentFingerprint = :fingerprint
              AND t.submittedAt > :since
            """)
    long countByFingerprintSince(@Param("fingerprint") String fingerprint, @Param("since") Instant since);

    /**
     * Finds the soft-deleted testimonials, for the "deleted" inbox tab.
     *
     * @param pageable the paging and sort
     * @return the matching page
     */
    Page<Testimonial> findAllByDeletedAtIsNotNull(Pageable pageable);
}
