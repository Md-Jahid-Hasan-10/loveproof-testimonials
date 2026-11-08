package com.loveproof.testimonials.web;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.domain.SpaceTestimonial;
import com.loveproof.testimonials.service.SpaceSubmissionService;
import com.loveproof.testimonials.web.dto.space.PublicSpaceResponse;
import com.loveproof.testimonials.web.dto.space.SubmitSpaceTestimonialRequest;

import jakarta.validation.Valid;

/**
 * The public Space surface.
 *
 * <p>Everything here is reachable without authentication: a visitor who has
 * never signed in fills in a Space's form and submits it (PRD §4.5).
 *
 * <p>The response to a submission never carries the submitter's email. The
 * email is stored so the owner can recognise a repeat submission, but it is
 * not echoed back to an anonymous caller.
 */
@RestController
@RequestMapping("/api/app/public/spaces")
public class PublicSpaceController {

    private final SpaceSubmissionService submissionService;

    /**
     * Creates the controller.
     *
     * @param submissionService the service holding the submission rules
     */
    public PublicSpaceController(SpaceSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /**
     * Reads the public form configuration of a Space.
     *
     * @param slug the Space's slug
     * @return the Space's public configuration
     */
    @GetMapping("/{slug}")
    public PublicSpaceResponse read(@PathVariable String slug) {
        Space space = submissionService.findBySlug(slug);
        return PublicSpaceResponse.from(space, submissionService.isAccepting(space));
    }

    /**
     * Submits a testimonial into a Space.
     *
     * <p>A submission from a bot -- one that fills the honeypot -- is accepted
     * and discarded. Answering with a success keeps the bot from learning
     * that it was detected; nothing reaches the owner's inbox.
     *
     * @param slug    the Space's slug
     * @param request the submission
     * @return 201 with the submission's identity
     */
    @PostMapping("/{slug}/testimonials")
    public ResponseEntity<SubmittedResponse> submit(
            @PathVariable String slug,
            @Valid @RequestBody SubmitSpaceTestimonialRequest request) {

        SpaceTestimonial saved = submissionService.submit(
                slug,
                request.name(),
                request.email(),
                request.content(),
                request.rating(),
                request.company(),
                request.socialLink(),
                request.website(),
                request.jobTitle(),
                Boolean.TRUE.equals(request.consent()),
                request.website2());

        if (saved == null) {
            // The honeypot was filled. The caller is told nothing.
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new SubmittedResponse(null, Instant.now()));
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new SubmittedResponse(saved.getId(), saved.getSubmittedAt()));
    }

    /**
     * The answer to a submission.
     *
     * @param id          the testimonial's identifier, or {@code null} when the
     *                   submission was discarded
     * @param submittedAt when the submission arrived
     */
    public record SubmittedResponse(java.util.UUID id, Instant submittedAt) {
    }
}
