package com.loveproof.testimonials.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of a testimonial submission.
 *
 * <p>The Jakarta Bean Validation annotations here are the authoritative server-side rules (FR-1.11).
 * The frontend mirrors them for fast feedback only.
 */
public record CreateTestimonialRequest(

        @NotBlank(message = "Name is required.")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
        String name,

        @Size(max = 120, message = "Role must be at most 120 characters.")
        String role,

        @Size(max = 120, message = "Company must be at most 120 characters.")
        String company,

        @NotNull(message = "Rating is required.")
        @Min(value = 1, message = "Rating must be between 1 and 5.")
        @Max(value = 5, message = "Rating must be between 1 and 5.")
        Integer rating,

        @NotBlank(message = "Testimonial text is required.")
        @Size(min = 20, max = 2000, message = "Testimonial text must be between 20 and 2000 characters.")
        String content,

        @NotNull(message = "Consent is required.")
        @AssertTrue(message = "You must consent to the publication of your testimonial.")
        Boolean consent) {
}
