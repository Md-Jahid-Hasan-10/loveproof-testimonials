package com.loveproof.testimonials.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import com.loveproof.testimonials.domain.ModerationAction;
import com.loveproof.testimonials.domain.ModerationActionType;
import com.loveproof.testimonials.domain.Testimonial;
import com.loveproof.testimonials.domain.TestimonialStatus;
import com.loveproof.testimonials.exception.ConflictException;
import com.loveproof.testimonials.exception.DuplicateTestimonialException;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.repository.ModerationActionRepository;
import com.loveproof.testimonials.repository.TestimonialRepository;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the business rules: creation, normalisation, duplicate detection and — above all — the
 * status transitions.
 *
 * <p>The clock is fixed so the duplicate window and the "last 7 days" count are deterministic
 * (NFR-TEST-5).
 */
@Import(TestimonialServiceTest.FixedClockConfig.class)
@TestPropertySource(properties = {
        "loveproof.consent.text-version=v1-test",
        "loveproof.security.ip-hash-salt=test-salt"
})
class TestimonialServiceTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");
    private static final String CONTENT =
            "The onboarding was genuinely smooth and the team answered every question we had.";

    @Autowired
    private TestimonialService service;

    @Autowired
    private TestimonialRepository testimonialRepository;

    @Autowired
    private ModerationActionRepository moderationActionRepository;

    @BeforeEach
    void clear() {
        moderationActionRepository.deleteAll();
        testimonialRepository.deleteAll();
        clock.reset();
        adminId = adminUserRepository.findByUsername("testadmin")
                .orElseThrow()
                .getId();
    }

    // --- Creation ---------------------------------------------------------

    @Test
    void newSubmissionStartsAsPending() {
        Testimonial created = service.create("Jane Doe", "Engineer", "Acme", 5, CONTENT, "1.2.3.4");

        assertThat(created.getStatus()).isEqualTo(TestimonialStatus.PENDING);
        assertThat(created.getSubmittedAt()).isEqualTo(NOW);
        assertThat(created.getConsentTextVersion()).isEqualTo("v1-test");
        assertThat(created.isConsentGiven()).isTrue();
        assertThat(created.isDeleted()).isFalse();
    }

    @Test
    void creationTrimsWhitespace() {
        Testimonial created = service.create("  Jane Doe  ", "  Engineer  ", "  Acme  ", 5, "  " + CONTENT + "  ", null);

        assertThat(created.getName()).isEqualTo("Jane Doe");
        assertThat(created.getRole()).isEqualTo("Engineer");
        assertThat(created.getCompany()).isEqualTo("Acme");
        assertThat(created.getContent()).isEqualTo(CONTENT);
    }

    @Test
    void emptyRoleAndCompanyBecomeNull() {
        Testimonial created = service.create("Jane Doe", "   ", "", 5, CONTENT, null);

        assertThat(created.getRole()).isNull();
        assertThat(created.getCompany()).isNull();
    }

    @Test
    void creationStoresTheHashedIpNeverTheRawAddress() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, "203.0.113.9");

        assertThat(created.getSubmitterIpHash()).isNotNull();
        assertThat(created.getSubmitterIpHash()).isNotEqualTo("203.0.113.9");
        assertThat(created.getSubmitterIpHash()).hasSize(64);
    }

    @Test
    void creationRecordsASubmitAuditEntry() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);

        List<ModerationAction> trail = service.auditTrail(created.getId());

        assertThat(trail).hasSize(1);
        assertThat(trail.get(0).getAction()).isEqualTo(ModerationActionType.SUBMIT);
        assertThat(trail.get(0).getNewStatus()).isEqualTo(TestimonialStatus.PENDING);
    }

    // --- Duplicate detection ---------------------------------------------

    @Test
    void identicalSubmissionInsideTheWindowIsRejected() {
        service.create("Jane Doe", null, null, 5, CONTENT, null);

        assertThatThrownBy(() -> service.create("Jane Doe", null, null, 5, CONTENT, null))
                .isInstanceOf(DuplicateTestimonialException.class);
    }

    @Test
    void identicalSubmissionIgnoringNameCasingIsRejected() {
        service.create("Jane Doe", null, null, 5, CONTENT, null);

        assertThatThrownBy(() -> service.create("jane doe", null, null, 5, CONTENT, null))
                .isInstanceOf(DuplicateTestimonialException.class);
    }

    @Test
    void identicalSubmissionIgnoringWhitespaceIsRejected() {
        service.create("Jane Doe", null, null, 5, CONTENT, null);

        assertThatThrownBy(() -> service.create("  Jane Doe  ", null, null, 5, "  " + CONTENT + "  ", null))
                .isInstanceOf(DuplicateTestimonialException.class);
    }

    @Test
    void differentContentIsNotADuplicate() {
        service.create("Jane Doe", null, null, 5, CONTENT, null);

        Testimonial second = service.create(
                "Jane Doe", null, null, 5, "A completely different testimonial body entirely.", null);

        assertThat(second.getId()).isNotNull();
    }

    @Test
    void submissionOutsideTheWindowIsNotADuplicate() {
        service.create("Jane Doe", null, null, 5, CONTENT, null);

        // Advance past the ten-minute window.
        clock.advance(TestimonialService.DUPLICATE_WINDOW.plusSeconds(1));

        Testimonial second = service.create("Jane Doe", null, null, 5, CONTENT, null);

        assertThat(second.getId()).isNotNull();
    }

    // --- Status transitions ----------------------------------------------

    @Test
    void approveMovesPendingToApproved() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);

        Testimonial approved = service.approve(created.getId(), adminId, "admin");

        assertThat(approved.getStatus()).isEqualTo(TestimonialStatus.APPROVED);
        assertThat(approved.getModeratedAt()).isEqualTo(NOW);
        assertThat(approved.getModeratedBy()).isEqualTo(adminId);
        assertThat(approved.getRejectionReason()).isNull();
        assertThat(approved.isLiveAndApproved()).isTrue();
    }

    @Test
    void rejectMovesPendingToRejectedAndStoresTheReason() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);

        Testimonial rejected = service.reject(created.getId(), adminId, "admin", "Not relevant to us.");

        assertThat(rejected.getStatus()).isEqualTo(TestimonialStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Not relevant to us.");
        assertThat(rejected.getModeratedAt()).isEqualTo(NOW);
        assertThat(rejected.isLiveAndApproved()).isFalse();
    }

    @Test
    void rejectWithoutAReasonIsRejected() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);

        assertThatThrownBy(() -> service.reject(created.getId(), adminId, "admin", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reject(created.getId(), adminId, "admin", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approvingClearsAPreviousRejectionReason() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);
        service.reject(created.getId(), adminId, "admin", "Changed my mind.");

        Testimonial approved = service.approve(created.getId(), adminId, "admin");

        assertThat(approved.getStatus()).isEqualTo(TestimonialStatus.APPROVED);
        assertThat(approved.getRejectionReason()).isNull();
    }

    @Test
    void softDeleteSetsTheDeletionMarkerAndLeavesTheStatusAlone() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);
        service.approve(created.getId(), adminId, "admin");

        Testimonial deleted = service.softDelete(created.getId(), adminId, "admin", "Spam");

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isEqualTo(NOW);
        assertThat(deleted.getDeletedBy()).isEqualTo(adminId);
        // Deletion and moderation are orthogonal: the status survives the delete.
        assertThat(deleted.getStatus()).isEqualTo(TestimonialStatus.APPROVED);
        assertThat(deleted.isLiveAndApproved()).isFalse();
    }

    @Test
    void restoreBringsBackThePreviousStatus() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);
        service.approve(created.getId(), adminId, "admin");
        service.softDelete(created.getId(), adminId, "admin", null);

        Testimonial restored = service.restore(created.getId(), adminId, "admin");

        assertThat(restored.isDeleted()).isFalse();
        assertThat(restored.getDeletedAt()).isNull();
        assertThat(restored.getStatus()).isEqualTo(TestimonialStatus.APPROVED);
        assertThat(restored.isLiveAndApproved()).isTrue();
    }

    @Test
    void moderatingAnUnknownTestimonialIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.approve(unknown, adminId, "admin"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.reject(unknown, adminId, "admin", "Reason."))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.softDelete(unknown, adminId, "admin", null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.restore(unknown, adminId, "admin"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void moderatingADeletedTestimonialIsAConflict() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);
        service.softDelete(created.getId(), adminId, "admin", null);

        assertThatThrownBy(() -> service.approve(created.getId(), adminId, "admin"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.reject(created.getId(), adminId, "admin", "Reason."))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deletingAnAlreadyDeletedTestimonialIsAConflict() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);
        service.softDelete(created.getId(), adminId, "admin", null);

        assertThatThrownBy(() -> service.softDelete(created.getId(), adminId, "admin", null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void restoringALiveTestimonialIsAConflict() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);

        assertThatThrownBy(() -> service.restore(created.getId(), adminId, "admin"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void everyModerationActionIsWrittenToTheAuditTrail() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);

        service.approve(created.getId(), adminId, "admin");
        service.softDelete(created.getId(), adminId, "admin", "Mistake");
        service.restore(created.getId(), adminId, "admin");

        List<ModerationAction> trail = service.auditTrail(created.getId());

        assertThat(trail).hasSize(4);
        assertThat(trail).extracting(ModerationAction::getAction)
                .containsExactlyInAnyOrder(
                        ModerationActionType.SUBMIT,
                        ModerationActionType.APPROVE,
                        ModerationActionType.DELETE,
                        ModerationActionType.RESTORE);
        assertThat(trail).allSatisfy(action -> {
            assertThat(action.getAdminUsername()).isNotBlank();
            assertThat(action.getTestimonialId()).isEqualTo(created.getId());
        });

        // The submission itself is recorded by the anonymous visitor, not by an administrator.
        ModerationAction submit = trail.stream()
                .filter(a -> a.getAction() == ModerationActionType.SUBMIT)
                .findFirst()
                .orElseThrow();
        assertThat(submit.getAdminUsername()).isEqualTo("anonymous");
        assertThat(submit.getAdminUserId()).isNull();

        // Every moderation action names the acting administrator.
        assertThat(trail).filteredOn(a -> a.getAction() != ModerationActionType.SUBMIT)
                .allSatisfy(action -> {
                    assertThat(action.getAdminUsername()).isEqualTo("admin");
                    assertThat(action.getAdminUserId()).isEqualTo(adminId);
                });
    }

    @Test
    void theAuditTrailRecordsTheStatusBeforeAndAfterEachAction() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);

        service.approve(created.getId(), adminId, "admin");

        List<ModerationAction> trail = service.auditTrail(created.getId());
        ModerationAction approve = trail.stream()
                .filter(a -> a.getAction() == ModerationActionType.APPROVE)
                .findFirst()
                .orElseThrow();

        assertThat(approve.getPreviousStatus()).isEqualTo(TestimonialStatus.PENDING);
        assertThat(approve.getNewStatus()).isEqualTo(TestimonialStatus.APPROVED);
    }

    @Test
    void theAuditTrailRecordsTheRejectionReason() {
        Testimonial created = service.create("Jane Doe", null, null, 5, CONTENT, null);

        service.reject(created.getId(), adminId, "admin", "Off topic.");

        ModerationAction reject = service.auditTrail(created.getId()).stream()
                .filter(a -> a.getAction() == ModerationActionType.REJECT)
                .findFirst()
                .orElseThrow();

        assertThat(reject.getReason()).isEqualTo("Off topic.");
    }

    // --- Reads -------------------------------------------------------------

    @Test
    void theWallShowsOnlyApprovedNonDeletedTestimonials() {
        Testimonial approved = service.create("Approved", null, null, 5, CONTENT, null);
        service.create("Pending", null, null, 3, CONTENT + " two", null);
        Testimonial rejected = service.create("Rejected", null, null, 2, CONTENT + " three", null);
        Testimonial deleted = service.create("Deleted", null, null, 1, CONTENT + " four", null);

        service.approve(approved.getId(), adminId, "admin");
        service.reject(rejected.getId(), adminId, "admin", "Reason.");
        service.softDelete(deleted.getId(), adminId, "admin", null);

        var wall = service.findPublicWall(null, null, 0, 20);

        assertThat(wall.getTotalElements()).isEqualTo(1);
        assertThat(wall.getContent()).extracting(Testimonial::getName).containsExactly("Approved");
    }

    @Test
    void theDashboardCountsExcludeDeletedRowsExceptTheDeletedCount() {
        Testimonial approved = service.create("Approved", null, null, 5, CONTENT, null);
        service.create("Pending", null, null, 3, CONTENT + " two", null);
        Testimonial rejected = service.create("Rejected", null, null, 2, CONTENT + " three", null);
        Testimonial deleted = service.create("Deleted", null, null, 1, CONTENT + " four", null);

        service.approve(approved.getId(), adminId, "admin");
        service.reject(rejected.getId(), adminId, "admin", "Reason.");
        service.softDelete(deleted.getId(), adminId, "admin", null);

        TestimonialService.DashboardStats stats = service.dashboardStats();

        assertThat(stats.total()).isEqualTo(3);
        assertThat(stats.pending()).isEqualTo(1);
        assertThat(stats.approved()).isEqualTo(1);
        assertThat(stats.rejected()).isEqualTo(1);
        assertThat(stats.deleted()).isEqualTo(1);
        assertThat(stats.averageRating()).isEqualTo(5.0);
    }

    @Test
    void theDashboardCountsSubmissionsFromTheLastSevenDays() {
        service.create("Recent", null, null, 5, CONTENT, null);

        TestimonialService.DashboardStats stats = service.dashboardStats();

        assertThat(stats.last7Days()).isEqualTo(1);
    }

    @Test
    void theModerationInboxSupportsAStatusFilter() {
        Testimonial approved = service.create("Approved", null, null, 5, CONTENT, null);
        service.create("Pending", null, null, 3, CONTENT + " two", null);
        service.approve(approved.getId(), adminId, "admin");

        var pendingOnly = service.findForModeration(TestimonialStatus.PENDING, null, 0, 20);
        var approvedOnly = service.findForModeration(TestimonialStatus.APPROVED, null, 0, 20);

        assertThat(pendingOnly.getContent()).extracting(Testimonial::getName).containsExactly("Pending");
        assertThat(approvedOnly.getContent()).extracting(Testimonial::getName).containsExactly("Approved");
    }

    @Test
    void theModerationInboxSupportsFreeTextSearch() {
        service.create("Alice Smith", null, "Acme Corp", 5, CONTENT, null);
        service.create("Bob Jones", null, "Globex", 3, CONTENT + " another", null);

        var byName = service.findForModeration(null, "alice", 0, 20);
        var byCompany = service.findForModeration(null, "globex", 0, 20);
        var byContent = service.findForModeration(null, "another", 0, 20);
        var noMatch = service.findForModeration(null, "nobody", 0, 20);

        assertThat(byName.getContent()).extracting(Testimonial::getName).containsExactly("Alice Smith");
        assertThat(byCompany.getContent()).extracting(Testimonial::getName).containsExactly("Bob Jones");
        assertThat(byContent.getContent()).extracting(Testimonial::getName).containsExactly("Bob Jones");
        assertThat(noMatch.getTotalElements()).isZero();
    }

    @Test
    void theDeletedInboxListsOnlyDeletedTestimonials() {
        Testimonial deleted = service.create("Deleted", null, null, 5, CONTENT, null);
        service.create("Live", null, null, 3, CONTENT + " two", null);
        service.softDelete(deleted.getId(), adminId, "admin", null);

        var inbox = service.findDeleted(0, 20);

        assertThat(inbox.getTotalElements()).isEqualTo(1);
        assertThat(inbox.getContent()).extracting(Testimonial::getName).containsExactly("Deleted");
    }

    /**
     * The acting administrator used throughout the tests. It is the seeded {@code testadmin}
     * account, because {@code moderation_action.admin_user_id} is a real foreign key.
     */
    @Autowired
    private com.loveproof.testimonials.repository.AdminUserRepository adminUserRepository;

    /**
     * The acting administrator's identifier.
     */
    private UUID adminId;

    /**
     * The clock the service reads, advanced by the tests.
     */
    @Autowired
    private MutableClock clock;

    /**
     * Supplies the adjustable clock to the service under test.
     */
    @TestConfiguration
    static class FixedClockConfig {

        /**
         * The clock bean.
         *
         * @return the clock
         */
        @Bean
        @Primary
        MutableClock adjustableClock() {
            return new MutableClock();
        }
    }

    /**
     * A clock whose instant can be advanced by the test.
     */
    static class MutableClock extends Clock {

        private Instant instant = NOW;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        /**
         * Moves the clock forward.
         *
         * @param duration how far to move
         */
        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        /**
         * Returns the clock to its starting instant.
         */
        void reset() {
            instant = NOW;
        }
    }
}
