package com.loveproof.testimonials.support;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Helpers for the admin test suites.
 *
 * <p>The seeded administrator in the test profile is {@code testadmin} / {@code test-password}.
 *
 * <p>HTTP Basic is deliberately disabled in the application — the admin area uses a form-free
 * JSON login and a server-side session — so these helpers sign in the way a browser does: they
 * call the real {@code POST /api/admin/session} endpoint and reuse the session it creates. The
 * moderation suites therefore exercise the genuine filter chain, including the authorization rules
 * and the CSRF check.
 */
public final class AdminTestSupport {

    /** The seeded administrator's username in the test profile. */
    public static final String ADMIN_USERNAME = "testadmin";

    /** The seeded administrator's password in the test profile. */
    public static final String ADMIN_PASSWORD = "test-password";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AdminTestSupport() {
        // utility class
    }

    /**
     * Signs in as the seeded administrator and returns the resulting session.
     *
     * @param mockMvc the test client
     * @return the authenticated session
     * @throws Exception when the login fails
     */
    public static MockHttpSession signIn(MockMvc mockMvc) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/session")
                        .contentType("application/json")
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\""
                                + ADMIN_PASSWORD + "\"}"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null) {
            throw new IllegalStateException("The login did not create a session.");
        }
        return session;
    }

    /**
     * Signs the request in as the seeded administrator.
     *
     * @param mockMvc the test client
     * @param builder the request
     * @return the request, signed in with a valid CSRF token
     * @throws Exception when the login fails
     */
    public static MockHttpServletRequestBuilder asAdmin(
            MockMvc mockMvc,
            MockHttpServletRequestBuilder builder) throws Exception {
        return builder
                .session(signIn(mockMvc))
                .with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf());
    }

    /**
     * Signs the request in as the administrator but omits the CSRF token.
     *
     * @param mockMvc the test client
     * @param builder the request
     * @return the request, signed in without a CSRF token
     * @throws Exception when the login fails
     */
    public static MockHttpServletRequestBuilder asAdminWithoutCsrf(
            MockMvc mockMvc,
            MockHttpServletRequestBuilder builder) throws Exception {
        return builder.session(signIn(mockMvc));
    }

    /**
     * Reads the CSRF token from the session, for a request that must not use the test helper's
     * built-in token.
     *
     * @param session the session
     * @return the token
     */
    public static String csrfToken(MockHttpSession session) {
        Object attribute = session.getAttribute(
                org.springframework.security.web.csrf.CsrfToken.class.getName());
        if (attribute instanceof org.springframework.security.web.csrf.CsrfToken token) {
            return token.getToken();
        }
        return null;
    }

    /**
     * Reads the username out of a JSON response body.
     *
     * @param result the response
     * @return the username
     * @throws Exception when the body cannot be parsed
     */
    public static String usernameOf(MvcResult result) throws Exception {
        JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
        return body.path("username").asText();
    }
}
