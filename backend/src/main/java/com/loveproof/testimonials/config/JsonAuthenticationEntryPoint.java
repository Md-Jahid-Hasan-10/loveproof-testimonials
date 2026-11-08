package com.loveproof.testimonials.config;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.loveproof.testimonials.web.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers an unauthenticated request with the shared error envelope.
 *
 * <p>The admin API is a JSON API, so an unauthenticated call gets a {@code 401} with a readable
 * body rather than a redirect to a login page — there is no login page to redirect to, and the
 * frontend distinguishes "not signed in" by the status code.
 */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final Clock clock;

    /**
     * Creates the entry point.
     *
     * @param clock the clock, used for the error timestamp
     */
    public JsonAuthenticationEntryPoint(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body = new ErrorResponse(
                "UNAUTHENTICATED",
                "You must sign in to access this resource.",
                Map.of(),
                clock.instant(),
                request.getRequestURI());

        response.getWriter().write("""
                {"code":"%s","message":"%s","fieldErrors":{},"timestamp":"%s","path":"%s"}"""
                .formatted(body.code(), body.message(), body.timestamp(), body.path()));
    }
}
