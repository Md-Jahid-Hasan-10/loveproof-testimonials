package com.loveproof.testimonials.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.domain.AdminUser;
import com.loveproof.testimonials.service.AdminSessionService;
import com.loveproof.testimonials.web.dto.ErrorResponse;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The admin session endpoints.
 *
 * <p>Login, logout and session restore. The session is a server-side HTTP session identified by
 * the {@code SESSION} cookie, which is {@code HttpOnly} and {@code SameSite=Lax} (the cookie
 * attributes are configured in {@code application.yml}).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSessionController {

    private final AdminSessionService adminSessionService;
    private final SecurityContextRepository securityContextRepository;

    /**
     * Creates the controller.
     *
     * @param adminSessionService the authentication service
     * @param securityContextRepository the repository that persists the security context
     */
    public AdminSessionController(
            AdminSessionService adminSessionService,
            SecurityContextRepository securityContextRepository) {
        this.adminSessionService = adminSessionService;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Signs an administrator in.
     *
     * <p>The failure message is deliberately the same for an unknown account and for a wrong
     * password, so the endpoint cannot be used to discover which usernames exist.
     *
     * @param request     the credentials
     * @param httpRequest the servlet request, for the error path
     * @param httpResponse the servlet response
     * @return the signed-in administrator
     */
    @PostMapping("/session")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        try {
            AdminUser admin = adminSessionService.authenticate(request.username(), request.password());

            // Write the security context into the session. Without this the browser
            // would be authenticated for this one request only, and every later admin
            // call would answer 401.
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            return ResponseEntity.ok(new SessionResponse(
                    admin.getUsername(),
                    admin.getDisplayName(),
                    admin.getRole().name(),
                    admin.getLastLoginAt()));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
                    "AUTHENTICATION_FAILED",
                    "Invalid username or password.",
                    Map.of(),
                    java.time.Instant.now(),
                    httpRequest.getRequestURI()));
        }
    }

    /**
     * Signs the current administrator out.
     *
     * <p>The session is invalidated server-side, so the cookie the browser still holds stops
     * authenticating anything. The cookie is also expired explicitly: invalidating the session
     * alone leaves the browser holding a session id that no longer resolves, which it would
     * keep sending on every subsequent request.
     *
     * @param httpRequest the servlet request, for the error path
     * @param httpResponse the servlet response, used to expire the session cookie
     * @return a 204
     */
    @DeleteMapping("/session")
    public ResponseEntity<?> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        var session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        // Expire the cookie with the same attributes it was set with, otherwise the
        // browser keeps it: a cookie is only replaced when the name, path and domain
        // match.
        Cookie cookie = new Cookie("SESSION", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        httpResponse.addCookie(cookie);

        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the current session, when there is one.
     *
     * <p>The frontend calls this on load to restore the session after a page refresh. It answers
     * {@code 200} with the identity, or {@code 401} when there is no session — never a redirect.
     *
     * @return the signed-in administrator
     */
    @GetMapping("/session")
    public ResponseEntity<?> currentSession() {
        return adminSessionService.currentAdmin()
                .map(admin -> ResponseEntity.ok(new SessionResponse(
                        admin.getUsername(),
                        admin.getDisplayName(),
                        admin.getRole().name(),
                        admin.getLastLoginAt())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    /**
     * The body of a login request.
     *
     * @param username the username
     * @param password the password
     */
    public record LoginRequest(
            @NotBlank(message = "A username is required.")
            @Size(max = 50, message = "The username must be at most 50 characters.")
            String username,

            @NotBlank(message = "A password is required.")
            @Size(max = 200, message = "The password must be at most 200 characters.")
            String password) {
    }

    /**
     * The signed-in administrator.
     *
     * @param username     the username
     * @param displayName  the name shown in the admin header
     * @param role         the role
     * @param lastLoginAt  when the account last signed in, or {@code null} on the first login
     */
    public record SessionResponse(
            String username,
            String displayName,
            String role,
            java.time.Instant lastLoginAt) {
    }
}
