package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the {@code prod} profile marks the session cookie {@code Secure}.
 *
 * <p>This is a separate test class from {@link AdminSessionCookieTest} because the {@code Secure}
 * attribute is driven by the active Spring profile, and a single test context cannot run under two
 * profiles at once.
 *
 * <p>Both {@code test} and {@code prod} are active, with {@code prod} last. Spring resolves a
 * property conflict in favour of the last-activated profile, so the test fixtures (the
 * {@code testadmin} account and the Testcontainers datasource) come from {@code test}, while
 * {@code secure: true} comes from {@code prod}. The {@code test} profile also sets
 * {@code secure: false}; activating {@code prod} afterwards is what makes this test meaningful.
 *
 * <p>The complementary assertion — that the cookie is <em>not</em> {@code Secure} outside
 * production — lives in {@link AdminSessionCookieTest}, which runs under the {@code test} profile
 * alone, over plain HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "prod"})
@Testcontainers
class AdminSessionCookieProfileTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("loveproof_cookie_prod_test")
            .withUsername("loveproof")
            .withPassword("loveproof");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void theProdProfileMarksTheSessionCookieSecure() {
        ResponseEntity<String> response = login();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String sessionCookie = header(response, "SESSION=");

        assertThat(sessionCookie)
                .as("The login response must set the session cookie")
                .isNotNull();
        // Under `prod` the cookie may only travel over HTTPS.
        assertThat(sessionCookie).contains("Secure");
        // The other attributes do not change with the profile: a production cookie
        // is still unreadable by scripts and still withheld from cross-site POSTs.
        assertThat(sessionCookie).contains("HttpOnly");
        assertThat(sessionCookie).contains("SameSite=Lax");
    }

    @Test
    void theProdProfileMarksTheCsrfCookieSecure() {
        ResponseEntity<String> response = login();

        String csrfCookie = header(response, "XSRF-TOKEN=");

        assertThat(csrfCookie)
                .as("The login response must set the CSRF cookie")
                .isNotNull();
        // The CSRF cookie is deliberately readable by the SPA, so it is not HttpOnly —
        // but it is still withheld from cross-site requests and, under `prod`, from
        // plain HTTP.
        assertThat(csrfCookie).doesNotContain("HttpOnly");
        assertThat(csrfCookie).contains("Secure");
    }

    /**
     * Signs in as the seeded administrator.
     *
     * @return the response
     */
    private ResponseEntity<String> login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        return restTemplate.postForEntity(
                "/api/admin/session",
                new HttpEntity<>(
                        "{\"username\":\"testadmin\",\"password\":\"test-password\"}",
                        headers),
                String.class);
    }

    /**
     * Finds a {@code Set-Cookie} header that starts with the given prefix.
     *
     * @param response the response
     * @param prefix   the cookie prefix, for example {@code SESSION=}
     * @return the header, or {@code null} when there is none
     */
    private String header(ResponseEntity<String> response, String prefix) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return null;
        }
        return setCookies.stream()
                .filter(header -> header.startsWith(prefix))
                .findFirst()
                .orElse(null);
    }
}
