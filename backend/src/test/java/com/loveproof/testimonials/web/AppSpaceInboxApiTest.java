package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.loveproof.testimonials.repository.SpaceRepository;
import com.loveproof.testimonials.repository.SpaceTestimonialRepository;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the owner's inbox for one Space.
 *
 * <p>These tests drive the real filter chain, so one customer's session must
 * not reach another customer's Space.
 */
@AutoConfigureMockMvc
class AppSpaceInboxApiTest extends PostgresIntegrationTest {

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
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Space space;

    @BeforeEach
    void setUp() {
        testimonialRepository.deleteAll();
        spaceRepository.deleteAll();
        userRepository.deleteAll();
        space = space(EMAIL, "customer-reviews");
    }

    @Test
    @DisplayName("the inbox refuses an anonymous caller")
    void theInboxRefusesAnAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/app/spaces/{spaceId}/testimonials", space.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the inbox lists the Space's testimonials")
    void theInboxListsTheSpacesTestimonials() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        testimonial(space, "Grace", "grace@example.com", "Lovely work.");

        mockMvc.perform(get("/api/app/spaces/{spaceId}/testimonials", space.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Grace"))
                .andExpect(jsonPath("$.content[0].email").value("grace@example.com"))
                .andExpect(jsonPath("$.content[0].visible").value(true))
                .andExpect(jsonPath("$.content[0].wallOfLove").value(false))
                .andExpect(jsonPath("$.content[0].favorite").value(false));
    }

    @Test
    @DisplayName("favourites sort above the rest")
    void favouritesSortAboveTheRest() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        SpaceTestimonial older = testimonial(space, "Older", "older@example.com", "First.");
        SpaceTestimonial newer = testimonial(space, "Newer", "newer@example.com", "Second.");
        newer.setFavorite(true);
        testimonialRepository.save(newer);

        mockMvc.perform(get("/api/app/spaces/{spaceId}/testimonials", space.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Newer"))
                .andExpect(jsonPath("$.content[1].name").value("Older"));
    }

    @Test
    @DisplayName("the inbox searches the name, the email and the text")
    void theInboxSearchesTheNameTheEmailAndTheText() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        testimonial(space, "Grace", "grace@example.com", "Lovely work.");
        testimonial(space, "Alan", "alan@example.com", "Something elsewhere entirely.");

        mockMvc.perform(get("/api/app/spaces/{spaceId}/testimonials", space.getId())
                        .session(session)
                        .param("search", "grace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Grace"));

        mockMvc.perform(get("/api/app/spaces/{spaceId}/testimonials", space.getId())
                        .session(session)
                        .param("search", "elsewhere"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Alan"));
    }

    @Test
    @DisplayName("the inbox filters by favourite and by Wall of Love")
    void theInboxFiltersByFavouriteAndByWallOfLove() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        SpaceTestimonial favourite = testimonial(space, "Grace", "grace@example.com", "Lovely.");
        favourite.setFavorite(true);
        favourite.setWallOfLove(true);
        testimonialRepository.save(favourite);
        testimonial(space, "Alan", "alan@example.com", "Fine.");

        mockMvc.perform(get("/api/app/spaces/{spaceId}/testimonials", space.getId())
                        .session(session)
                        .param("favorite", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Grace"));

        mockMvc.perform(get("/api/app/spaces/{spaceId}/testimonials", space.getId())
                        .session(session)
                        .param("wallOfLove", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("the inbox filters by hidden")
    void theInboxFiltersByHidden() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        SpaceTestimonial hidden = testimonial(space, "Grace", "grace@example.com", "Lovely.");
        hidden.setVisible(false);
        testimonialRepository.save(hidden);
        testimonial(space, "Alan", "alan@example.com", "Fine.");

        mockMvc.perform(get("/api/app/spaces/{spaceId}/testimonials", space.getId())
                        .session(session)
                        .param("hidden", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Grace"));
    }

    @Test
    @DisplayName("a hidden testimonial stays in the inbox")
    void aHiddenTestimonialStaysInTheInbox() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        SpaceTestimonial hidden = testimonial(space, "Grace", "grace@example.com", "Lovely.");
        hidden.setVisible(false);
        testimonialRepository.save(hidden);

        mockMvc.perform(get("/api/app/spaces/{spaceId}/testimonials", space.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].visible").value(false));
    }

    @Test
    @DisplayName("favouriting a testimonial works")
    void favouritingATestimonialWorks() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        SpaceTestimonial testimonial = testimonial(space, "Grace", "grace@example.com", "Lovely.");

        mockMvc.perform(patch("/api/app/spaces/{spaceId}/testimonials/{id}",
                        space.getId(), testimonial.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));
    }

    @Test
    @DisplayName("adding a testimonial to the Wall of Love works")
    void addingATestimonialToTheWallOfLoveWorks() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        SpaceTestimonial testimonial = testimonial(space, "Grace", "grace@example.com", "Lovely.");

        mockMvc.perform(patch("/api/app/spaces/{spaceId}/testimonials/{id}",
                        space.getId(), testimonial.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wallOfLove\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallOfLove").value(true));
    }

    @Test
    @DisplayName("a curation request leaves the flags it does not mention alone")
    void aCurationRequestLeavesTheFlagsItDoesNotMentionAlone() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        SpaceTestimonial testimonial = testimonial(space, "Grace", "grace@example.com", "Lovely.");
        testimonial.setWallOfLove(true);
        testimonialRepository.save(testimonial);

        mockMvc.perform(patch("/api/app/spaces/{spaceId}/testimonials/{id}",
                        space.getId(), testimonial.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true))
                .andExpect(jsonPath("$.wallOfLove").value(true));
    }

    @Test
    @DisplayName("hiding a testimonial works")
    void hidingATestimonialWorks() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        SpaceTestimonial testimonial = testimonial(space, "Grace", "grace@example.com", "Lovely.");

        mockMvc.perform(patch("/api/app/spaces/{spaceId}/testimonials/{id}",
                        space.getId(), testimonial.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false));
    }

    @Test
    @DisplayName("editing a testimonial updates the text")
    void editingATestimonialUpdatesTheText() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        SpaceTestimonial testimonial = testimonial(space, "Grace", "grace@example.com", "Lovely.");

        mockMvc.perform(put("/api/app/spaces/{spaceId}/testimonials/{id}",
                        space.getId(), testimonial.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Edited by the owner.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Edited by the owner."));
    }

    @Test
    @DisplayName("deleting a testimonial removes it")
    void deletingATestimonialRemovesIt() throws Exception {
        MockHttpSession session = signIn(EMAIL);
        SpaceTestimonial testimonial = testimonial(space, "Grace", "grace@example.com", "Lovely.");

        mockMvc.perform(delete("/api/app/spaces/{spaceId}/testimonials/{id}",
                        space.getId(), testimonial.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(testimonialRepository.count()).isZero();
    }

    @Test
    @DisplayName("another customer's Space is answered as not found")
    void anotherCustomersSpaceIsAnsweredAsNotFound() throws Exception {
        MockHttpSession session = signIn(OTHER_EMAIL);

        mockMvc.perform(get("/api/app/spaces/{spaceId}/testimonials", space.getId())
                        .session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("another customer cannot change a testimonial")
    void anotherCustomerCannotChangeATestimonial() throws Exception {
        MockHttpSession session = signIn(OTHER_EMAIL);
        SpaceTestimonial testimonial = testimonial(space, "Grace", "grace@example.com", "Lovely.");

        mockMvc.perform(patch("/api/app/spaces/{spaceId}/testimonials/{id}",
                        space.getId(), testimonial.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a read-only Space can be read but not changed")
    void aReadOnlySpaceCanBeReadButNotChanged() throws Exception {
        MockHttpSession session = signIn(OTHER_EMAIL);

        // The owner ends up with four Spaces on the Free plan, which allows
        // three. The three oldest stay editable (OPEN-B), so the Space this
        // test uses has to be the newest of the four.
        Space oldest = space(OTHER_EMAIL, "oldest");
        spaceRepository.save(new Space(oldest.getOwnerId(), "Older 2", "older-2"));
        spaceRepository.save(new Space(oldest.getOwnerId(), "Older 3", "older-3"));
        Space readOnly = space(OTHER_EMAIL, "newest");

        SpaceTestimonial testimonial = testimonial(readOnly, "Grace", "grace@example.com", "Lovely.");

        mockMvc.perform(get("/api/app/spaces/{spaceId}/testimonials", readOnly.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(patch("/api/app/spaces/{spaceId}/testimonials/{id}",
                        readOnly.getId(), testimonial.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}"))
                .andExpect(status().isConflict());
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
        // The account may already exist: `setUp` creates the owner with the
        // same password, so registering again is refused with a conflict.
        // Either answer is fine -- the sign-in below is what the test needs.
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
