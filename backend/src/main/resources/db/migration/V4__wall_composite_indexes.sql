-- Composite indexes for the public wall.
--
-- The wall is the busiest read in the application: every visitor to the wall
-- page triggers it, and it is the only query that is allowed to run without a
-- session. The baseline indexes cover the single columns, but the wall's two
-- orderings need the sort column and the filter columns in one index:
--
--   * "newest first" walks (submitted_at DESC) and filters on the live,
--     approved rows.
--   * "highest rated" walks (rating DESC) over the same rows.
--
-- Without these, the planner reads every approved row and sorts them to
-- return the first page. With them, it walks the index and stops after the
-- rows the page needs.
--
-- Both indexes are partial, on the same predicate as the baseline ones, so
-- they stay small however large the moderation queue grows: a pending or
-- deleted row is never in either of them.
--
-- The rating index carries the submission time as a tie-breaker, so the
-- ordering is total and two rows with the same rating always come back in
-- the same order.

-- The newest-first wall.
CREATE INDEX idx_testimonial_live_approved_recent
    ON testimonial (submitted_at DESC, rating DESC)
    WHERE status = 'APPROVED' AND deleted_at IS NULL;

-- The highest-rated wall, with the submission time as the tie-breaker.
CREATE INDEX idx_testimonial_live_approved_rating
    ON testimonial (rating DESC, submitted_at DESC)
    WHERE status = 'APPROVED' AND deleted_at IS NULL;

-- The wall filtered by rating and ordered by rating. The rating filter is
-- the one the visitor picks by hand, and it narrows the result set to a
-- fifth of the wall at most.
CREATE INDEX idx_testimonial_live_approved_rating_filter
    ON testimonial (rating, submitted_at DESC)
    WHERE status = 'APPROVED' AND deleted_at IS NULL AND consent_given = TRUE;
