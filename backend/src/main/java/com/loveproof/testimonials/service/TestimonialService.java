package com.loveproof.testimonials.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.domain.ModerationAction;
import com.loveproof.testimonials.domain.ModerationActionType;
import com.loveproof.testimonials.domain.Testimonial;
import com.loveproof.testimonials.domain.TestimonialStatus;
import com.loveproof.testimonials.exception.ConflictException;
import com.loveproof.testimonials.exception.DuplicateTestimonialException;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.repository.ModerationActionRepository;
import com.loveproof.testimonials.repository.TestimonialRepository;

/**
 * Business rules for testimonials: creation, public reads and moderation.
 *
 * <p>This layer owns the status transitions. A testimonial moves {@code PENDING → APPROVED} or
 * {@code PENDING → REJECTED} through a moderation decision, and the soft-delete flag moves
 * independently of the status. Every transition is written to the append-only audit log.
 */
@Service
public class TestimonialService {

    /** The window in which an identical submission counts as a duplicate (FR-1.13). */
    static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(10);

    private final TestimonialRepository testimonialRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final FingerprintService fingerprintService;
    private final Clock clock;
    private final String consentTextVersion;
    private final String ipHashSalt;

    /**
     * Creates the service.
     *
     * @param testimonialRepository      the testimonial store
     * @param moderationActionRepository the audit log store
     * @param fingerprintService         the fingerprint and IP-hash helper
     * @param clock                      the clock, injected so tests are deterministic
     * @param consentTextVersion         the identifier of the consent wording shown to visitors
     * @param ipHashSalt                 the salt used when hashing client IP addresses
     */
    public TestimonialService(
            TestimonialRepository testimonialRepository,
            ModerationActionRepository moderationActionRepository,
            FingerprintService fingerprintService,
            Clock clock,
            @Value("${loveproof.consent.text-version:v1}") String consentTextVersion,
            @Value("${loveproof.security.ip-hash-salt:loveproof-local-salt}") String ipHashSalt) {
        this.testimonialRepository = testimonialRepository;
        this.moderationActionRepository = moderationActionRepository;
        this.fingerprintService = fingerprintService;
        this.clock = clock;
        this.consentTextVersion = consentTextVersion;
        this.ipHashSalt = ipHashSalt;
    }

    /**
     * Creates a pending testimonial from a validated submission.
     *
     * <p>The name and content are trimmed; empty {@code role} and {@code company} become
     * {@code null} rather than an empty string.
     *
     * @param name          the submitter's name
     * @param role          the job title, or {@code null}
     * @param company       the company name, or {@code null}
     * @param rating        the 1-5 rating
     * @param content       the testimonial text
     * @param clientIp      the raw client address, hashed before storage; may be {@code null}
     * @return the created testimonial, in status {@link TestimonialStatus#PENDING}
     * @throws DuplicateTestimonialException when an identical submission was made inside the
     *                                        duplicate window
     */
    @Transactional
    public Testimonial create(
            String name,
            String role,
            String company,
            int rating,
            String content,
            String clientIp) {
        String trimmedName = name.strip();
        String trimmedContent = content.strip();
        String trimmedRole = normaliseOptional(role);
        String trimmedCompany = normaliseOptional(company);
        String fingerprint = fingerprintService.fingerprint(trimmedName, trimmedContent);

        Instant now = clock.instant();
        Instant windowStart = now.minus(DUPLICATE_WINDOW);
        if (testimonialRepository.countByFingerprintSince(fingerprint, windowStart) > 0) {
            throw new DuplicateTestimonialException(
                    "An identical testimonial was submitted recently. Please wait before submitting again.");
        }

        Testimonial testimonial = new Testimonial(
                UUID.randomUUID(),
                trimmedName,
                trimmedRole,
                trimmedCompany,
                rating,
                trimmedContent,
                consentTextVersion,
                now,
                fingerprint);
        testimonial.setSubmitterIpHash(fingerprintService.hashIpAddress(clientIp, ipHashSalt));

        Testimonial saved = testimonialRepository.save(testimonial);

        // The audit trail covers the full lifecycle, not just moderation.
        moderationActionRepository.save(new ModerationAction(
                saved.getId(),
                null,
                "anonymous",
                ModerationActionType.SUBMIT,
                null,
                TestimonialStatus.PENDING,
                null,
                now));

        return saved;
    }

    /**
     * Lists the public wall: approved, non-deleted testimonials.
     *
     * @param rating optional rating filter
     * @param sort   the ordering
     * @param page   the 0-based page number
     * @param size   the page size
     * @return the matching page
     */
    @Transactional(readOnly = true)
    public Page<Testimonial> findPublicWall(Integer rating, Sort sort, int page, int size) {
        Sort ordering = sort == null ? Sort.by(Sort.Direction.DESC, "submittedAt") : sort;
        Pageable pageable = PageRequest.of(page, size, ordering);
        return testimonialRepository.findLiveApproved(rating != null, rating, pageable);
    }

    /**
     * Computes the public statistics: the approved count and the average approved rating.
     *
     * @return the count and the average rating, the latter {@code null} when nothing is approved
     */
    @Transactional(readOnly = true)
    public PublicStats publicStats() {
        TestimonialRepository.PublicRatingStats stats = testimonialRepository.findPublicStats();
        long count = stats == null || stats.getTotal() == null ? 0L : stats.getTotal();
        Double averageRating = stats == null ? null : stats.getAverageRating();
        return new PublicStats(count, averageRating);
    }

    /**
     * Lists the moderation inbox.
     *
     * @param status optional status filter
     * @param query  optional free-text search across name, company and content
     * @param page   the 0-based page number
     * @param size   the page size
     * @return the matching page
     */
    @Transactional(readOnly = true)
    public Page<Testimonial> findForModeration(TestimonialStatus status, String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"));
        String search = normaliseOptional(query);
        return testimonialRepository.findForModeration(
                status != null,
                status,
                search != null,
                search == null ? "" : search,
                pageable);
    }

    /**
     * Lists the soft-deleted testimonials.
     *
     * @param page the 0-based page number
     * @param size the page size
     * @return the matching page
     */
    @Transactional(readOnly = true)
    public Page<Testimonial> findDeleted(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deletedAt"));
        return testimonialRepository.findAllByDeletedAtIsNotNull(pageable);
    }

    /**
     * Computes the dashboard counts.
     *
     * <p>Counts are derived at request time, so they cannot drift from the rows. Soft-deleted
     * records are excluded from every count except the deleted one.
     *
     * @return the dashboard counts
     */
    @Transactional(readOnly = true)
    public DashboardStats dashboardStats() {
        List<TestimonialRepository.StatusCount> rows = testimonialRepository.countByStatusExcludingDeleted();
        long pending = 0;
        long approved = 0;
        long rejected = 0;
        for (TestimonialRepository.StatusCount row : rows) {
            long count = row.getTotal() == null ? 0L : row.getTotal();
            switch (row.getStatus()) {
                case PENDING -> pending = count;
                case APPROVED -> approved = count;
                case REJECTED -> rejected = count;
            }
        }
        long deleted = testimonialRepository.countDeleted();
        long total = pending + approved + rejected;
        long last7Days = testimonialRepository.countSubmittedSince(
                clock.instant().minus(7, ChronoUnit.DAYS));

        Double averageRating = null;
        TestimonialRepository.PublicRatingStats publicStats = testimonialRepository.findPublicStats();
        if (publicStats != null) {
            averageRating = publicStats.getAverageRating();
        }

        return new DashboardStats(total, pending, approved, rejected, deleted, last7Days, averageRating);
    }

    /**
     * Reads one testimonial by id.
     *
     * @param id the identifier
     * @return the testimonial
     * @throws NotFoundException when no such testimonial exists
     */
    @Transactional(readOnly = true)
    public Testimonial findById(UUID id) {
        return testimonialRepository.findById(id).orElseThrow(() -> NotFoundException.testimonial(id));
    }

    /**
     * Approves a testimonial.
     *
     * <p>An approved testimonial becomes immediately visible on the public wall.
     *
     * @param id        the testimonial identifier
     * @param adminId   the acting administrator, or {@code null} for a system action
     * @param username  the acting administrator's username, recorded in the audit log
     * @return the moderated testimonial
     * @throws NotFoundException when no such testimonial exists
     * @throws ConflictException when the testimonial is soft-deleted
     */
    @Transactional
    public Testimonial approve(UUID id, UUID adminId, String username) {
        Testimonial testimonial = requireModeratable(id);
        TestimonialStatus previous = testimonial.getStatus();
        testimonial.moderate(TestimonialStatus.APPROVED, adminId, clock.instant(), null);
        recordAction(testimonial, adminId, username, ModerationActionType.APPROVE, previous,
                TestimonialStatus.APPROVED, null);
        return testimonial;
    }

    /**
     * Rejects a testimonial.
     *
     * @param id        the testimonial identifier
     * @param adminId   the acting administrator, or {@code null} for a system action
     * @param username  the acting administrator's username, recorded in the audit log
     * @param reason    the rejection reason; required by the database constraint, so a rejection
     *                  always carries one
     * @return the moderated testimonial
     * @throws NotFoundException when no such testimonial exists
     * @throws ConflictException when the testimonial is soft-deleted
     * @throws IllegalArgumentException when the reason is blank
     */
    @Transactional
    public Testimonial reject(UUID id, UUID adminId, String username, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection must carry a reason.");
        }
        Testimonial testimonial = requireModeratable(id);
        TestimonialStatus previous = testimonial.getStatus();
        testimonial.moderate(TestimonialStatus.REJECTED, adminId, clock.instant(), reason.strip());
        recordAction(testimonial, adminId, username, ModerationActionType.REJECT, previous,
                TestimonialStatus.REJECTED, reason.strip());
        return testimonial;
    }

    /**
     * Soft-deletes a testimonial.
     *
     * <p>The moderation status is left untouched: deletion and moderation are orthogonal.
     *
     * @param id       the testimonial identifier
     * @param adminId  the acting administrator, or {@code null} for a system action
     * @param username the acting administrator's username, recorded in the audit log
     * @param reason   the optional delete note
     * @return the deleted testimonial
     * @throws NotFoundException when no such testimonial exists
     * @throws ConflictException when the testimonial is already deleted
     */
    @Transactional
    public Testimonial softDelete(UUID id, UUID adminId, String username, String reason) {
        Testimonial testimonial = requireModeratable(id);
        TestimonialStatus previous = testimonial.getStatus();
        testimonial.softDelete(adminId, clock.instant());
        recordAction(testimonial, adminId, username, ModerationActionType.DELETE, previous, previous,
                normaliseOptional(reason));
        return testimonial;
    }

    /**
     * Restores a soft-deleted testimonial to the status it had when it was deleted.
     *
     * @param id       the testimonial identifier
     * @param adminId  the acting administrator, or {@code null} for a system action
     * @param username the acting administrator's username, recorded in the audit log
     * @return the restored testimonial
     * @throws NotFoundException when no such testimonial exists
     * @throws ConflictException when the testimonial is not deleted
     */
    @Transactional
    public Testimonial restore(UUID id, UUID adminId, String username) {
        Testimonial testimonial = testimonialRepository.findById(id)
                .orElseThrow(() -> NotFoundException.testimonial(id));
        if (!testimonial.isDeleted()) {
            throw new ConflictException("This testimonial is not deleted and cannot be restored.");
        }
        TestimonialStatus previous = testimonial.getStatus();
        testimonial.restore(clock.instant());
        recordAction(testimonial, adminId, username, ModerationActionType.RESTORE, previous, previous, null);
        return testimonial;
    }

    /**
     * Lists the audit trail of one testimonial.
     *
     * @param id the testimonial identifier
     * @return the actions, newest first
     */
    @Transactional(readOnly = true)
    public List<ModerationAction> auditTrail(UUID id) {
        return moderationActionRepository.findByTestimonialIdOrderByCreatedAtDesc(id);
    }

    /**
     * Loads a testimonial that a moderation action may be applied to.
     *
     * @param id the testimonial identifier
     * @return the testimonial
     * @throws NotFoundException when no such testimonial exists
     * @throws ConflictException when the testimonial is soft-deleted
     */
    private Testimonial requireModeratable(UUID id) {
        Testimonial testimonial = testimonialRepository.findById(id)
                .orElseThrow(() -> NotFoundException.testimonial(id));
        if (testimonial.isDeleted()) {
            throw new ConflictException("This testimonial has been deleted and cannot be moderated.");
        }
        return testimonial;
    }

    private void recordAction(
            Testimonial testimonial,
            UUID adminId,
            String username,
            ModerationActionType action,
            TestimonialStatus previous,
            TestimonialStatus current,
            String reason) {
        moderationActionRepository.save(new ModerationAction(
                testimonial.getId(),
                adminId,
                username == null || username.isBlank() ? "unknown" : username,
                action,
                previous,
                current,
                reason,
                clock.instant()));
    }

    /**
     * Normalises an optional free-text field: trimmed, and {@code null} when it is empty.
     *
     * @param value the raw value
     * @return the normalised value, or {@code null}
     */
    private static String normaliseOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * The public wall statistics.
     *
     * @param count         the number of approved, non-deleted testimonials
     * @param averageRating the average rating of those testimonials, or {@code null} when there are
     *                       none
     */
    public record PublicStats(long count, Double averageRating) {
    }

    /**
     * The dashboard counts.
     *
     * @param total        the number of non-deleted testimonials
     * @param pending      the number awaiting moderation
     * @param approved     the number approved
     * @param rejected     the number rejected
     * @param deleted      the number soft-deleted
     * @param last7Days    the number submitted in the last 7 days
     * @param averageRating the average approved rating, or {@code null} when there are none
     */
    public record DashboardStats(
            long total,
            long pending,
            long approved,
            long rejected,
            long deleted,
            long last7Days,
            Double averageRating) {
    }
}
