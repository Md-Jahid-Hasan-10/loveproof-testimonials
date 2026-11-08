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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the session cookie as a browser would receive it.
 *
 * <p>The other admin tests use MockMvc, which does not simulate the servlet container's cookie
 * handling: MockMvc never writes a {@code Set-Cookie} header for the session, because the
 * container — not the framework — is responsible for it. Asserting the cookie's attributes
 * through MockMvc would therefore assert nothing about the running application.
 *
 * <p>This test starts the real embedded Tomcat and reads the {@code Set-Cookie} headers off the
 * wire, so it verifies what the browser actually receives.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AdminSessionCookieTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("loveproof_cookie_test")
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
    void theLoginResponseCarriesASessionCookieThatTheBrowserCannotRead() {
        ResponseEntity<String> response = login();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String sessionCookie = header(response, "SESSION=");

        assertThat(sessionCookie)
                .as("The login response must set the session cookie")
                .isNotNull();
        // The cookie carries the session id, so a script must not be able to read it.
        assertThat(sessionCookie).contains("HttpOnly");
        // Lax keeps the cookie off cross-site POSTs while still sending it on top-level
        // navigations.
        assertThat(sessionCookie).contains("SameSite=Lax");
        // The test server is plain HTTP, so the cookie must not be `Secure`.
        assertThat(sessionCookie).doesNotContain("Secure");
    }

    @Test
    void theSessionCookieKeepsTheAdministratorSignedInAcrossRequests() {
        ResponseEntity<String> login = login();

        String sessionId = sessionId(header(login, "SESSION="));
        assertThat(sessionId).isNotNull();

        // The browser sends the cookie back on the next request. Without it the
        // administrator would be signed out immediately.
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "SESSION=" + sessionId);

        ResponseEntity<String> current = restTemplate.exchange(
                "/api/admin/session",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(current.getBody()).contains("testadmin");
    }

    @Test
    void loggingOutClearsTheSessionCookie() {
        ResponseEntity<String> login = login();

        String sessionId = sessionId(header(login, "SESSION="));
        assertThat(sessionId).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "SESSION=" + sessionId);

        ResponseEntity<String> logout = restTemplate.exchange(
                "/api/admin/session",
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                String.class);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(header(logout, "SESSION="))
                .as("The logout response must expire the session cookie")
                .isNotNull();
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

    /**
     * Extracts the value of a cookie from its {@code Set-Cookie} header.
     *
     * @param header the header, for example {@code SESSION=abc; Path=/; HttpOnly}
     * @return the value, or {@code null} when the header is {@code null}
     */
    private String sessionId(String header) {
        if (header == null) {
            return null;
        }
        int end = header.indexOf(';');
        String pair = end < 0 ? header : header.substring(0, end);
        int equals = pair.indexOf('=');
        return equals < 0 ? null : pair.substring(equals + 1);
    }
}
