package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * Verifies that the submission rate limit's period is read from its configuration.
 *
 * <p>The period decides how fast the bucket refills, and therefore how long a refused visitor has
 * to wait. It was once read from {@code loveproof.rate-limit.submit.perval-seconds} — a misspelling
 * of {@code period-seconds} — so the configured value never arrived and the window was always the
 * hard-coded default of an hour. The typo was invisible because the default and the configured value
 * happened to be the same number; changing {@code LOVEPROOF_RATE_LIMIT_SUBMIT_PERIOD_SECONDS} had
 * no effect at all.
 *
 * <p>This test pins the period to a value that differs from the default and reads it back out of
 * the {@code Retry-After} header, so the property has to be honoured for the test to pass.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "loveproof.rate-limit.submit.limit=2",
        "loveproof.rate-limit.submit.period-seconds=60",
        "loveproof.rate-limit.login.limit=1000",
        "loveproof.rate-limit.login.period-seconds=300"
})
class RateLimitPeriodConfigurationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiterService limiter;

    @BeforeEach
    void resetBuckets() {
        limiter.reset();
    }

    @Test
    @DisplayName("the submission window comes from the configuration, not from a hard-coded default")
    void theSubmissionWindowComesFromTheConfiguration() throws Exception {
        // Two submissions are allowed by the configured limit of two.
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/testimonials")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(submission("allowed-" + attempt)))
                    .andExpect(status().isCreated());
        }

        MvcResult result = mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submission("blocked")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        long retryAfter = Long.parseLong(result.getResponse().getHeader("Retry-After"));

        // With a limit of two and a window of sixty seconds, the bucket holds
        // two tokens and refills one every thirty seconds. Both tokens have
        // been spent, so the refused request waits for the next one — at most
        // a full window. The default window of an hour would put the ceiling
        // at 3600 seconds, and the assertion below would not hold.
        assertThat(retryAfter)
                .as("the refusal must be sized by the configured window, not by the default")
                .isBetween(1L, 60L);
    }

    /**
     * Builds a submission whose text is unique, so the duplicate-submission fingerprint never
     * rejects it.
     *
     * @param marker a marker that makes the submission unique
     * @return the request body
     */
    private String submission(String marker) {
        return """
                {
                  "name": "Window Probe %s",
                  "role": "QA",
                  "company": "Probe Ltd",
                  "rating": 5,
                  "content": "A submission made to check the configured rate limit window. %s",
                  "consent": true
                }
                """.formatted(marker, marker);
    }
}
