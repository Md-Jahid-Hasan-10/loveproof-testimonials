package com.loveproof.testimonials.web;

import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.domain.Testimonial;
import com.loveproof.testimonials.service.TestimonialService;
import com.loveproof.testimonials.web.dto.CreateTestimonialRequest;
import com.loveproof.testimonials.web.dto.PageResponse;
import com.loveproof.testimonials.web.dto.TestimonialResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * The public testimonial API.
 *
 * <p>Every endpoint here is reachable without authentication. The read endpoints expose approved,
 * non-deleted testimonials only — never pending, rejected or deleted content (FR-5.1, FR-5.7).
 */
@RestController
@RequestMapping("/api/testimonials")
public class TestimonialController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TestimonialService testimonialService;

    /**
     * Creates the controller.
     *
     * @param testimonialService the service holding the business rules
     */
    public TestimonialController(TestimonialService testimonialService) {
        this.testimonialService = testimonialService;
    }

    /**
     * Submits a testimonial.
     *
     * <p>The submission is created in status {@code PENDING} and does not appear on the wall until a
     * moderator approves it (FR-1.10).
     *
     * @param request     the submission
     * @param httpRequest the servlet request, used to read the client address for rate-limit
     *                    bookkeeping
     * @return 201 with the created testimonial
     */
    @PostMapping
    public ResponseEntity<TestimonialResponse> create(
            @Valid @RequestBody CreateTestimonialRequest request,
            HttpServletRequest httpRequest) {
        Testimonial created = testimonialService.create(
                request.name(),
                request.role(),
                request.company(),
                request.rating(),
                request.content(),
                clientIp(httpRequest));

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(TestimonialResponse.from(created));
    }

    /**
     * Lists the public wall.
     *
     * @param rating optional rating filter, 1-5
     * @param sort   the ordering: {@code submittedAt} (default) or {@code rating}
     * @param page   the 0-based page number
     * @param size   the page size
     * @return the matching page of approved testimonials
     */
    @GetMapping
    public PageResponse<TestimonialResponse> list(
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false, defaultValue = "submittedAt") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        Sort ordering = resolveSort(sort);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        Page<Testimonial> results = testimonialService.findPublicWall(rating, ordering, safePage, safeSize);
        return PageResponse.from(results, TestimonialResponse::from);
    }

    /**
     * Returns the public statistics: the approved count and the average approved rating.
     *
     * @return the statistics
     */
    @GetMapping("/stats")
    public PublicStatsResponse stats() {
        TestimonialService.PublicStats stats = testimonialService.publicStats();
        return new PublicStatsResponse(stats.count(), stats.averageRating());
    }

    /**
     * Resolves the requested ordering, defaulting to newest first.
     *
     * @param sort the requested sort key
     * @return the ordering
     */
    private Sort resolveSort(String sort) {
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "submittedAt");
        }
        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "rating" -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "submittedAt"));
            case "submittedat", "submitted_at", "newest" -> Sort.by(Sort.Direction.DESC, "submittedAt");
            default -> Sort.by(Sort.Direction.DESC, "submittedAt");
        };
    }

    /**
     * Reads the client address for rate-limit bookkeeping.
     *
     * <p>The address is only ever passed to the service, which hashes it. The raw value is never
     * stored or logged (NFR-SEC-6).
     *
     * @param httpRequest the servlet request
     * @return the client address, or {@code null} when it is unavailable
     */
    private String clientIp(HttpServletRequest httpRequest) {
        String forwarded = httpRequest.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return httpRequest.getRemoteAddr();
    }

    /**
     * The public statistics.
     *
     * @param count         the number of approved testimonials
     * @param averageRating the average rating, or {@code null} when nothing is approved
     */
    public record PublicStatsResponse(long count, Double averageRating) {
    }
}
