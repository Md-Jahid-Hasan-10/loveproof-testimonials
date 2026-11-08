package com.loveproof.testimonials.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loveproof.testimonials.domain.ModerationAction;

/**
 * Persistence for {@link ModerationAction}.
 *
 * <p>The table is append-only: this repository deliberately exposes no update or delete operation.
 */
public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

    /**
     * Lists the audit trail of one testimonial, newest first.
     *
     * @param testimonialId the testimonial
     * @return the actions, newest first
     */
    List<ModerationAction> findByTestimonialIdOrderByCreatedAtDesc(UUID testimonialId);

    /**
     * Counts the audit entries recorded for one testimonial.
     *
     * @param testimonialId the testimonial
     * @return the count
     */
    long countByTestimonialId(UUID testimonialId);
}
