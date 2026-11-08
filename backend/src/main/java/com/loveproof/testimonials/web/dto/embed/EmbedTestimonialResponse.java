package com.loveproof.testimonials.web.dto.embed;

import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.domain.SpaceTestimonial;

/**
 * One testimonial as the embed sees it.
 *
 * <p>The submitter's email is never here. The optional profile fields are
 * only carried when the Space collects them: a testimonial that was sent
 * without a company, from a Space that asks for one, still shows whatever
 * was actually sent, and never shows an empty placeholder (PRD §4.8).
 */
public record EmbedTestimonialResponse(
        String name,
        String content,
        Integer rating,
        String company,
        String jobTitle,
        String website,
        String socialLink) {

    /**
     * Builds the response from a testimonial.
     *
     * @param testimonial the testimonial
     * @param space       the Space that collected it
     * @return the response
     */
    public static EmbedTestimonialResponse from(SpaceTestimonial testimonial, Space space) {
        return new EmbedTestimonialResponse(
                testimonial.getName(),
                testimonial.getContent(),
                testimonial.getRating(),
                space.isFieldCompany() ? testimonial.getCompany() : null,
                space.isFieldJobTitle() ? testimonial.getJobTitle() : null,
                space.isFieldWebsite() ? testimonial.getWebsite() : null,
                space.isFieldSocial() ? testimonial.getSocialLink() : null);
    }
}
