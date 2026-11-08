package com.loveproof.testimonials.web;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.domain.ModerationAction;
import com.loveproof.testimonials.domain.ModerationActionType;
import com.loveproof.testimonials.domain.Testimonial;
import com.loveproof.testimonials.domain.TestimonialStatus;
import com.loveproof.testimonials.service.AdminSessionService;
import com.loveproof.testimonials.service.TestimonialService;
import com.loveproof.testimonials.web.dto.PageResponse;
import com.loveproof.testimonials.web.dto.TestimonialResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The admin moderation API.
 *
 * <p><strong>Access control.</strong> Spring Security requires an authenticated session with the
 * {@code ADMIN} role for every endpoint under {@code /api/admin/**}, and a CSRF token on every
 * state-changing request. The rules live in {@code SecurityConfig}, not here — the controller
 * never checks a role itself.
 *
 * <p>Every moderation action is written to the append-only audit log by the service layer, and is
 * attributed to the administrator whose session made the request.
 */
@RestController
@RequestMapping("/api/admin/testimonials")
public class AdminTestimonialController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TestimonialService testimonialService;
    private final AdminSessionService adminSessionService;

    /**
     * Creates the controller.
     *
     * @param testimonialService the service holding the business rules
     * @param adminSessionService the service resolving the acting administrator
     */
    public AdminTestimonialController(
            TestimonialService testimonialService,
            AdminSessionService adminSessionService) {
        this.testimonialService = testimonialService;
        this.adminSessionService = adminSessionService;
    }

    /**
     * The dashboard counts.
     *
     * @return the counts
     */
    @GetMapping("/stats")
    public DashboardStatsResponse stats() {
        TestimonialService.DashboardStats stats = testimonialService.dashboardStats();
        return new DashboardStatsResponse(
                stats.total(),
                stats.pending(),
                stats.approved(),
                stats.rejected(),
                stats.deleted(),
                stats.last7Days(),
                stats.averageRating());
    }

    /**
     * The moderation inbox.
     *
     * @param status optional status filter
     * @param q      optional free-text search across name, company and content
     * @param page   the 0-based page number
     * @param size   the page size
     * @return the matching page
     */
    @GetMapping
    public PageResponse<TestimonialResponse> inbox(
            @RequestParam(required = false) TestimonialStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Page<Testimonial> results = testimonialService.findForModeration(status, q, safePage, safeSize);
        return PageResponse.from(results, TestimonialResponse::from);
    }

    /**
     * The soft-deleted testimonials.
     *
     * @param page the 0-based page number
     * @param size the page size
     * @return the matching page
     */
    @GetMapping("/deleted")
    public PageResponse<TestimonialResponse> deleted(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Testimonial> results = testimonialService.findDeleted(Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(results, TestimonialResponse::from);
    }

    /**
     * A single testimonial, including its moderation state.
     *
     * <p>Unlike the public wall, this returns pending, rejected and soft-deleted items too: the
     * administrator needs to see a testimonial's actual state, not the published projection of it.
     *
     * @param id the testimonial identifier
     * @return the testimonial
     */
    @GetMapping("/{id}")
    public TestimonialResponse one(@PathVariable UUID id) {
        return TestimonialResponse.from(testimonialService.findById(id));
    }

    /**
     * The append-only moderation history of one testimonial.
     *
     * <p>Newest first. The trail covers the whole lifecycle, so the submission appears alongside
     * the moderation decisions.
     *
     * @param id the testimonial identifier
     * @return the audit entries
     */
    @GetMapping("/{id}/audit")
    public List<ModerationActionResponse> auditTrail(@PathVariable UUID id) {
        return testimonialService.auditTrail(id).stream()
                .map(ModerationActionResponse::from)
                .toList();
    }

    /**
     * Approves a testimonial, making it immediately visible on the public wall.
     *
     * @param id the testimonial identifier
     * @return the moderated testimonial
     */
    @PostMapping("/{id}/approve")
    public TestimonialResponse approve(@PathVariable UUID id) {
        return TestimonialResponse.from(testimonialService.approve(
                id, adminSessionService.currentAdminId(), adminSessionService.currentUsername()));
    }

    /**
     * Rejects a testimonial with a reason.
     *
     * @param id      the testimonial identifier
     * @param request the rejection request
     * @return the moderated testimonial
     */
    @PostMapping("/{id}/reject")
    public TestimonialResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return TestimonialResponse.from(testimonialService.reject(
                id,
                adminSessionService.currentAdminId(),
                adminSessionService.currentUsername(),
                request.reason()));
    }

    /**
     * Soft-deletes a testimonial.
     *
     * @param id the testimonial identifier
     * @return the deleted testimonial
     */
    @DeleteMapping("/{id}")
    public TestimonialResponse softDelete(@PathVariable UUID id) {
        return TestimonialResponse.from(testimonialService.softDelete(
                id, adminSessionService.currentAdminId(), adminSessionService.currentUsername(), null));
    }

    /**
     * Restores a soft-deleted testimonial to the status it had when it was deleted.
     *
     * @param id the testimonial identifier
     * @return the restored testimonial
     */
    @PostMapping("/{id}/restore")
    public TestimonialResponse restore(@PathVariable UUID id) {
        return TestimonialResponse.from(testimonialService.restore(
                id, adminSessionService.currentAdminId(), adminSessionService.currentUsername()));
    }

    /**
     * The body of a rejection request.
     *
     * @param reason the rejection reason, required
     */
    public record RejectRequest(
            @NotBlank(message = "A rejection reason is required.")
            @Size(max = 255, message = "The rejection reason must be at most 255 characters.")
            String reason) {
    }

    /**
     * One audit entry.
     *
     * @param action         the kind of action
     * @param adminUsername  the acting account; {@code anonymous} for the original submission
     * @param adminUserId    the acting account's identifier, or {@code null} for an anonymous
     *                       submission or a removed account
     * @param previousStatus the status before the action, or {@code null}
     * @param newStatus      the status after the action, or {@code null}
     * @param reason         the optional reason, for example a rejection reason
     * @param createdAt      when the action happened
     */
    public record ModerationActionResponse(
            ModerationActionType action,
            String adminUsername,
            UUID adminUserId,
            TestimonialStatus previousStatus,
            TestimonialStatus newStatus,
            String reason,
            Instant createdAt) {

        /**
         * Maps an audit entry to its response shape.
         *
         * @param action the audit entry
         * @return the response
         */
        static ModerationActionResponse from(ModerationAction action) {
            return new ModerationActionResponse(
                    action.getAction(),
                    action.getAdminUsername(),
                    action.getAdminUserId(),
                    action.getPreviousStatus(),
                    action.getNewStatus(),
                    action.getReason(),
                    action.getCreatedAt());
        }
    }

    /**
     * The dashboard counts.
     *
     * @param total         the number of non-deleted testimonials
     * @param pending       the number awaiting moderation
     * @param approved      the number approved
     * @param rejected      the number rejected
     * @param deleted       the number soft-deleted
     * @param last7Days     the number submitted in the last 7 days
     * @param averageRating the average approved rating, or {@code null} when there are none
     */
    public record DashboardStatsResponse(
            long total,
            long pending,
            long approved,
            long rejected,
            long deleted,
            long last7Days,
            Double averageRating) {
    }
}
