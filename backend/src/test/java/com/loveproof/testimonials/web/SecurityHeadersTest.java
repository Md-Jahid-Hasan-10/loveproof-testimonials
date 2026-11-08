package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * The security headers on every response.
 *
 * <p>The headers are what a browser is told to trust about the response: no framing, no content
 * sniffing, no leaking the path to third parties, and no scripts or connections outside the
 * application's own origin.
 */
@AutoConfigureMockMvc
class SecurityHeadersTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a public read carries the security headers")
    void publicReadCarriesTheHeaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/testimonials")).andReturn();

        assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                .contains("default-src 'self'")
                .contains("script-src 'self'")
                .contains("object-src 'none'")
                .contains("frame-ancestors 'none'")
                .contains("base-uri 'self'");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(result.getResponse().getHeader("Referrer-Policy")).isEqualTo("same-origin");
    }

    @Test
    @DisplayName("the health endpoint carries the security headers too")
    void healthCarriesTheHeaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/health")).andReturn();

        assertThat(result.getResponse().getHeader("Content-Security-Policy")).isNotNull();
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("a failed request carries the security headers too")
    void failureCarriesTheHeaders() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Security-Policy")).isNotNull();
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("the strict transport header is absent on plain HTTP")
    void hstsIsAbsentWithoutHttps() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/testimonials")).andReturn();

        // The test server is plain HTTP, so sending HSTS would let an
        // attacker on the path strip it.
        assertThat(result.getResponse().getHeader("Strict-Transport-Security")).isNull();
    }

    @Test
    @DisplayName("the policy allows a same-origin connection to the API")
    void policyAllowsTheApiConnection() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/testimonials")).andReturn();

        // The SPA calls the API on its own origin and nothing else.
        assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                .contains("connect-src 'self'");
    }
}
