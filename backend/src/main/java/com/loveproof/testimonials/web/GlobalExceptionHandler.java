package com.loveproof.testimonials.web;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.loveproof.testimonials.exception.ConflictException;
import com.loveproof.testimonials.exception.DuplicateTestimonialException;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.exception.PlanLimitExceededException;
import com.loveproof.testimonials.exception.SpaceNotAcceptingException;
import com.loveproof.testimonials.web.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Renders every failure as the shared error envelope.
 *
 * <p>Controllers never build error responses by hand; they throw and this advice formats them. The
 * envelope shape is fixed by FR-6.1 and {@code CLAUDE.md} §6.2.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    /**
     * Creates the handler.
     *
     * @param clock the clock, used for the error timestamp
     */
    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /**
     * Handles a bean validation failure on a request body.
     *
     * @param ex       the exception
     * @param request  the servlet request
     * @return a 400 with the per-field messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // Keep the first message per field: the most specific one.
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_FAILED",
                "The submitted testimonial is not valid.",
                fieldErrors,
                clock.instant(),
                path(request)));
    }

    /**
     * Handles a malformed or unparseable request body.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return a 400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Rejected an unreadable request body on {}", path(request));
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "MALFORMED_REQUEST",
                "The request body could not be read.",
                Map.of(),
                clock.instant(),
                path(request)));
    }

    /**
     * Handles a query parameter of the wrong type, e.g. a non-numeric {@code rating}.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return a 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_FAILED",
                "The request contains an invalid parameter.",
                Map.of(ex.getName(), "Value must be of type " + ex.getRequiredType().getSimpleName() + "."),
                clock.instant(),
                path(request)));
    }

    /**
     * Handles a missing resource.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return a 404
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                "NOT_FOUND",
                ex.getMessage(),
                Map.of(),
                clock.instant(),
                path(request)));
    }

    /**
     * Handles a duplicate submission.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return a 409
     */
    @ExceptionHandler(DuplicateTestimonialException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateTestimonialException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                "DUPLICATE_SUBMISSION",
                ex.getMessage(),
                Map.of(),
                clock.instant(),
                path(request)));
    }

    /**
     * Handles a conflict with the current state of a resource.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return a 409
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                "CONFLICT",
                ex.getMessage(),
                Map.of(),
                clock.instant(),
                path(request)));
    }

    /**
     * Handles a submission to a Space that is not collecting.
     *
     * <p>The answer is a 409 with a neutral message: the submitter learns
     * that the Space is not accepting responses and nothing else. Why it
     * stopped is the owner's business, and the owner sees the reason on
     * their own Space page (PRD §4.5).
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return a 409
     */
    @ExceptionHandler(SpaceNotAcceptingException.class)
    public ResponseEntity<ErrorResponse> handleNotAccepting(
            SpaceNotAcceptingException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                "SPACE_NOT_ACCEPTING",
                ex.getMessage(),
                Map.of(),
                clock.instant(),
                path(request)));
    }

    /**
     * Handles an action that would take the account past a plan limit.
     *
     * <p>The answer is a 409 rather than a 403: nothing is wrong with the
     * request, the account simply cannot do this yet. The message says what
     * was reached and what to do about it, which is what the interface shows
     * next to its upgrade prompt.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return a 409
     */
    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handlePlanLimit(
            PlanLimitExceededException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                "PLAN_LIMIT_REACHED",
                ex.getMessage(),
                Map.of(),
                clock.instant(),
                path(request)));
    }

    /**
     * Handles an unknown route.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return a 404
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        return notFound(request);
    }
    /**
     * Handles a request for a static resource that does not exist.
     *
     * <p>Spring 6 throws {@link NoResourceFoundException} rather than {@link NoHandlerFoundException}
     * for unmatched paths, so it is handled here too.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return a 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return notFound(request);
    }

    /**
     * Handles an illegal argument, which indicates a broken invariant rather than bad input.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return a 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_FAILED",
                ex.getMessage(),
                Map.of(),
                clock.instant(),
                path(request)));
    }

    /**
     * Handles anything unforeseen.
     *
     * <p>The exception is logged in full server-side; the client receives a generic message, so no
     * internal detail leaks out of the API.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return a 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while serving {}", path(request), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                "INTERNAL_ERROR",
                "Something went wrong. Please try again later.",
                Map.of(),
                clock.instant(),
                path(request)));
    }

    /**
     * Reads the request path.
     *
     * @param request the servlet request
     * @return the path
     */
    private String path(HttpServletRequest request) {
        return request.getRequestURI();
    }

    /**
     * Builds the shared 404 response.
     *
     * @param request the servlet request
     * @return the response
     */
    private ResponseEntity<ErrorResponse> notFound(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                "NOT_FOUND",
                "No endpoint matches this request.",
                Map.of(),
                clock.instant(),
                path(request)));
    }
}
