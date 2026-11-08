package com.loveproof.testimonials.web.dto.space;

import java.time.Instant;

import com.loveproof.testimonials.domain.SpaceTestimonial;

/**
 * One testimonial in the owner's inbox.
 *
 * <p>Unlike the public response, this one carries the email: the owner needs
 * it to recognise a submission (PRD §4.7). It is the owner's own data, shown
 * only to the account that owns the Space.
 */
public record SpaceTestimonialResponse(
        String id,
        String name,
        String email,
        String content,
        Integer rating,
        String company,
        String socialLink,
        String website,
        String jobTitle,
        boolean consentGiven,
        boolean favorite,
        boolean wallOfLove,
        boolean visible,
        Instant submittedAt) {

    /**
     * Builds the response from a testimonial.
     *
     * @param testimonial the testimonial
     * @return the response
     */
    public static SpaceTestimonialResponse from(SpaceTestimonial testimonial) {
        return new SpaceTestimonialResponse(
                testimonial.getId().toString(),
                testimonial.getName(),
                testimonial.getEmail(),
                testimonial.getContent(),
                testimonial.getRating(),
                testimonial.getCompany(),
                testimonial.getSocialLink(),
                testimonial.getWebsite(),
                testimonial.getJobTitle(),
                testimonial.isConsentGiven(),
                testimonial.isFavorite(),
                testimonial.isWallOfLove(),
                testimonial.isVisible(),
                testimonial.getSubmittedAt());
    }
}
