package com.loveproof.testimonials.web.dto.space;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of a testimonial edit.
 *
 * @param content the new testimonial text
 */
public record EditTestimonialRequest(

        @NotBlank(message = "The testimonial text cannot be empty.")
        @Size(max = 2000, message = "The testimonial text must be at most 2000 characters.")
        String content) {
}
