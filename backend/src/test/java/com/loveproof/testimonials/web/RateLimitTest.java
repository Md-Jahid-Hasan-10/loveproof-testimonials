package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * The rate limits on the public submission and the admin sign-in.
 *
 * <p>Both limits are keyed on a salted hash of the client address. MockMvc sends
 * {@code 127.0.0.1} for every request, so all the requests in a test share one bucket; a test that
 * needs a second bucket changes the forwarded header — which only counts when the deployment
 * declares it sits behind a trusted proxy.
 *
 * <p>The allowances are lowered here so a test can exhaust a bucket in a handful of requests. The
 * rest of the suite keeps the generous defaults, or its submissions would be refused.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "loveproof.rate-limit.submit.limit=5",
        "loveproof.rate-limit.submit.period-seconds=3600",
        "loveproof.rate-limit.login.limit=10",
        "loveproof.rate-limit.login.period-seconds=300"
})
class RateLimitTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiterService limiter;

    @BeforeEach
    void resetBuckets() {
        limiter.reset();
    }

    /**
     * A submission whose text is unique, so the duplicate-submission fingerprint never rejects it.
     *
     * @param marker a marker that makes the submission unique
     * @return the request body
     */
    private String submission(String marker) {
        return """
                {
                  "name": "Rate Limit Probe %s",
                  "role": "QA",
                  "company": "Probe Ltd",
                  "rating": 5,
                  "content": "A submission made to exercise the rate limit. %s",
                  "consent": true
                }
                """.formatted(marker, marker);
    }

    @Test
    @DisplayName("the public submission endpoint refuses requests past the limit")
    void submissionIsRateLimited() throws Exception {
        // Five submissions are allowed per hour by default.
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/testimonials")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(submission("ok-" + attempt)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submission("blocked")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(
                        "Too many testimonials were submitted from this location. Please try again later."));
    }

    @Test
    @DisplayName("the refusal carries a Retry-After header of at least one second")
    void refusalCarriesRetryAfter() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/testimonials")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(submission("hdr-" + attempt)))
                    .andExpect(status().isCreated());
        }

        MvcResult result = mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submission("hdr-blocked")))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        String retryAfter = result.getResponse().getHeader("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("the limit is per client, so another client is unaffected")
    void limitIsPerClient() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/testimonials")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(submission("first-" + attempt)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submission("blocked")))
                .andExpect(status().isTooManyRequests());

        // A different client address, and therefore a different bucket. The
        // header is only honoured because the test profile trusts it.
        mockMvc.perform(post("/api/testimonials")
                        .header("X-Forwarded-For", "203.0.113.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submission("other-client")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("the admin sign-in endpoint refuses attempts past the limit")
    void loginIsRateLimited() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/api/admin/session")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/admin/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("the login refusal is the same whatever username was tried")
    void loginRefusalRevealsNothing() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/api/admin/session")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        String unknownUser = mockMvc.perform(post("/api/admin/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andReturn()
                .getResponse()
                .getContentAsString();

        limiter.reset();

        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/api/admin/session")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"testadmin\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        String existingUser = mockMvc.perform(post("/api/admin/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testadmin\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The two bodies differ only in the timestamp and the path is the same,
        // so the message itself has to be identical.
        assertThat(messageOf(unknownUser)).isEqualTo(messageOf(existingUser));
        assertThat(messageOf(unknownUser)).doesNotContain("testadmin");
    }

    @Test
    @DisplayName("the read-only endpoints are not rate limited")
    void readsAreNotRateLimited() throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            mockMvc.perform(get("/api/testimonials"))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Reads the message out of an error envelope.
     *
     * @param body the response body
     * @return the message
     */
    private String messageOf(String body) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(body)
                    .path("message")
                    .asText();
        } catch (Exception e) {
            throw new AssertionError("The body was not valid JSON: " + body, e);
        }
    }
}
