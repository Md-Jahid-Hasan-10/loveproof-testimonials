package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.loveproof.testimonials.domain.ModerationAction;
import com.loveproof.testimonials.repository.ModerationActionRepository;
import com.loveproof.testimonials.repository.TestimonialRepository;
import com.loveproof.testimonials.service.TestimonialService;
import com.loveproof.testimonials.support.AdminTestSupport;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the admin moderation endpoints.
 *
 * <p>Every request is signed in as the seeded administrator, so the moderation behaviour is
 * exercised through the real filter chain — the authorization rules and the CSRF check are
 * genuinely applied. The authentication itself is covered by {@code AdminAuthTest}.
 */
@AutoConfigureMockMvc
class AdminTestimonialApiTest extends PostgresIntegrationTest {

    private static final String CONTENT =
            "The onboarding was genuinely smooth and the team answered every question we had.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestimonialService testimonialService;

    @Autowired
    private TestimonialRepository testimonialRepository;

    @Autowired
    private ModerationActionRepository moderationActionRepository;

    @BeforeEach
    void clear() {
        moderationActionRepository.deleteAll();
        testimonialRepository.deleteAll();
    }

    @Test
    void approveMovesTheTestimonialToApproved() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void anApprovedTestimonialImmediatelyAppearsOnTheWall() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Jane Doe"));
    }

    @Test
    void rejectRequiresAReason() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/reject", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.reason").isNotEmpty());
    }

    @Test
    void rejectMovesTheTestimonialToRejected() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/reject", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Off topic.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void aRejectedTestimonialNeverAppearsOnTheWall() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/reject", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Off topic.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void deleteSoftDeletesTheTestimonial() throws Exception {
        UUID id = create("Jane Doe", CONTENT);
        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(delete("/api/admin/testimonials/{id}", id)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void actingOnAnAlreadyDeletedTestimonialIsAConflict() throws Exception {
        UUID id = create("Jane Doe", CONTENT);
        mockMvc.perform(asAdmin(delete("/api/admin/testimonials/{id}", id)))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void restoreBringsBackTheDeletedTestimonial() throws Exception {
        UUID id = create("Jane Doe", CONTENT);
        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(delete("/api/admin/testimonials/{id}", id)))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/restore", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void restoringALiveTestimonialIsAConflict() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/restore", id)))
                .andExpect(status().isConflict());
    }

    @Test
    void moderatingAnUnknownTestimonialIsNotFound() throws Exception {
        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void theDashboardCountsMatchTheRows() throws Exception {
        UUID approved = create("Approved", CONTENT);
        create("Pending", CONTENT + " two");
        UUID rejected = create("Rejected", CONTENT + " three");
        UUID deleted = create("Deleted", CONTENT + " four");

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", approved)))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/reject", rejected))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Off topic.\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(delete("/api/admin/testimonials/{id}", deleted)))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(get("/api/admin/testimonials/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.approved").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.deleted").value(1));
    }

    @Test
    void theInboxFiltersByStatus() throws Exception {
        UUID approved = create("Approved", CONTENT);
        create("Pending", CONTENT + " two");
        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", approved)))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(get("/api/admin/testimonials")).param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Pending"));
    }

    /**
     * The inbox must work with no filters at all.
     *
     * <p>This is the view the administrator lands on. It used to fail with a 500: the repository
     * filtered with {@code :query IS NULL}, and Hibernate cannot infer a JDBC type for a
     * parameter that is only ever compared to {@code NULL}, so it bound it as {@code bytea} and
     * PostgreSQL rejected the query with "function lower(bytea) does not exist".
     */
    @Test
    void theInboxWorksWithNoFiltersAtAll() throws Exception {
        create("No Filter", CONTENT);

        mockMvc.perform(asAdmin(get("/api/admin/testimonials")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("No Filter"));
    }

    @Test
    void theInboxSearchesAcrossNameCompanyAndContent() throws Exception {
        create("Alice Smith", "Acme Corp", CONTENT);
        create("Bob Jones", "Globex", CONTENT + " another");

        mockMvc.perform(asAdmin(get("/api/admin/testimonials")).param("q", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Alice Smith"));

        mockMvc.perform(asAdmin(get("/api/admin/testimonials")).param("q", "globex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Bob Jones"));
    }

    /**
     * The public wall must work with no rating filter.
     *
     * <p>The same {@code :param IS NULL} problem once made the rating filter unbindable, which
     * broke the wall for every visitor who did not filter by rating.
     */
    @Test
    void thePublicWallWorksWithNoRatingFilter() throws Exception {
        UUID id = create("Wall", CONTENT);
        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Wall"));
    }

    @Test
    void everyModerationActionIsRecordedInTheAuditLog() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk());

        assertThat(moderationActionRepository.countByTestimonialId(id)).isEqualTo(2);
    }

    @Test
    void theAuditTrailAttributesTheActionToTheSignedInAdministrator() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk());

        List<ModerationAction> actions =
                moderationActionRepository.findByTestimonialIdOrderByCreatedAtDesc(id);

        // The newest action is the approval; the older one is the submission.
        assertThat(actions).hasSize(2);
        assertThat(actions.get(0).getAction().name()).isEqualTo("APPROVE");
        assertThat(actions.get(0).getAdminUsername()).isEqualTo("testadmin");
        assertThat(actions.get(0).getAdminUserId()).isNotNull();

        // The submission was made by the anonymous visitor, not by the administrator.
        assertThat(actions.get(1).getAction().name()).isEqualTo("SUBMIT");
        assertThat(actions.get(1).getAdminUsername()).isEqualTo("anonymous");
        assertThat(actions.get(1).getAdminUserId()).isNull();
    }

    /**
     * A single testimonial can be read regardless of its moderation state.
     *
     * <p>The public wall only publishes approved items, but the administrator needs to see a
     * pending or rejected item in its actual state.
     */
    @Test
    void aSingleTestimonialIsReadableWhateverItsStatus() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdmin(get("/api/admin/testimonials/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    void readingAnUnknownTestimonialIsNotFound() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/testimonials/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /**
     * The audit trail endpoint returns the whole lifecycle, newest first.
     *
     * <p>The submission itself is in the trail, so the trail explains how a testimonial reached
     * its current state rather than only listing the moderation decisions.
     */
    @Test
    void theAuditTrailIsReadableOverHttp() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdmin(post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(get("/api/admin/testimonials/{id}/audit", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("APPROVE"))
                .andExpect(jsonPath("$[0].adminUsername").value("testadmin"))
                .andExpect(jsonPath("$[0].previousStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].newStatus").value("APPROVED"))
                .andExpect(jsonPath("$[1].action").value("SUBMIT"))
                .andExpect(jsonPath("$[1].adminUsername").value("anonymous"));
    }

    @Test
    void theAuditTrailIsNotReachableWithoutASession() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(get("/api/admin/testimonials/{id}/audit", id))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Signs a request in as the seeded administrator, with a valid CSRF token.
     *
     * @param builder the request
     * @return the request, signed in
     * @throws Exception when the login fails
     */
    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder)
            throws Exception {
        return AdminTestSupport.asAdmin(mockMvc, builder);
    }

    /**
     * Creates a pending testimonial.
     *
     * @param name    the name
     * @param content the testimonial text
     * @return the identifier
     */
    private UUID create(String name, String content) {
        return create(name, null, content);
    }

    /**
     * Creates a pending testimonial.
     *
     * @param name    the name
     * @param company the company
     * @param content the testimonial text
     * @return the identifier
     */
    private UUID create(String name, String company, String content) {
        return testimonialService.create(name, null, company, 5, content, null).getId();
    }
}
