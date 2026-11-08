package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.AuthTokenRepository;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the customer authentication endpoints.
 *
 * <p>These tests drive the real filter chain, so the things they assert are
 * the things a browser would see: the session is created on sign-in, the
 * protected routes refuse an anonymous caller with a 401, and the sign-out
 * really ends the session rather than merely forgetting it.
 *
 * <p>The two surfaces are also checked against each other. A customer session
 * must not open an administrator route, and an administrator session must not
 * open a customer route.
 */
@AutoConfigureMockMvc
class AppAuthApiTest extends PostgresIntegrationTest {

    private static final String EMAIL = "ada@example.com";

    private static final String PASSWORD = "a-good-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AuthTokenRepository tokenRepository;

    @BeforeEach
    void clearTheAccounts() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("registering returns the new account and leaves it unverified")
    void registeringReturnsTheNewAccount() throws Exception {
        mockMvc.perform(post("/api/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"Ada\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.displayName").value("Ada"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.plan").value("FREE"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("registering with a taken email is a conflict")
    void registeringWithATakenEmailIsAConflict() throws Exception {
        register();

        mockMvc.perform(post("/api/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"another-password\",\"displayName\":\"Ada\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("a too-short password is rejected by validation")
    void aTooShortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"short\",\"displayName\":\"Ada\"}"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("signing in returns the account and starts a session")
    void signingInReturnsTheAccount() throws Exception {
        register();

        mockMvc.perform(post("/api/app/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("a wrong password is refused with the generic message")
    void aWrongPasswordIsRefused() throws Exception {
        register();

        mockMvc.perform(post("/api/app/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"not-the-password\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("That email and password combination is not correct."));
    }

    @Test
    @DisplayName("an unknown email is refused with the same message as a wrong password")
    void anUnknownEmailIsRefusedWithTheSameMessage() throws Exception {
        mockMvc.perform(post("/api/app/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"not-the-password\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("That email and password combination is not correct."));
    }

    @Test
    @DisplayName("the signed-in customer can be read back")
    void theSignedInCustomerCanBeReadBack() throws Exception {
        register();
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/app/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("the current customer endpoint refuses an anonymous caller")
    void theCurrentCustomerEndpointRefusesAnAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/app/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("signing out really ends the session")
    void signingOutReallyEndsTheSession() throws Exception {
        register();
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/app/auth/me").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/app/auth/sign-out")
                        .session(session)
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());

        // The same session must no longer authenticate anything.
        mockMvc.perform(get("/api/app/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the forgot-password endpoint always answers the same way")
    void theForgotPasswordEndpointAlwaysAnswersTheSameWay() throws Exception {
        register();

        mockMvc.perform(post("/api/app/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/app/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("the verification-token check reports a bad token as invalid")
    void theVerificationTokenCheckReportsABadTokenAsInvalid() throws Exception {
        mockMvc.perform(get("/api/app/auth/verify-email").param("token", "never-issued"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    @DisplayName("a customer session cannot open an administrator route")
    void aCustomerSessionCannotOpenAnAdministratorRoute() throws Exception {
        register();
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/admin/testimonials/stats").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an administrator session cannot open a customer route")
    void anAdministratorSessionCannotOpenACustomerRoute() throws Exception {
        MockHttpSession adminSession =
                (MockHttpSession) mockMvc.perform(post("/api/admin/session")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"testadmin\",\"password\":\"test-password\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getRequest()
                        .getSession(false);

        mockMvc.perform(get("/api/app/auth/me").session(adminSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a state-changing customer request without a CSRF token is refused")
    void aStateChangingCustomerRequestWithoutACsrfTokenIsRefused() throws Exception {
        register();
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/app/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"another-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a state-changing customer request with a CSRF token succeeds")
    void aStateChangingCustomerRequestWithACsrfTokenSucceeds() throws Exception {
        register();
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/app/auth/change-password")
                        .session(session)
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"another-password\"}"))
                .andExpect(status().isNoContent());

        AppUser updated = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(updated.getPasswordHash()).isNotEqualTo(PASSWORD);
    }

    /**
     * Registers the test customer.
     *
     * @throws Exception when the registration fails
     */
    private void register() throws Exception {
        mockMvc.perform(post("/api/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"Ada\"}"))
                .andExpect(status().isCreated());
    }

    /**
     * Signs the test customer in and returns the resulting session.
     *
     * @return the session
     * @throws Exception when the sign-in fails
     */
    private MockHttpSession signIn() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/app/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
