package com.loveproof.testimonials.web.dto.space;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of a submission into a Space.
 *
 * <p>The Jakarta Bean Validation annotations here are the authoritative
 * server-side rules (PRD §4.5). The frontend mirrors them for fast feedback
 * only.
 *
 * <p>The optional fields are all nullable: which of them are required
 * depends on the Space's configuration, and the service decides that, not
 * the request shape. A Space that asks for nothing beyond name, email and
 * text accepts a request with no optional fields at all.
 */
public record SubmitSpaceTestimonialRequest(

        @NotBlank(message = "Name is required.")
        @Size(max = 100, message = "Name must be at most 100 characters.")
        String name,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid email address.")
        @Size(max = 254, message = "Email must be at most 254 characters.")
        String email,

        @NotBlank(message = "Testimonial text is required.")
        @Size(max = 2000, message = "Testimonial text must be at most 2000 characters.")
        String content,

        @Min(value = 1, message = "Rating must be between 1 and 5.")
        @Max(value = 5, message = "Rating must be between 1 and 5.")
        Integer rating,

        @Size(max = 120, message = "Company name must be at most 120 characters.")
        String company,

        @Size(max = 500, message = "Social media link must be at most 500 characters.")
        String socialLink,

        @Size(max = 500, message = "Website URL must be at most 500 characters.")
        String website,

        @Size(max = 120, message = "Job title must be at most 120 characters.")
        String jobTitle,

        /** Consent is optional and never gates display (PRD §4.3, rule 9). */
        Boolean consent,

        /** The honeypot field. Must be empty; a bot fills it. */
        String website2) {
}
