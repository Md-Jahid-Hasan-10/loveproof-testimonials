package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.domain.SpaceTestimonial;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.SpaceRepository;
import com.loveproof.testimonials.repository.SpaceTestimonialRepository;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the plan limits, enforced on the server.
 *
 * <p>The limits have to be enforced here rather than in the interface: a
 * rule the browser honours is a rule one request away from being bypassed
 * (PRD §4.9).
 */
@AutoConfigureMockMvc
class PlanLimitApiTest extends PostgresIntegrationTest {

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
    @DisplayName("a Free account cannot create a fourth Space")
    void aFreeAccountCannotCreateAFourthSpace() throws Exception {
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
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("a Free account cannot choose the carousel")
    void aFreeAccountCannotChooseTheCarousel() throws Exception {
        MockHttpSession session = signIn();
        String spaceId = createSpace(session, "Reviews", "reviews");

        mockMvc.perform(put("/api/app/spaces/{spaceId}/embed", spaceId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"layout\":\"carousel\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Premium")));
    }

    @Test
    @DisplayName("a Free account cannot choose a background colour")
    void aFreeAccountCannotChooseABackgroundColour() throws Exception {
        MockHttpSession session = signIn();
        String spaceId = createSpace(session, "Reviews", "reviews");

        mockMvc.perform(put("/api/app/spaces/{spaceId}/embed", spaceId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backgroundColor\":\"#ffffff\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Premium")));
    }

    @Test
    @DisplayName("a Free account cannot choose the animation")
    void aFreeAccountCannotChooseTheAnimation() throws Exception {
        MockHttpSession session = signIn();
        String spaceId = createSpace(session, "Reviews", "reviews");

        mockMvc.perform(put("/api/app/spaces/{spaceId}/embed", spaceId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animation\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Premium")));
    }

    @Test
    @DisplayName("a Premium account may choose all of them")
    void aPremiumAccountMayChooseAllOfThem() throws Exception {
        MockHttpSession session = signIn();
        String spaceId = createSpace(session, "Reviews", "reviews");
        upgradeToPremium(EMAIL);

        mockMvc.perform(put("/api/app/spaces/{spaceId}/embed", spaceId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"layout\":\"carousel\",\"backgroundColor\":\"#ffffff\","
                                + "\"animation\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layout").value("carousel"))
                .andExpect(jsonPath("$.backgroundColor").value("#ffffff"))
                .andExpect(jsonPath("$.animation").value(true));
    }

    @Test
    @DisplayName("a Free Space stops accepting once it holds a hundred testimonials")
    void aFreeSpaceStopsAcceptingOnceItHoldsAHundredTestimonials() throws Exception {
        MockHttpSession session = signIn();
        Space space = space("reviews");

        // The Free plan allows a hundred testimonials per Space. Filling it
        // directly is quicker than submitting a hundred times, and the limit
        // is about the count, not about how the count got there.
        for (int index = 0; index < 100; index++) {
            testimonialRepository.save(testimonial(space, "Grace " + index));
        }

        mockMvc.perform(get("/api/app/public/spaces/{slug}", space.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepting").value(false));
    }

    @Test
    @DisplayName("the hundred-and-first submission into a full Space is refused")
    void theHundredAndFirstSubmissionIntoAFullSpaceIsRefused() throws Exception {
        MockHttpSession session = signIn();
        Space space = space("reviews");

        for (int index = 0; index < 100; index++) {
            testimonialRepository.save(testimonial(space, "Grace " + index));
        }

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Late Arrival","email":"late@example.com",
                                 "content":"A testimonial that arrives after the Space is full."}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_ACCEPTING"));
    }

    @Test
    @DisplayName("the refusal does not say why the Space is full")
    void theRefusalDoesNotSayWhyTheSpaceIsFull() throws Exception {
        MockHttpSession session = signIn();
        Space space = space("reviews");

        for (int index = 0; index < 100; index++) {
            testimonialRepository.save(testimonial(space, "Grace " + index));
        }

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Late Arrival","email":"late@example.com",
                                 "content":"A testimonial that arrives after the Space is full."}
                                """))
                .andExpect(status().isConflict())
                // The submitter learns nothing about the owner's plan: the
                // message is the same one a closed Space gives.
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsStringIgnoringCase("plan"))));
    }

    @Test
    @DisplayName("a Premium Space keeps accepting past a hundred testimonials")
    void aPremiumSpaceKeepsAcceptingPastAHundredTestimonials() throws Exception {
        MockHttpSession session = signIn();
        Space space = space("reviews");
        upgradeToPremium(EMAIL);

        for (int index = 0; index < 100; index++) {
            testimonialRepository.save(testimonial(space, "Grace " + index));
        }

        mockMvc.perform(get("/api/app/public/spaces/{slug}", space.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepting").value(true));
    }

    /**
     * Signs in as the test customer, registering the account if needed.
     *
     * @return the session
     * @throws Exception when the sign-in fails
     */
    private MockHttpSession signIn() throws Exception {
        try {
            mockMvc.perform(post("/api/app/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD
                                    + "\",\"displayName\":\"Test User\"}"))
                    .andExpect(status().isCreated());
        } catch (Exception ignored) {
            // The account already exists from an earlier call in this test.
        }

        return (MockHttpSession) mockMvc.perform(post("/api/app/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
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

        int start = body.indexOf("\"id\":\"") + "\"id\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    /**
     * Creates a Space directly, without going through the API.
     *
     * @param slug the Space's slug
     * @return the Space
     */
    private Space space(String slug) {
        AppUser owner = userRepository.findByEmailIgnoreCase(EMAIL)
                .orElseGet(() -> userRepository.save(new AppUser(
                        EMAIL, "{noop}", "Test User")));
        return spaceRepository.save(new Space(owner.getId(), "Customer reviews", slug));
    }

    /**
     * Creates a testimonial directly.
     *
     * @param space the Space it belongs to
     * @param name  the submitter's name
     * @return the testimonial
     */
    private SpaceTestimonial testimonial(Space space, String name) {
        SpaceTestimonial testimonial = new SpaceTestimonial(
                space.getId(), name, "grace@example.com", "Lovely work, thank you.");
        testimonial.setConsentGiven(true);
        testimonial.setSubmittedAt(java.time.Instant.now());
        testimonial.setUpdatedAt(java.time.Instant.now());
        return testimonialRepository.save(testimonial);
    }

    /**
     * Moves an account onto the Premium plan.
     *
     * @param email the account's email address
     */
    private void upgradeToPremium(String email) {
        AppUser owner = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        owner.setPlan(com.loveproof.testimonials.domain.Plan.PREMIUM);
        userRepository.save(owner);
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
