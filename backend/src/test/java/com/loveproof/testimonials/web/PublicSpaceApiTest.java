package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Verifies the public Space surface: the form configuration and the
 * submission.
 *
 * <p>These tests drive the real filter chain, so the anonymity of the public
 * endpoints is tested as a browser would meet it. A visitor who has never
 * signed in must be able to submit.
 */
@AutoConfigureMockMvc
class PublicSpaceApiTest extends PostgresIntegrationTest {

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
    @DisplayName("the public form can be read without signing in")
    void thePublicFormCanBeReadWithoutSigningIn() throws Exception {
        Space space = space("customer-reviews");

        mockMvc.perform(get("/api/app/public/spaces/{slug}", space.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Customer reviews"))
                .andExpect(jsonPath("$.ratingEnabled").value(false))
                .andExpect(jsonPath("$.accepting").value(true));
    }

    @Test
    @DisplayName("the public form of an unknown slug answers 404")
    void thePublicFormOfAnUnknownSlugAnswers404() throws Exception {
        mockMvc.perform(get("/api/app/public/spaces/{slug}", "nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("the public form does not leak the owner's identity")
    void thePublicFormDoesNotLeakTheOwnersIdentity() throws Exception {
        Space space = space("customer-reviews");

        mockMvc.perform(get("/api/app/public/spaces/{slug}", space.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.editable").doesNotExist())
                .andExpect(jsonPath("$.publicUrl").doesNotExist());
    }

    @Test
    @DisplayName("an anonymous visitor can submit a testimonial")
    void anAnonymousVisitorCanSubmitATestimonial() throws Exception {
        Space space = space("customer-reviews");

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"grace@example.com",
                                 "content":"They did a wonderful job for us."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());

        assertThat(testimonialRepository.countBySpaceId(space.getId())).isOne();
    }

    @Test
    @DisplayName("the answer to a submission never carries the email")
    void theAnswerToASubmissionNeverCarriesTheEmail() throws Exception {
        Space space = space("customer-reviews");

        String body = mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"grace@example.com",
                                 "content":"They did a wonderful job for us."}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("grace@example.com");
    }

    @Test
    @DisplayName("the submitted text is stored as inert text")
    void theSubmittedTextIsStoredAsInertText() throws Exception {
        Space space = space("customer-reviews");

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"grace@example.com",
                                 "content":"<script>alert('x')</script> Lovely work"}
                                """))
                .andExpect(status().isCreated());

        SpaceTestimonial saved = testimonialRepository.findAll().getFirst();
        // The text is stored exactly as it was sent, and rendered as text by
        // the interface rather than as markup. The escape hatch is the
        // renderer, not a mangled copy in the database.
        assertThat(saved.getContent()).contains("<script>");
    }

    @Test
    @DisplayName("a submission to an unknown slug answers 404")
    void aSubmissionToAnUnknownSlugAnswers404() throws Exception {
        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", "nothing-here")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"grace@example.com","content":"Hello there"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a submission missing the name is rejected")
    void aSubmissionMissingTheNameIsRejected() throws Exception {
        Space space = space("customer-reviews");

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"grace@example.com","content":"Hello there"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        assertThat(testimonialRepository.count()).isZero();
    }

    @Test
    @DisplayName("a submission with a malformed email is rejected")
    void aSubmissionWithAMalformedEmailIsRejected() throws Exception {
        Space space = space("customer-reviews");

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"not-an-email","content":"Hello there"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("a submission with a rating outside 1 to 5 is rejected")
    void aSubmissionWithARatingOutsideOneToFiveIsRejected() throws Exception {
        Space space = space("customer-reviews");
        space.setRatingEnabled(true);
        spaceRepository.save(space);

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"grace@example.com",
                                 "content":"Hello there","rating":9}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").exists());
    }

    @Test
    @DisplayName("a field the Space does not ask for is not stored")
    void aFieldTheSpaceDoesNotAskForIsNotStored() throws Exception {
        Space space = space("customer-reviews");

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"grace@example.com","content":"Hello there",
                                 "company":"Acme","rating":5}
                                """))
                .andExpect(status().isCreated());

        SpaceTestimonial saved = testimonialRepository.findAll().getFirst();
        assertThat(saved.getCompany()).isNull();
        // The Space has ratings turned off, so no rating is kept either.
        assertThat(saved.getRating()).isNull();
    }

    @Test
    @DisplayName("a field the Space asks for is stored")
    void aFieldTheSpaceAsksForIsStored() throws Exception {
        Space space = space("customer-reviews");
        space.setFieldCompany(true);
        space.setRatingEnabled(true);
        spaceRepository.save(space);

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"grace@example.com","content":"Hello there",
                                 "company":"Acme","rating":5}
                                """))
                .andExpect(status().isCreated());

        SpaceTestimonial saved = testimonialRepository.findAll().getFirst();
        assertThat(saved.getCompany()).isEqualTo("Acme");
        assertThat(saved.getRating()).isEqualTo(5);
    }

    @Test
    @DisplayName("a required field the Space asks for must be answered")
    void aRequiredFieldTheSpaceAsksForMustBeAnswered() throws Exception {
        Space space = space("customer-reviews");
        space.setFieldCompany(true);
        space.setFieldCompanyRequired(true);
        spaceRepository.save(space);

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"grace@example.com","content":"Hello there"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(testimonialRepository.count()).isZero();
    }

    @Test
    @DisplayName("a submission fills the honeypot and is discarded")
    void aSubmissionFillsTheHoneypotAndIsDiscarded() throws Exception {
        Space space = space("customer-reviews");

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bot","email":"bot@example.com","content":"Buy things",
                                 "website2":"http://spam.example.com"}
                                """))
                .andExpect(status().isCreated());

        assertThat(testimonialRepository.count()).isZero();
    }

    @Test
    @DisplayName("the address locks once the Space has received a testimonial")
    void theAddressLocksOnceTheSpaceHasReceivedATestimonial() throws Exception {
        Space space = space("customer-reviews");
        assertThat(space.isSlugLocked()).isFalse();

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"grace@example.com","content":"Hello there"}
                                """))
                .andExpect(status().isCreated());

        Space reloaded = spaceRepository.findById(space.getId()).orElseThrow();
        assertThat(reloaded.isSlugLocked()).isTrue();
    }

    @Test
    @DisplayName("the same person may submit twice")
    void theSamePersonMaySubmitTwice() throws Exception {
        Space space = space("customer-reviews");

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Grace","email":"grace@example.com",
                                     "content":"They did a wonderful job for us."}
                                    """))
                    .andExpect(status().isCreated());
        }

        assertThat(testimonialRepository.countBySpaceId(space.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("a Space that is not collecting refuses submissions")
    void aSpaceThatIsNotCollectingRefusesSubmissions() throws Exception {
        Space space = spaceThatIsNotCollecting();

        mockMvc.perform(post("/api/app/public/spaces/{slug}/testimonials", space.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"grace@example.com","content":"Hello there"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_ACCEPTING"));

        assertThat(testimonialRepository.count()).isZero();
    }

    @Test
    @DisplayName("a Space that is not collecting says so on its public page")
    void aSpaceThatIsNotCollectingSaysSoOnItsPublicPage() throws Exception {
        Space space = spaceThatIsNotCollecting();

        mockMvc.perform(get("/api/app/public/spaces/{slug}", space.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepting").value(false));
    }

    /**
     * Creates a Space that its owner can no longer edit.
     *
     * <p>The owner has four Spaces on the Free plan, which allows three. The
     * three oldest stay editable and the newest one does not, so the Space
     * created here has to be the newest of the four (OPEN-B).
     *
     * @return the Space
     */
    private Space spaceThatIsNotCollecting() {
        AppUser owner = userRepository.save(new AppUser(
                "busy@example.com", "{noop}", "Busy"));

        for (int index = 0; index < 3; index++) {
            spaceRepository.save(new Space(owner.getId(), "Older " + index, "older-" + index));
        }

        // Created last, so it is the newest of the four and the one that
        // falls outside the Free limit.
        Space space = new Space(owner.getId(), "Newest", "newest");
        return spaceRepository.save(space);
    }

    /**
     * Creates a customer with a Space.
     *
     * @param slug the Space's slug
     * @return the Space
     */
    private Space space(String slug) {
        AppUser owner = userRepository.save(new AppUser(
                EMAIL, "{noop}", "Ada"));
        Space space = new Space(owner.getId(), "Customer reviews", slug);
        space.setSubtitle("What people say");
        return spaceRepository.save(space);
    }
}
