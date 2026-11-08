package com.loveproof.testimonials.web;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loveproof.testimonials.web.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes the shared error envelope straight onto the servlet response.
 *
 * <p>{@link GlobalExceptionHandler} renders the envelope for failures raised inside a controller.
 * A filter, however, runs before the dispatcher servlet: an exception thrown there never reaches
 * the {@code @RestControllerAdvice}, so the client would receive a servlet container error page
 * instead of the envelope. This writer lets a filter produce the same envelope, so the shape of a
 * failure does not depend on where it was raised.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Creates the writer.
     *
     * @param objectMapper the mapper that renders the envelope as JSON
     * @param clock        the clock, used for the error timestamp
     */
    public ErrorResponseWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Writes an error envelope.
     *
     * @param response the servlet response
     * @param request  the servlet request, used for the path
     * @param status   the status code to send
     * @param code     the machine-readable error code
     * @param message  the human-readable message
     * @param headers  extra headers to set, e.g. {@code Retry-After}
     * @throws IOException when the body cannot be written
     */
    public void write(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            String code,
            String message,
            Map<String, String> headers)
            throws IOException {

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        headers.forEach(response::setHeader);

        ErrorResponse body = new ErrorResponse(
                code,
                message,
                Map.of(),
                Instant.now(clock),
                request.getRequestURI());

        objectMapper.writeValue(response.getWriter(), body);
        response.flushBuffer();
    }
}
