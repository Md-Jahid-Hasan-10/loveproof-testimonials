package com.loveproof.testimonials.config;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.loveproof.testimonials.web.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers an access-denied request with the shared error envelope.
 *
 * <p>Spring Security's default sends an empty body with a {@code 403}. The API contract in
 * {@code CLAUDE.md} §6.2 requires every failure to carry the same envelope, so the frontend can
 * read the error the same way regardless of which layer produced it.
 *
 * <p>A request that is not authenticated at all answers {@code 401} rather than {@code 403},
 * even when the CSRF check is what rejected it. The CSRF filter runs before the authorization
 * rules, so an anonymous write would otherwise be reported as "forbidden" when the accurate
 * answer is "you are not signed in" — and the frontend redirects to the login screen on
 * {@code 401}, not on {@code 403}.
 */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final Clock clock;

    /**
     * Creates the handler.
     *
     * @param clock the clock, used for the error timestamp
     */
    public JsonAccessDeniedHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);

        HttpStatus status = authenticated ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
        String code = authenticated ? "FORBIDDEN" : "UNAUTHENTICATED";
        String message = authenticated
                ? "You do not have permission to perform this action."
                : "You must sign in to access this resource.";

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body = new ErrorResponse(
                code,
                message,
                Map.of(),
                clock.instant(),
                request.getRequestURI());

        response.getWriter().write("""
                {"code":"%s","message":"%s","fieldErrors":{},"timestamp":"%s","path":"%s"}"""
                .formatted(body.code(), body.message(), body.timestamp(), body.path()));
    }
}
