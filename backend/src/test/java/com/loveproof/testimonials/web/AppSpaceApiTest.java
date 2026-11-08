package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.SpaceRepository;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the Spaces endpoints.
 *
 * <p>These tests drive the real filter chain, so the session scoping is
 * tested as a browser would meet it: one customer's session must not reach
 * another customer's Space.
 */
@AutoConfigureMockMvc
class AppSpaceApiTest extends PostgresIntegrationTest {

    private static final String EMAIL = "ada@example.com";

    private static final String PASSWORD = "a-good-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @BeforeEach
    void clearTheSpaces() {
        spaceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("the Spaces endpoints refuse an anonymous caller")
    void theSpacesEndpointsRefuseAnAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/app/spaces"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("creating a Space answers with its public URL")
    void creatingASpaceAnswersWithItsPublicUrl() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/app/spaces")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ada's Reviews\",\"subtitle\":\"What people say\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Ada's Reviews"))
                .andExpect(jsonPath("$.slug").value("ada-s-reviews"))
                .andExpect(jsonPath("$.publicUrl").value("http://localhost:5173/s/ada-s-reviews"))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    @DisplayName("creating a Space without a title is rejected")
    void creatingASpaceWithoutATitleIsRejected() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/app/spaces")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest());

        assertThat(spaceRepository.count()).isZero();
    }

    @Test
    @DisplayName("creating a Space with a taken slug is a conflict")
    void creatingASpaceWithATakenSlugIsAConflict() throws Exception {
        MockHttpSession session = signIn();
        createSpace(session, "Reviews", "reviews");

        mockMvc.perform(post("/api/app/spaces")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Other\",\"slug\":\"reviews\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("creating a Space beyond the plan limit is a conflict with a clear code")
    void creatingBeyondThePlanLimitIsAConflict() throws Exception {
        MockHttpSession session = signIn();
        for (int index = 0; index < 3; index++) {
            createSpace(session, "Space " + index, "space-" + index);
        }

        mockMvc.perform(post("/api/app/spaces")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"One too many\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("limit")));
    }

    @Test
    @DisplayName("choosing a premium theme on a Free account is a conflict")
    void choosingAPremiumThemeOnAFreeAccountIsAConflict() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/app/spaces")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Reviews\",\"theme\":\"bold\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Premium")));
    }

    @Test
    @DisplayName("listing returns only the signed-in customer's Spaces")
    void listingReturnsOnlyTheSignedInCustomersSpaces() throws Exception {
        MockHttpSession session = signIn();
        createSpace(session, "Mine", "mine");

        // Another customer with their own Space.
        MockHttpSession other = signInAs("grace@example.com");
        createSpace(other, "Theirs", "theirs");

        mockMvc.perform(get("/api/app/spaces").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("mine"));
    }

    @Test
    @DisplayName("another customer's Space is answered as not found")
    void anotherCustomersSpaceIsAnsweredAsNotFound() throws Exception {
        MockHttpSession session = signIn();
        String id = createSpace(session, "Mine", "mine");

        MockHttpSession other = signInAs("grace@example.com");

        mockMvc.perform(get("/api/app/spaces/{id}", id).session(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(delete("/api/app/spaces/{id}", id).session(other).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("updating a Space changes it")
    void updatingASpaceChangesIt() throws Exception {
        MockHttpSession session = signIn();
        String id = createSpace(session, "Mine", "mine");

        mockMvc.perform(put("/api/app/spaces/{id}", id)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\",\"ratingEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.ratingEnabled").value(true))
                // The fields that were not sent are left alone.
                .andExpect(jsonPath("$.slug").value("mine"));
    }

    @Test
    @DisplayName("deleting a Space removes it")
    void deletingASpaceRemovesIt() throws Exception {
        MockHttpSession session = signIn();
        String id = createSpace(session, "Mine", "mine");

        mockMvc.perform(delete("/api/app/spaces/{id}", id).session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(spaceRepository.count()).isZero();
    }

    @Test
    @DisplayName("a state-changing request without a CSRF token is refused")
    void aStateChangingRequestWithoutACsrfTokenIsRefused() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/app/spaces")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Reviews\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the themes are listed with the premium ones marked")
    void theThemesAreListedWithThePremiumOnesMarked() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/app/spaces/themes").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].key").value("basic"))
                .andExpect(jsonPath("$[0].premium").value(false))
                .andExpect(jsonPath("$[3].premium").value(true));
    }

    /**
     * Signs in as the test customer.
     *
     * @return the session
     * @throws Exception when the sign-in fails
     */
    private MockHttpSession signIn() throws Exception {
        return signInAs(EMAIL);
    }

    /**
     * Signs in as a given customer, registering the account if needed.
     *
     * @param email the email address
     * @return the session
     * @throws Exception when the sign-in fails
     */
    private MockHttpSession signInAs(String email) throws Exception {
        try {
            mockMvc.perform(post("/api/app/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                    + "\",\"displayName\":\"Test User\"}"))
                    .andExpect(status().isCreated());
        } catch (Exception ignored) {
            // The account already exists from an earlier call in this test.
        }

        return (MockHttpSession) mockMvc.perform(post("/api/app/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    /**
     * Creates a Space and returns its identifier.
     *
     * @param session the signed-in customer's session
     * @param title   the title
     * @param slug    the slug
     * @return the identifier
     * @throws Exception when the creation fails
     */
    private String createSpace(MockHttpSession session, String title, String slug) throws Exception {
        String body = mockMvc.perform(post("/api/app/spaces")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The response is a small object; the identifier is the first value.
        int start = body.indexOf("\"id\":\"") + "\"id\":\"".length();
        return body.substring(start, body.indexOf('"', start));
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
