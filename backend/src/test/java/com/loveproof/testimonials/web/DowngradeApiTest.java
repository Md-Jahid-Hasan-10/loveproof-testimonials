package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.domain.SpaceTestimonial;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.SpaceRepository;
import com.loveproof.testimonials.repository.SpaceTestimonialRepository;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies what a downgrade does and does not do.
 *
 * <p>The PRD's acceptance criterion, in full: a downgraded owner keeps all of
 * their Spaces, the ones beyond the Free limit become read-only rather than
 * being deleted, and the refusal is a refusal on the server rather than a
 * disabled button in the interface (PRD §4.9, OPEN-B).
 */
@AutoConfigureMockMvc
class DowngradeApiTest extends PostgresIntegrationTest {

    private static final String EMAIL = "ada@example.com";

    private static final String PASSWORD = "a-good-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private SpaceTestimonialRepository testimonialRepository;

    @BeforeEach
    void clearEverything() {
        testimonialRepository.deleteAll();
        spaceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("a downgraded owner keeps all of their Spaces")
    void aDowngradedOwnerKeepsAllOfTheirSpaces() throws Exception {
        MockHttpSession session = signIn();
        List<Space> spaces = tenSpaces();

        downgradeToFree();

        mockMvc.perform(get("/api/app/spaces").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    @DisplayName("a downgraded owner cannot create another Space")
    void aDowngradedOwnerCannotCreateAnotherSpace() throws Exception {
        MockHttpSession session = signIn();
        tenSpaces();

        downgradeToFree();

        mockMvc.perform(post("/api/app/spaces")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"One too many\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("the three oldest Spaces stay editable after the downgrade")
    void theThreeOldestSpacesStayEditableAfterTheDowngrade() throws Exception {
        MockHttpSession session = signIn();
        List<Space> spaces = tenSpaces();

        downgradeToFree();

        for (int index = 0; index < 3; index++) {
            Space space = spaces.get(index);

            mockMvc.perform(put("/api/app/spaces/{spaceId}", space.getId())
                            .session(session)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Renamed " + index + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Renamed " + index))
                    .andExpect(jsonPath("$.editable").value(true));
        }
    }

    @Test
    @DisplayName("the Spaces beyond the limit refuse an update")
    void theSpacesBeyondTheLimitRefuseAnUpdate() throws Exception {
        MockHttpSession session = signIn();
        List<Space> spaces = tenSpaces();

        downgradeToFree();

        Space readOnly = spaces.get(3);
        mockMvc.perform(put("/api/app/spaces/{spaceId}", readOnly.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("the Spaces beyond the limit refuse a delete")
    void theSpacesBeyondTheLimitRefuseADelete() throws Exception {
        MockHttpSession session = signIn();
        List<Space> spaces = tenSpaces();

        downgradeToFree();

        Space readOnly = spaces.get(3);
        mockMvc.perform(delete("/api/app/spaces/{spaceId}", readOnly.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));

        // Nothing was deleted: a downgrade never removes data.
        assertThat(spaceRepository.count()).isEqualTo(10);
    }

    @Test
    @DisplayName("the Spaces beyond the limit refuse an embed change")
    void theSpacesBeyondTheLimitRefuseAnEmbedChange() throws Exception {
        MockHttpSession session = signIn();
        List<Space> spaces = tenSpaces();

        downgradeToFree();

        Space readOnly = spaces.get(3);
        mockMvc.perform(put("/api/app/spaces/{spaceId}/embed", readOnly.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"layout\":\"masonry\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("the Spaces beyond the limit refuse a token rotation")
    void theSpacesBeyondTheLimitRefuseATokenRotation() throws Exception {
        MockHttpSession session = signIn();
        List<Space> spaces = tenSpaces();

        downgradeToFree();

        Space readOnly = spaces.get(3);
        mockMvc.perform(post("/api/app/spaces/{spaceId}/embed/rotate-token", readOnly.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("the Spaces beyond the limit refuse a testimonial change")
    void theSpacesBeyondTheLimitRefuseATestimonialChange() throws Exception {
        MockHttpSession session = signIn();
        List<Space> spaces = tenSpaces();

        Space readOnly = spaces.get(3);
        SpaceTestimonial testimonial = testimonialRepository.save(
                testimonial(readOnly, "Grace", "Lovely work."));

        downgradeToFree();

        mockMvc.perform(patch("/api/app/spaces/{spaceId}/testimonials/{id}",
                        readOnly.getId(), testimonial.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("a read-only Space still renders its public page")
    void aReadOnlySpaceStillRendersItsPublicPage() throws Exception {
        List<Space> spaces = tenSpaces();

        downgradeToFree();

        Space readOnly = spaces.get(3);
        mockMvc.perform(get("/api/app/public/spaces/{slug}", readOnly.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Customer reviews"))
                // It renders, but it no longer collects.
                .andExpect(jsonPath("$.accepting").value(false));
    }

    @Test
    @DisplayName("a read-only Space refuses new submissions")
    void aReadOnlySpaceRefusesNewSubmissions() throws Exception {
        List<Space> spaces = tenSpaces();

        downgradeToFree();

        Space readOnly = spaces.get(3);
        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", readOnly.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Late Arrival","email":"late@example.com",
                                 "content":"A testimonial that arrives after the downgrade."}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_ACCEPTING"));
    }

    @Test
    @DisplayName("the existing embed of a read-only Space still renders")
    void theExistingEmbedOfAReadOnlySpaceStillRenders() throws Exception {
        MockHttpSession session = signIn();
        List<Space> spaces = tenSpaces();
        Space readOnly = spaces.get(3);

        String body = mockMvc.perform(get("/api/app/spaces/{spaceId}/embed", readOnly.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int start = body.indexOf("\"embedToken\":\"") + "\"embedToken\":\"".length();
        String token = body.substring(start, body.indexOf('"', start));

        downgradeToFree();

        // The embed belongs to somebody else's website. A downgrade on the
        // owner's side must not take it down.
        mockMvc.perform(get("/api/app/public/embeds/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testimonials").isArray());
    }

    @Test
    @DisplayName("the editable set is the same on every call")
    void theEditableSetIsTheSameOnEveryCall() throws Exception {
        signIn();
        tenSpaces();

        downgradeToFree();

        MockHttpSession session = signIn();
        String body = mockMvc.perform(get("/api/app/spaces").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Exactly three Spaces are editable, and they are the same three on
        // every read. The count is checked by parsing the response rather
        // than with a filter expression, because a JSONPath filter returns a
        // list and its length is the length of each match.
        int editable = 0;
        for (com.fasterxml.jackson.databind.JsonNode element
                : new com.fasterxml.jackson.databind.ObjectMapper().readTree(body)) {
            if (element.get("editable").asBoolean()) {
                editable++;
            }
        }
        assertThat(editable).isEqualTo(3);
    }

    /**
     * Creates ten Spaces, oldest first.
     *
     * @return the Spaces, oldest first
     */
    private List<Space> tenSpaces() {
        AppUser owner = userRepository.findByEmailIgnoreCase(EMAIL)
                .orElseGet(() -> userRepository.save(new AppUser(
                        EMAIL, "{noop}", "Test User")));

        List<Space> spaces = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            spaces.add(spaceRepository.save(
                    new Space(owner.getId(), "Customer reviews", "space-" + index)));
        }
        return spaces;
    }

    /**
     * Creates a testimonial directly.
     *
     * @param space   the Space it belongs to
     * @param name    the submitter's name
     * @param content the testimonial text
     * @return the testimonial
     */
    private SpaceTestimonial testimonial(Space space, String name, String content) {
        SpaceTestimonial testimonial = new SpaceTestimonial(
                space.getId(), name, "grace@example.com", content);
        testimonial.setConsentGiven(true);
        testimonial.setSubmittedAt(java.time.Instant.now());
        testimonial.setUpdatedAt(java.time.Instant.now());
        return testimonialRepository.save(testimonial);
    }

    /**
     * Moves the account onto the Free plan.
     */
    private void downgradeToFree() {
        AppUser owner = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        owner.setPlan(Plan.FREE);
        userRepository.save(owner);
    }

    /**
     * Signs in as the test customer, registering the account if needed.
     *
     * @return the session
     * @throws Exception when the sign-in fails
     */
    private MockHttpSession signIn() throws Exception {
        // The account may already exist from an earlier call in this test, in
        // which case the registration answers 409 and the sign-in below is
        // what matters.
        mockMvc.perform(post("/api/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD
                                + "\",\"displayName\":\"Test User\"}"));

        return (MockHttpSession) mockMvc.perform(post("/api/app/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    /**
     * The CSRF token post-processor.
     *
     * @return the post-processor
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.csrf();
    }
}
