package com.loveproof.testimonials.web.dto.space;

/**
 * The body of a curation action.
 *
 * <p>One record covers favourite, Wall of Love and visibility, because the
 * three actions differ only in which flag they set. A missing flag means
 * "leave this alone", so a partial request never resets the other two.
 *
 * @param favorite   the new favourite state, or {@code null} to leave it alone
 * @param wallOfLove the new Wall of Love state, or {@code null}
 * @param visible    the new visibility state, or {@code null}
 */
public record CurationRequest(
        Boolean favorite,
        Boolean wallOfLove,
        Boolean visible) {
}
