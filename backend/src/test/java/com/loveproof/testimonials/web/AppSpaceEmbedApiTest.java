package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import com.loveproof.testimonials.repository.SpaceEmbedConfigRepository;
import com.loveproof.testimonials.repository.SpaceRepository;
import com.loveproof.testimonials.repository.SpaceTestimonialRepository;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * The embed: the owner's configuration and the public data it renders.
 *
 * <p>Two rules are checked here more than anything else: the embed shows
 * only the Wall of Love entries that are visible, and it never carries an
 * email address out to the host page.
 */
@AutoConfigureMockMvc
class AppSpaceEmbedApiTest extends PostgresIntegrationTest {

    private static final String EMAIL = "ada@example.com";

    private static final String OTHER_EMAIL = "bob@example.com";

    private static final String PASSWORD = "a-good-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private SpaceTestimonialRepository testimonialRepository;

    @Autowired
    private SpaceEmbedConfigRepository configRepository;

    @Autowired
    private com.loveproof.testimonials.service.EmbedService embedService;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Space space;

    @BeforeEach
    void setUp() {
        testimonialRepository.deleteAll();
        configRepository.deleteAll();
        spaceRepository.deleteAll();
        userRepository.deleteAll();
        space = space(EMAIL, "customer-reviews");
    }

    @Test
    @DisplayName("the embed configuration refuses an anonymous caller")
    void theEmbedConfigurationRefusesAnAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/app/spaces/{spaceId}/embed", space.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the embed configuration is created on the first read")
    void theEmbedConfigurationIsCreatedOnTheFirstRead() throws Exception {
        MockHttpSession session = signIn(EMAIL);

        mockMvc.perform(get("/api/app/spaces/{spaceId}/embed", space.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceId").value(space.getId().toString()))
                .andExpect(jsonPath("$.layout").value("masonry"))
                .andExpect(jsonPath("$.themeMode").value("light"))
                .andExpect(jsonPath("$.animation").value(false))
                .andExpect(jsonPath("$.embedToken").isNotEmpty())
                .andExpect(jsonPath("$.scriptUrl").value(org.hamcrest.Matchers.endsWith("/embed/loveproof.js")))
                .andExpect(jsonPath("$.dataUrl").value(
                        org.hamcrest.Matchers.endsWith("/api/app/public/embeds/" + embedTokenOf(space))));
    }

    @Test
    @DisplayName("the layout is saved")
    void theLayoutIsSaved() throws Exception {
        MockHttpSession session = signIn(EMAIL);

        mockMvc.perform(put("/api/app/spaces/{spaceId}/embed", space.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"layout\":\"masonry\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layout").value("masonry"));
    }

    @Test
    @DisplayName("the theme mode is saved")
    void theThemeModeIsSaved() throws Exception {
        MockHttpSession session = signIn(EMAIL);

        mockMvc.perform(put("/api/app/spaces/{spaceId}/embed", space.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"themeMode\":\"dark\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeMode").value("dark"));
    }

    @Test
    @DisplayName("the carousel needs a Premium plan")
    void theCarouselNeedsAPremiumPlan() throws Exception {
        MockHttpSession session = signIn(EMAIL);

        mockMvc.perform(put("/api/app/spaces/{spaceId}/embed", space.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"layout\":\"carousel\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("a background colour needs a Premium plan")
    void aBackgroundColourNeedsAPremiumPlan() throws Exception {
        MockHttpSession session = signIn(EMAIL);

        mockMvc.perform(put("/api/app/spaces/{spaceId}/embed", space.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backgroundColor\":\"#ffffff\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("the animation needs a Premium plan")
    void theAnimationNeedsAPremiumPlan() throws Exception {
        MockHttpSession session = signIn(EMAIL);

        mockMvc.perform(put("/api/app/spaces/{spaceId}/embed", space.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animation\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a malformed background colour is refused")
    void aMalformedBackgroundColourIsRefused() throws Exception {
        MockHttpSession session = signIn(EMAIL);

        mockMvc.perform(put("/api/app/spaces/{spaceId}/embed", space.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backgroundColor\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("another customer's Space is answered as not found")
    void anotherCustomersSpaceIsAnsweredAsNotFound() throws Exception {
        MockHttpSession session = signIn(OTHER_EMAIL);

        mockMvc.perform(get("/api/app/spaces/{spaceId}/embed", space.getId())
                        .session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the public data needs no session")
    void thePublicDataNeedsNoSession() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        String token = embedTokenOf(space);

        mockMvc.perform(get("/api/app/public/embeds/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layout").value("masonry"))
                .andExpect(jsonPath("$.testimonials").isArray());
    }

    @Test
    @DisplayName("an unknown token is answered as not found")
    void anUnknownTokenIsAnsweredAsNotFound() throws Exception {
        mockMvc.perform(get("/api/app/public/embeds/{token}", "nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the embed shows only the Wall of Love entries that are visible")
    void theEmbedShowsOnlyTheWallOfLoveEntriesThatAreVisible() throws Exception {
        MockHttpSession session = signIn(EMAIL);

        SpaceTestimonial onTheWall = testimonial(space, "Grace", "grace@example.com", "Lovely.");
        onTheWall.setWallOfLove(true);
        testimonialRepository.save(onTheWall);

        SpaceTestimonial hidden = testimonial(space, "Alan", "alan@example.com", "Fine.");
        hidden.setWallOfLove(true);
        hidden.setVisible(false);
        testimonialRepository.save(hidden);

        testimonial(space, "Katherine", "katherine@example.com", "Ordinary.");

        mockMvc.perform(get("/api/app/public/embeds/{token}", embedTokenOf(space)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testimonials.length()").value(1))
                .andExpect(jsonPath("$.testimonials[0].name").value("Grace"));
    }

    @Test
    @DisplayName("the embed never carries an email address")
    void theEmbedNeverCarriesAnEmailAddress() throws Exception {
        MockHttpSession session = signIn(EMAIL);

        SpaceTestimonial onTheWall = testimonial(space, "Grace", "grace@example.com", "Lovely.");
        onTheWall.setWallOfLove(true);
        testimonialRepository.save(onTheWall);

        String body = mockMvc.perform(get("/api/app/public/embeds/{token}", embedTokenOf(space)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("grace@example.com");
        assertThat(body).doesNotContain("email");
    }

    @Test
    @DisplayName("the embed carries only the profile fields the Space collects")
    void theEmbedCarriesOnlyTheProfileFieldsTheSpaceCollects() throws Exception {
        MockHttpSession session = signIn(EMAIL);

        SpaceTestimonial onTheWall = testimonial(space, "Grace", "grace@example.com", "Lovely.");
        onTheWall.setWallOfLove(true);
        onTheWall.setCompany("Acme");
        onTheWall.setJobTitle("Engineer");
        onTheWall.setWebsite("https://acme.example");
        onTheWall.setSocialLink("https://social.example/grace");
        testimonialRepository.save(onTheWall);

        // The Space collects none of the optional fields, so none of them
        // travel to the host page.
        mockMvc.perform(get("/api/app/public/embeds/{token}", embedTokenOf(space)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testimonials[0].company").doesNotExist())
                .andExpect(jsonPath("$.testimonials[0].jobTitle").doesNotExist())
                .andExpect(jsonPath("$.testimonials[0].website").doesNotExist())
                .andExpect(jsonPath("$.testimonials[0].socialLink").doesNotExist());

        // With the fields on, they travel.
        space.setFieldCompany(true);
        space.setFieldJobTitle(true);
        space.setFieldWebsite(true);
        space.setFieldSocial(true);
        spaceRepository.save(space);

        mockMvc.perform(get("/api/app/public/embeds/{token}", embedTokenOf(space)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testimonials[0].company").value("Acme"))
                .andExpect(jsonPath("$.testimonials[0].jobTitle").value("Engineer"))
                .andExpect(jsonPath("$.testimonials[0].website").value("https://acme.example"))
                .andExpect(jsonPath("$.testimonials[0].socialLink")
                        .value("https://social.example/grace"));
    }

    @Test
    @DisplayName("the token can be rotated")
    void theTokenCanBeRotated() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        String before = embedTokenOf(space);

        mockMvc.perform(post("/api/app/spaces/{spaceId}/embed/rotate-token", space.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedToken").value(
                        org.hamcrest.Matchers.not(before)));

        // The old token no longer works, and the new one does.
        mockMvc.perform(get("/api/app/public/embeds/{token}", before))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/app/public/embeds/{token}", embedTokenOf(space)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the embed script is served as JavaScript")
    void theEmbedScriptIsServedAsJavaScript() throws Exception {
        mockMvc.perform(get("/embed/loveproof.js"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith("application/javascript"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(
                                org.hamcrest.Matchers.containsString("textContent")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(
                                org.hamcrest.Matchers.not(
                                        org.hamcrest.Matchers.containsString("innerHTML"))));
    }

    /**
     * The embed token of a Space.
     *
     * <p>The configuration is created the first time it is read, so the
     * helper creates it too rather than assuming it is already there.
     *
     * @param space the Space
     * @return the token
     */
    private String embedTokenOf(Space space) {
        return embedService.getOrCreate(space.getOwnerId(), space.getId()).getEmbedToken();
    }

    /**
     * Creates a customer with a Space.
     *
     * @param email the owner's email address
     * @param slug  the Space's slug
     * @return the Space
     */
    private Space space(String email, String slug) {
        AppUser owner = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new AppUser(
                        email, passwordEncoder.encode(PASSWORD), "Test User")));
        return spaceRepository.save(new Space(owner.getId(), "Customer reviews", slug));
    }

    /**
     * Adds a testimonial to a Space.
     *
     * @param space  the Space
     * @param name   the submitter's name
     * @param email  the submitter's email
     * @param text   the testimonial text
     * @return the testimonial
     */
    private SpaceTestimonial testimonial(Space space, String name, String email, String text) {
        return testimonialRepository.save(new SpaceTestimonial(space.getId(), name, email, text));
    }

    /**
     * Signs in as a given customer, registering the account if needed.
     *
     * @param email the email address
     * @return the session
     * @throws Exception when the sign-in fails
     */
    private MockHttpSession signIn(String email) throws Exception {
        mockMvc.perform(post("/api/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\",\"displayName\":\"Test User\"}"));

        return (MockHttpSession) mockMvc.perform(post("/api/app/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
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
