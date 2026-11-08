package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.domain.SpaceTestimonial;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.SpaceRepository;
import com.loveproof.testimonials.repository.SpaceTestimonialRepository;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies what the dashboard reports.
 *
 * <p>The PRD's acceptance criteria, in full (PRD §4.6): the numbers reflect
 * only the account's own data, the chart shows a point for every day in the
 * range — including the days with no submissions — and a Premium account
 * sees no upgrade notice.
 */
@AutoConfigureMockMvc
class DashboardApiTest extends PostgresIntegrationTest {

    private static final String EMAIL = "ada@example.com";

    private static final String PASSWORD = "a-good-password";

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    @DisplayName("the dashboard needs a signed-in account")
    void theDashboardNeedsASignedInAccount() throws Exception {
        mockMvc.perform(get("/api/app/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an account with no Spaces reports zeroes, not an error")
    void anAccountWithNoSpacesReportsZeroes() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/app/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpaces").value(0))
                .andExpect(jsonPath("$.totalTestimonials").value(0))
                .andExpect(jsonPath("$.uniqueSubmitters").value(0))
                .andExpect(jsonPath("$.daily.length()").value(30))
                .andExpect(jsonPath("$.recent.length()").value(0));
    }

    @Test
    @DisplayName("the numbers count the account's own Spaces and testimonials")
    void theNumbersCountTheAccountsOwnData() throws Exception {
        MockHttpSession session = signIn();
        Space first = space();
        Space second = space();

        testimonial(first, "Grace", "grace@example.com", "Lovely work.", now());
        testimonial(first, "Alan", "alan@example.com", "Fine, thank you.", now());
        testimonial(second, "Grace", "grace@example.com", "Still lovely.", now());

        mockMvc.perform(get("/api/app/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpaces").value(2))
                .andExpect(jsonPath("$.totalTestimonials").value(3))
                // One person who submitted to two Spaces counts once.
                .andExpect(jsonPath("$.uniqueSubmitters").value(2));
    }

    @Test
    @DisplayName("the numbers never include another account's data")
    void theNumbersNeverIncludeAnotherAccountsData() throws Exception {
        MockHttpSession session = signIn();
        Space mine = space();
        testimonial(mine, "Grace", "grace@example.com", "Lovely work.", now());

        // A second account, with its own Space and its own submissions.
        AppUser other = userRepository.save(new AppUser(
                "someone-else@example.com", "{noop}", "Somebody Else"));
        Space theirs = spaceRepository.save(
                new Space(other.getId(), "Their reviews", "theirs"));
        testimonial(theirs, "Alan", "alan@example.com", "Not mine.", now());

        mockMvc.perform(get("/api/app/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpaces").value(1))
                .andExpect(jsonPath("$.totalTestimonials").value(1))
                .andExpect(jsonPath("$.uniqueSubmitters").value(1));
    }

    @Test
    @DisplayName("the chart has a point for every day in the range, zeros included")
    void theChartHasAPointForEveryDayInTheRange() throws Exception {
        MockHttpSession session = signIn();
        Space space = space();
        testimonial(space, "Grace", "grace@example.com", "Today.", now());

        JsonNode daily = daily(session, 7);

        assertThat(daily.size()).isEqualTo(7);
        // The last bucket is today, and it holds the one submission.
        assertThat(daily.get(6).get("count").asLong()).isEqualTo(1);
        // Every other day has a point and it is zero — the gap is shown, not
        // skipped, or the line would lie about what happened in between.
        for (int index = 0; index < 6; index++) {
            assertThat(daily.get(index).get("count").asLong()).isZero();
        }
    }

    @Test
    @DisplayName("the chart buckets by the UTC day the submission arrived on")
    void theChartBucketsByTheUtcDay() throws Exception {
        MockHttpSession session = signIn();
        Space space = space();

        // Two on the same UTC day, one on the day before.
        Instant today = now();
        testimonial(space, "Grace", "grace@example.com", "One.", today);
        testimonial(space, "Alan", "alan@example.com", "Two.", today);
        testimonial(space, "Eve", "eve@example.com", "Three.",
                today.minus(1, ChronoUnit.DAYS));

        JsonNode daily = daily(session, 3);

        assertThat(daily.get(2).get("count").asLong()).isEqualTo(2);
        assertThat(daily.get(1).get("count").asLong()).isEqualTo(1);
        assertThat(daily.get(0).get("count").asLong()).isZero();
    }

    @Test
    @DisplayName("the range is selectable, and 30 days is the default")
    void theRangeIsSelectable() throws Exception {
        MockHttpSession session = signIn();
        space();

        mockMvc.perform(get("/api/app/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rangeDays").value(30))
                .andExpect(jsonPath("$.daily.length()").value(30));

        mockMvc.perform(get("/api/app/dashboard").param("days", "7").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rangeDays").value(7))
                .andExpect(jsonPath("$.daily.length()").value(7));
    }

    @Test
    @DisplayName("an absurd range is clamped rather than refused")
    void anAbsurdRangeIsClamped() throws Exception {
        MockHttpSession session = signIn();
        space();

        mockMvc.perform(get("/api/app/dashboard").param("days", "100000").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rangeDays").value(365));

        mockMvc.perform(get("/api/app/dashboard").param("days", "0").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rangeDays").value(1));
    }

    @Test
    @DisplayName("the recent submissions are listed newest first")
    void theRecentSubmissionsAreListedNewestFirst() throws Exception {
        MockHttpSession session = signIn();
        Space space = space();

        Instant now = now();
        testimonial(space, "Older", "older@example.com", "Submitted earlier.",
                now.minus(2, ChronoUnit.HOURS));
        testimonial(space, "Newer", "newer@example.com", "Submitted just now.", now);

        mockMvc.perform(get("/api/app/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recent.length()").value(2))
                .andExpect(jsonPath("$.recent[0].name").value("Newer"))
                .andExpect(jsonPath("$.recent[1].name").value("Older"))
                // The Space each one belongs to is named, so a dashboard with
                // several Spaces is readable.
                .andExpect(jsonPath("$.recent[0].spaceTitle").value("Customer reviews"));
    }

    @Test
    @DisplayName("a Free account is told what the plan allows")
    void aFreeAccountIsToldWhatThePlanAllows() throws Exception {
        MockHttpSession session = signIn();
        space();

        mockMvc.perform(get("/api/app/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.spacesLimit").value(3));
    }

    @Test
    @DisplayName("a Premium account is told what the plan allows")
    void aPremiumAccountIsToldWhatThePlanAllows() throws Exception {
        MockHttpSession session = signIn();
        space();
        upgradeToPremium();

        mockMvc.perform(get("/api/app/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.spacesLimit").value(25));
    }

    /**
     * The chart's daily buckets.
     *
     * @param session the signed-in session
     * @param days    how many days to ask for
     * @return the buckets, oldest first
     * @throws Exception when the request fails
     */
    private JsonNode daily(MockHttpSession session, int days) throws Exception {
        String body = mockMvc.perform(get("/api/app/dashboard")
                        .param("days", String.valueOf(days))
                        .session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return MAPPER.readTree(body).get("daily");
    }

    /**
     * Creates a Space owned by the signed-in account.
     *
     * @return the Space
     */
    private Space space() {
        AppUser owner = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        return spaceRepository.save(
                new Space(owner.getId(), "Customer reviews", "space-" + System.nanoTime()));
    }

    /**
     * Creates a testimonial directly.
     *
     * @param space       the Space it belongs to
     * @param name        the submitter's name
     * @param email       the submitter's email
     * @param content     the testimonial text
     * @param submittedAt when it arrived
     * @return the testimonial
     */
    private SpaceTestimonial testimonial(
            Space space,
            String name,
            String email,
            String content,
            Instant submittedAt) {
        SpaceTestimonial testimonial = new SpaceTestimonial(
                space.getId(), name, email, content);
        testimonial.setConsentGiven(true);
        testimonial.setSubmittedAt(submittedAt);
        testimonial.setUpdatedAt(submittedAt);
        return testimonialRepository.save(testimonial);
    }

    /**
     * Now.
     *
     * @return the current instant
     */
    private Instant now() {
        return Instant.now();
    }

    /**
     * Moves the account onto the Premium plan.
     */
    private void upgradeToPremium() {
        AppUser owner = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        owner.setPlan(Plan.PREMIUM);
        userRepository.save(owner);
    }

    /**
     * Signs in as the test customer, registering the account if needed.
     *
     * @return the session
     * @throws Exception when the sign-in fails
     */
    private MockHttpSession signIn() throws Exception {
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
}
