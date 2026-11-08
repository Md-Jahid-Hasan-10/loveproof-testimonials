package com.loveproof.testimonials.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.loveproof.testimonials.domain.Testimonial;
import com.loveproof.testimonials.domain.TestimonialStatus;

/**
 * The public representation of a testimonial.
 *
 * <p>Never carries the submitter's IP hash or the content fingerprint: those are internal
 * anti-spam bookkeeping and must not reach a client (NFR-SEC-7).
 *
 * @param id                 the identifier
 * @param name               the submitter's name
 * @param role               the job title, or {@code null}
 * @param company            the company name, or {@code null}
 * @param rating             the 1-5 rating
 * @param content            the testimonial text
 * @param status             the moderation status
 * @param consentTextVersion the consent wording shown at submission time
 * @param submittedAt        the submission timestamp
 */
public record TestimonialResponse(
        UUID id,
        String name,
        String role,
        String company,
        int rating,
        String content,
        TestimonialStatus status,
        String consentTextVersion,
        Instant submittedAt) {

    /**
     * Maps an entity to its public representation.
     *
     * @param testimonial the entity
     * @return the response
     */
    public static TestimonialResponse from(Testimonial testimonial) {
        return new TestimonialResponse(
                testimonial.getId(),
                testimonial.getName(),
                testimonial.getRole(),
                testimonial.getCompany(),
                testimonial.getRating(),
                testimonial.getContent(),
                testimonial.getStatus(),
                testimonial.getConsentTextVersion(),
                testimonial.getSubmittedAt());
    }
}
