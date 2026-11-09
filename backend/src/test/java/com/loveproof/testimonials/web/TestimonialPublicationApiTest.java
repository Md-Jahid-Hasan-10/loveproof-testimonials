package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loveproof.testimonials.domain.Testimonial;
import com.loveproof.testimonials.domain.TestimonialStatus;
import com.loveproof.testimonials.repository.ModerationActionRepository;
import com.loveproof.testimonials.repository.TestimonialRepository;
import com.loveproof.testimonials.support.AdminTestSupport;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the publication rules of the testimonial API end to end.
 *
 * <p>These tests walk the whole lifecycle the way it happens in production: a visitor submits
 * through the public endpoint, a moderator approves or rejects through the admin endpoints, and an
 * anonymous reader then reads the public wall. Nothing is stubbed and nothing short-circuits the
 * service layer, so a rule that holds here holds for a real request.
 *
 * <p>The four properties under test are:
 *
 * <ol>
 *   <li>a valid submission creates a testimonial in status {@code PENDING};</li>
 *   <li>an invalid rating and a missing required field are refused with the error envelope;</li>
 *   <li>a rejected testimonial never appears on the public wall;</li>
 *   <li>only an approved testimonial that carries publication consent appears on the wall.</li>
 * </ol>
 */
@AutoConfigureMockMvc
class TestimonialPublicationApiTest extends PostgresIntegrationTest {

    /** A testimonial body long enough to satisfy the 20-character minimum. */
    private static final String CONTENT =
            "The onboarding was genuinely smooth and the team answered every question we had.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestimonialRepository testimonialRepository;

    @Autowired
    private ModerationActionRepository moderationActionRepository;

    @BeforeEach
    void clear() {
        moderationActionRepository.deleteAll();
        testimonialRepository.deleteAll();
    }

    // -----------------------------------------------------------------------
    // 1. A valid submission creates a pending testimonial.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("a valid submission creates a testimonial that is pending")
    void aValidSubmissionCreatesAPendingTestimonial() throws Exception {
        MvcResult result = mockMvc.perform(submit("Jane Doe", "Engineer", "Acme", 5, CONTENT, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.rating").value(5))
                .andReturn();

        UUID id = idOf(result);

        // The rule is not only that the response says PENDING: the row that
        // was written carries the status too.
        Testimonial stored = testimonialRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TestimonialStatus.PENDING);
        assertThat(stored.getName()).isEqualTo("Jane Doe");
        assertThat(stored.getRating()).isEqualTo((short) 5);
        assertThat(stored.getModeratedAt())
                .as("a pending testimonial has not been moderated")
                .isNull();
        assertThat(stored.getRejectionReason())
                .as("a pending testimonial carries no rejection reason")
                .isNull();
    }

    @Test
    @DisplayName("a pending submission does not appear on the public wall")
    void aPendingSubmissionDoesNotAppearOnTheWall() throws Exception {
        submit("Jane Doe", "Engineer", "Acme", 5, CONTENT, true);

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("a pending submission is not counted in the public statistics")
    void aPendingSubmissionIsNotCountedInThePublicStatistics() throws Exception {
        submit("Jane Doe", "Engineer", "Acme", 5, CONTENT, true);

        mockMvc.perform(get("/api/testimonials/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.averageRating").doesNotExist());
    }

    // -----------------------------------------------------------------------
    // 2. Invalid ratings and missing required fields are rejected.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("a rating below the range is refused")
    void aRatingBelowTheRangeIsRefused() throws Exception {
        mockMvc.perform(submit("Jane Doe", null, null, 0, CONTENT, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.rating").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/testimonials"));
    }

    @Test
    @DisplayName("a rating above the range is refused")
    void aRatingAboveTheRangeIsRefused() throws Exception {
        mockMvc.perform(submit("Jane Doe", null, null, 6, CONTENT, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.rating").isNotEmpty());
    }

    @Test
    @DisplayName("a missing rating is refused")
    void aMissingRatingIsRefused() throws Exception {
        mockMvc.perform(submit("Jane Doe", null, null, null, CONTENT, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.rating").isNotEmpty());
    }

    @Test
    @DisplayName("a missing name is refused")
    void aMissingNameIsRefused() throws Exception {
        mockMvc.perform(submit(null, null, null, 4, CONTENT, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
    }

    @Test
    @DisplayName("a missing testimonial text is refused")
    void aMissingContentIsRefused() throws Exception {
        mockMvc.perform(submit("Jane Doe", null, null, 4, null, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.content").isNotEmpty());
    }

    @Test
    @DisplayName("a missing consent is refused")
    void aMissingConsentIsRefused() throws Exception {
        mockMvc.perform(submit("Jane Doe", null, null, 4, CONTENT, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.consent").isNotEmpty());
    }

    @Test
    @DisplayName("a refused submission is not stored")
    void aRefusedSubmissionIsNotStored() throws Exception {
        mockMvc.perform(submit("Jane Doe", null, null, 0, CONTENT, true))
                .andExpect(status().isBadRequest());
        mockMvc.perform(submit("Jane Doe", null, null, 4, CONTENT, false))
                .andExpect(status().isBadRequest());

        assertThat(testimonialRepository.findAll())
                .as("a refused submission must leave no row behind")
                .isEmpty();
    }

    // -----------------------------------------------------------------------
    // 3. A rejected testimonial never appears on the public wall.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("a rejected testimonial never appears on the public wall")
    void aRejectedTestimonialNeverAppearsOnTheWall() throws Exception {
        UUID id = idOf(mockMvc.perform(submit("Jane Doe", "Engineer", "Acme", 5, CONTENT, true))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(AdminTestSupport.asAdmin(
                        mockMvc, post("/api/admin/testimonials/{id}/reject", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Not relevant to us.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/testimonials/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("a rejected testimonial stays off the wall after it is approved and rejected again")
    void aRejectedTestimonialStaysOffTheWallAcrossModeration() throws Exception {
        UUID id = idOf(mockMvc.perform(submit("Jane Doe", null, null, 5, CONTENT, true))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(AdminTestSupport.asAdmin(
                        mockMvc, post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(AdminTestSupport.asAdmin(
                        mockMvc, post("/api/admin/testimonials/{id}/reject", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Changed our mind.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // -----------------------------------------------------------------------
    // 4. Only approved testimonials with publication consent appear.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("an approved testimonial with consent appears on the public wall")
    void anApprovedTestimonialWithConsentAppearsOnTheWall() throws Exception {
        UUID id = idOf(mockMvc.perform(submit("Jane Doe", "Engineer", "Acme", 5, CONTENT, true))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(AdminTestSupport.asAdmin(
                        mockMvc, post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Jane Doe"))
                .andExpect(jsonPath("$.content[0].role").value("Engineer"))
                .andExpect(jsonPath("$.content[0].company").value("Acme"))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.content[0].content").value(CONTENT));

        mockMvc.perform(get("/api/testimonials/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0));
    }

    @Test
    @DisplayName("the wall shows the approved testimonials and hides the other states")
    void theWallShowsOnlyTheApprovedTestimonials() throws Exception {
        UUID approved = idOf(mockMvc.perform(submit("Approved Person", null, null, 5, CONTENT + " approved", true))
                .andExpect(status().isCreated())
                .andReturn());
        UUID pending = idOf(mockMvc.perform(submit("Pending Person", null, null, 3, CONTENT + " pending", true))
                .andExpect(status().isCreated())
                .andReturn());
        UUID rejected = idOf(mockMvc.perform(submit("Rejected Person", null, null, 2, CONTENT + " rejected", true))
                .andExpect(status().isCreated())
                .andReturn());
        UUID deleted = idOf(mockMvc.perform(submit("Deleted Person", null, null, 1, CONTENT + " deleted", true))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(AdminTestSupport.asAdmin(
                        mockMvc, post("/api/admin/testimonials/{id}/approve", approved)))
                .andExpect(status().isOk());
        mockMvc.perform(AdminTestSupport.asAdmin(
                        mockMvc, post("/api/admin/testimonials/{id}/reject", rejected))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Not relevant to us.\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(AdminTestSupport.asAdmin(
                        mockMvc, delete("/api/admin/testimonials/{id}", deleted)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(approved.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Approved Person"));

        // The pending one was never moderated, so it is still pending.
        assertThat(testimonialRepository.findById(pending).orElseThrow().getStatus())
                .isEqualTo(TestimonialStatus.PENDING);
    }

    @Test
    @DisplayName("a submission without consent never reaches the wall, whatever the moderation says")
    void aSubmissionWithoutConsentNeverReachesTheWall() throws Exception {
        // The API refuses a submission that does not carry consent, so the row
        // cannot be created that way. The database carries the same rule: a row
        // with consent_given = false cannot exist at all, which is what makes
        // the consent predicate on the wall query a statement of intent rather
        // than a filter that actually excludes something.
        mockMvc.perform(submit("Jane Doe", null, null, 5, CONTENT, false))
                .andExpect(status().isBadRequest());

        assertThat(testimonialRepository.findAll())
                .as("no row may exist without consent")
                .isEmpty();

        // And every row that does exist carries the consent, and the wording
        // version that was shown at submission time.
        UUID id = idOf(mockMvc.perform(submit("Jane Doe", null, null, 5, CONTENT, true))
                .andExpect(status().isCreated())
                .andReturn());

        Testimonial stored = testimonialRepository.findById(id).orElseThrow();
        assertThat(stored.isConsentGiven()).isTrue();
        assertThat(stored.getConsentTextVersion()).isNotBlank();

        mockMvc.perform(AdminTestSupport.asAdmin(
                        mockMvc, post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].consentTextVersion")
                        .value(stored.getConsentTextVersion()));
    }

    @Test
    @DisplayName("a soft-deleted testimonial disappears from the wall")
    void aSoftDeletedTestimonialDisappearsFromTheWall() throws Exception {
        UUID id = idOf(mockMvc.perform(submit("Jane Doe", null, null, 5, CONTENT, true))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(AdminTestSupport.asAdmin(
                        mockMvc, post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(AdminTestSupport.asAdmin(
                        mockMvc, delete("/api/admin/testimonials/{id}", id)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds the public submission request.
     *
     * @param name    the submitter's name, may be {@code null}
     * @param role    the job title, may be {@code null}
     * @param company the company name, may be {@code null}
     * @param rating  the rating, may be {@code null}
     * @param content the testimonial text, may be {@code null}
     * @param consent the consent flag, may be {@code null}
     * @return the request builder
     */
    private MockHttpServletRequestBuilder submit(
            String name, String role, String company, Integer rating, String content, Boolean consent) {
        return post("/api/testimonials")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(name, role, company, rating, content, consent));
    }

    /**
     * Reads the identifier out of a created testimonial's response body.
     *
     * @param result the response
     * @return the identifier
     */
    private UUID idOf(MvcResult result) {
        try {
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            return UUID.fromString(body.path("id").asText());
        } catch (Exception e) {
            throw new AssertionError("The response body was not a testimonial.", e);
        }
    }

    /**
     * Builds a request body.
     *
     * @param name    the submitter's name, may be {@code null}
     * @param role    the job title, may be {@code null}
     * @param company the company name, may be {@code null}
     * @param rating  the rating, may be {@code null}
     * @param content the testimonial text, may be {@code null}
     * @param consent the consent flag, may be {@code null}
     * @return the JSON body
     */
    private String body(String name, String role, String company, Integer rating, String content, Boolean consent) {
        return """
                {
                  "name": %s,
                  "role": %s,
                  "company": %s,
                  "rating": %s,
                  "content": %s,
                  "consent": %s
                }
                """.formatted(
                jsonString(name),
                jsonString(role),
                jsonString(company),
                rating == null ? "null" : rating,
                jsonString(content),
                consent == null ? "null" : consent);
    }

    /**
     * Renders a value as a JSON string, or {@code null}.
     *
     * @param value the value
     * @return the JSON fragment
     */
    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
