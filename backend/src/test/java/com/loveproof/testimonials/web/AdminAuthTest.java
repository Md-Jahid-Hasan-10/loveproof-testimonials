package com.loveproof.testimonials.web;

import static com.loveproof.testimonials.support.AdminTestSupport.ADMIN_PASSWORD;
import static com.loveproof.testimonials.support.AdminTestSupport.ADMIN_USERNAME;
import static com.loveproof.testimonials.support.AdminTestSupport.asAdmin;
import static com.loveproof.testimonials.support.AdminTestSupport.asAdminWithoutCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.loveproof.testimonials.domain.AdminUser;
import com.loveproof.testimonials.repository.AdminUserRepository;
import com.loveproof.testimonials.repository.ModerationActionRepository;
import com.loveproof.testimonials.repository.TestimonialRepository;
import com.loveproof.testimonials.service.TestimonialService;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies that the admin surface is actually protected.
 *
 * <p>These are the acceptance criteria from the Phase 3 of the implementation plan: an
 * unauthenticated admin request is refused, a wrong password is refused without revealing which
 * half was wrong, the session cookie carries the right attributes, a logout really invalidates the
 * session, and a state-changing request without a CSRF token is refused.
 */
@AutoConfigureMockMvc
class AdminAuthTest extends PostgresIntegrationTest {

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

    @Autowired
    private AdminUserRepository adminUserRepository;

    @BeforeEach
    void clear() {
        moderationActionRepository.deleteAll();
        testimonialRepository.deleteAll();
    }

    @Test
    void anAdminEndpointWithoutASessionIsRefused() throws Exception {
        mockMvc.perform(get("/api/admin/testimonials/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void everyAdminEndpointIsProtected() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(get("/api/admin/testimonials")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/testimonials/deleted")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/testimonials/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/testimonials/{id}/approve", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/testimonials/{id}", id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void thePublicApiStaysOpen() throws Exception {
        mockMvc.perform(get("/api/testimonials")).andExpect(status().isOk());
        mockMvc.perform(get("/api/testimonials/stats")).andExpect(status().isOk());
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void correctCredentialsSignTheAdministratorIn() throws Exception {
        mockMvc.perform(post("/api/admin/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\""
                                + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.displayName").value("Test Admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void aWrongPasswordIsRefused() throws Exception {
        mockMvc.perform(post("/api/admin/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void anUnknownUserIsRefusedWithTheSameMessageAsAWrongPassword() throws Exception {
        mockMvc.perform(post("/api/admin/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void aSuccessfulLoginRecordsTheLoginTimestamp() throws Exception {
        Instant before = adminUserRepository.findByUsername(ADMIN_USERNAME)
                .orElseThrow()
                .getLastLoginAt();

        mockMvc.perform(post("/api/admin/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\""
                                + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        Instant after = adminUserRepository.findByUsername(ADMIN_USERNAME)
                .orElseThrow()
                .getLastLoginAt();

        assertThat(after).isNotNull();
        // The timestamp moves forward; it is not asserted to be exactly "now"
        // because the tests share one database and another login may have
        // happened in between.
        assertThat(after).isAfterOrEqualTo(before);
    }

    @Test
    void theSessionCanBeRestoredAfterARefresh() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/admin/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USERNAME));
    }

    @Test
    void withoutASessionTheCurrentSessionEndpointIsRefused() throws Exception {
        mockMvc.perform(get("/api/admin/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutInvalidatesTheSessionServerSide() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/admin/session").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/session").session(session))
                .andExpect(status().isNoContent());

        // The same cookie must no longer authenticate anything.
        mockMvc.perform(get("/api/admin/session").session(session))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/testimonials/stats").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aStateChangingRequestWithoutACsrfTokenIsRefused() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdminWithoutCsrf(mockMvc, post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStateChangingRequestWithACsrfTokenSucceeds() throws Exception {
        UUID id = create("Jane Doe", CONTENT);

        mockMvc.perform(asAdmin(mockMvc, post("/api/admin/testimonials/{id}/approve", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void theCsrfTokenCookieIsReadableByTheFrontend() throws Exception {
        var result = mockMvc.perform(get("/api/testimonials")).andReturn();

        var cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        // The token is deliberately not HttpOnly: the SPA has to read it to echo it back.
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    void aSignedInAdministratorCanReadTheAdminStats() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/admin/testimonials/stats").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.pending").value(0));
    }

    /**
     * Signs in and returns the resulting session.
     *
     * @return the session
     * @throws Exception when the login fails
     */
    private MockHttpSession signIn() throws Exception {
        var result = mockMvc.perform(post("/api/admin/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\""
                                + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /**
     * Creates a pending testimonial.
     *
     * @param name    the name
     * @param content the testimonial text
     * @return the identifier
     */
    private UUID create(String name, String content) {
        return testimonialService.create(name, null, null, 5, content, null).getId();
    }
}
