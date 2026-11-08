package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loveproof.testimonials.domain.TestimonialStatus;
import com.loveproof.testimonials.repository.TestimonialRepository;
import com.loveproof.testimonials.service.TestimonialService;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the public testimonial API: submission, validation boundaries and the wall read.
 *
 * <p>Exercises the real stack against a real PostgreSQL instance, so the database constraints back
 * up the API-level validation.
 */
@AutoConfigureMockMvc
class TestimonialApiTest extends PostgresIntegrationTest {

    private static final String VALID_CONTENT =
            "The onboarding was genuinely smooth and the team answered every question we had.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestimonialService testimonialService;

    @Autowired
    private TestimonialRepository testimonialRepository;

    @BeforeEach
    void clearTestimonials() {
        testimonialRepository.deleteAll();
    }

    @Test
    void validSubmissionIsCreatedAsPending() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", "Engineer", "Acme", 5, VALID_CONTENT, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.role").value("Engineer"))
                .andExpect(jsonPath("$.company").value("Acme"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());
    }

    @Test
    void submissionWithoutRoleAndCompanyStoresNull() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 4, VALID_CONTENT, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.company").doesNotExist());
    }

    @Test
    void emptyRoleAndCompanyAreStoredAsNull() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", "   ", "  ", 4, VALID_CONTENT, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.company").doesNotExist());
    }

    @Test
    void nameShorterThanTwoCharactersIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("J", null, null, 4, VALID_CONTENT, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/testimonials"));
    }

    @Test
    void nameLongerThanOneHundredCharactersIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("n".repeat(101), null, null, 4, VALID_CONTENT, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
    }

    @Test
    void nameOfExactlyTwoCharactersIsAccepted() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jo", null, null, 4, VALID_CONTENT, true)))
                .andExpect(status().isCreated());
    }

    @Test
    void nameOfExactlyOneHundredCharactersIsAccepted() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("n".repeat(100), null, null, 4, VALID_CONTENT, true)))
                .andExpect(status().isCreated());
    }

    @Test
    void whitespaceOnlyNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ", null, null, 4, VALID_CONTENT, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
    }

    @Test
    void ratingOfZeroIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 0, VALID_CONTENT, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").isNotEmpty());
    }

    @Test
    void ratingOfSixIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 6, VALID_CONTENT, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").isNotEmpty());
    }

    @Test
    void ratingOfOneIsAccepted() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 1, VALID_CONTENT, true)))
                .andExpect(status().isCreated());
    }

    @Test
    void ratingOfFiveIsAccepted() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 5, VALID_CONTENT, true)))
                .andExpect(status().isCreated());
    }

    @Test
    void missingRatingIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, null, VALID_CONTENT, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").isNotEmpty());
    }

    @Test
    void contentShorterThanTwentyCharactersIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 4, "Too short.", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").isNotEmpty());
    }

    @Test
    void contentOfExactlyTwentyCharactersIsAccepted() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 4, "c".repeat(20), true)))
                .andExpect(status().isCreated());
    }

    @Test
    void contentLongerThanTwoThousandCharactersIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 4, "c".repeat(2001), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").isNotEmpty());
    }

    @Test
    void consentFalseIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 4, VALID_CONTENT, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.consent").isNotEmpty());
    }

    @Test
    void missingConsentIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 4, VALID_CONTENT, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.consent").isNotEmpty());
    }

    @Test
    void roleLongerThanOneHundredAndTwentyCharactersIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", "r".repeat(121), null, 4, VALID_CONTENT, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role").isNotEmpty());
    }

    @Test
    void companyLongerThanOneHundredAndTwentyCharactersIsRejected() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, "c".repeat(121), 4, VALID_CONTENT, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.company").isNotEmpty());
    }

    @Test
    void malformedBodyIsRejectedWithTheErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.path").value("/api/testimonials"));
    }

    @Test
    void wallReturnsOnlyApprovedNonDeletedTestimonials() throws Exception {
        testimonialService.create("Approved Person", null, null, 5, VALID_CONTENT, "1.2.3.4");
        var pending = testimonialService.create("Pending Person", null, null, 3, VALID_CONTENT + " two", "1.2.3.5");
        var rejected = testimonialService.create("Rejected Person", null, null, 2, VALID_CONTENT + " three", "1.2.3.6");
        var deleted = testimonialService.create("Deleted Person", null, null, 1, VALID_CONTENT + " four", "1.2.3.7");

        testimonialService.approve(findId("Approved Person"), null, "system");
        testimonialService.reject(findId("Rejected Person"), null, "system", "Not relevant.");
        testimonialService.softDelete(findId("Deleted Person"), null, "system", null);

        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Approved Person"));
    }

    @Test
    void wallIsEmptyWhenNothingIsApproved() throws Exception {
        mockMvc.perform(get("/api/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void wallSupportsTheRatingFilter() throws Exception {
        testimonialService.create("Five Star", null, null, 5, VALID_CONTENT + " five", "1.2.3.8");
        testimonialService.create("Three Star", null, null, 3, VALID_CONTENT + " six", "1.2.3.9");
        testimonialService.approve(findId("Five Star"), null, "system");
        testimonialService.approve(findId("Three Star"), null, "system");

        mockMvc.perform(get("/api/testimonials").param("rating", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Five Star"));
    }

    @Test
    void wallSupportsSortingByRating() throws Exception {
        testimonialService.create("Three Star", null, null, 3, VALID_CONTENT + " seven", "1.2.3.10");
        testimonialService.create("Five Star", null, null, 5, VALID_CONTENT + " eight", "1.2.3.11");
        testimonialService.approve(findId("Three Star"), null, "system");
        testimonialService.approve(findId("Five Star"), null, "system");

        mockMvc.perform(get("/api/testimonials").param("sort", "rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Five Star"));
    }

    @Test
    void wallPaginatesAndReportsATotal() throws Exception {
        for (int i = 0; i < 5; i++) {
            testimonialService.create("Person " + i, null, null, 4, VALID_CONTENT + " item " + i, "10.0.0." + i);
            testimonialService.approve(findId("Person " + i), null, "system");
        }

        mockMvc.perform(get("/api/testimonials").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void nonNumericRatingFilterIsRejected() throws Exception {
        mockMvc.perform(get("/api/testimonials").param("rating", "high"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void publicStatsCountOnlyApprovedTestimonials() throws Exception {
        testimonialService.create("Approved Person", null, null, 5, VALID_CONTENT, "1.2.3.12");
        testimonialService.create("Pending Person", null, null, 3, VALID_CONTENT + " nine", "1.2.3.13");
        testimonialService.approve(findId("Approved Person"), null, "system");

        mockMvc.perform(get("/api/testimonials/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0));
    }

    @Test
    void duplicateSubmissionIsRejectedWithConflict() throws Exception {
        String content = "A testimonial that will be submitted twice in quick succession.";

        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 5, content, true)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 5, content, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SUBMISSION"));
    }

    @Test
    void submissionNeverEchoesTheIpHashOrFingerprint() throws Exception {
        String response = mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane Doe", null, null, 5, VALID_CONTENT, true)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("submitterIpHash");
        assertThat(response).doesNotContain("contentFingerprint");
    }

    @Test
    void anUnknownEndpointReturnsTheErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/does-not-exist"));
    }

    /**
     * Finds the identifier of the testimonial with the given name.
     *
     * @param name the name
     * @return the identifier
     */
    private java.util.UUID findId(String name) {
        return testimonialRepository.findAll().stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    /**
     * Builds a request body.
     *
     * @param name    the name
     * @param role    the role, may be {@code null}
     * @param company the company, may be {@code null}
     * @param rating  the rating, may be {@code null}
     * @param content the testimonial text
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
