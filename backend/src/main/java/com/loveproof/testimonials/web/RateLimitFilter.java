package com.loveproof.testimonials.web;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.loveproof.testimonials.service.FingerprintService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies the rate limits to the endpoints that can be abused.
 *
 * <p>Two endpoints are limited:
 *
 * <ul>
 *   <li>The public submission endpoint, so one client cannot flood the moderation inbox.</li>
 *   <li>The admin sign-in endpoint, so one client cannot brute-force the administrator's
 *       password.</li>
 * </ul>
 *
 * <p>The login limit is keyed on the client address alone, never on the submitted username. Keying
 * it on the username would let an attacker spread their guesses across many names to lift the
 * limit, and would make the refusal depend on which name was tried — which is exactly the
 * information the generic login message exists to withhold.
 *
 * <p>The filter runs before the security chain, so a refused request never reaches the
 * controller and never touches the database.
 *
 * <p>A refusal is written straight onto the response rather than thrown. The filter sits outside
 * the dispatcher servlet, so an exception raised here would never reach the shared exception
 * advice and the client would receive a container error page instead of the error envelope.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService limiter;
    private final ClientIpResolver ipResolver;
    private final FingerprintService fingerprintService;
    private final ErrorResponseWriter errorResponseWriter;

    private final String ipHashSalt;
    private final int submitLimit;
    private final Duration submitPeriod;
    private final int loginLimit;
    private final Duration loginPeriod;

    /**
     * Creates the filter.
     *
     * @param limiter            the bucket service
     * @param ipResolver         the client address reader
     * @param fingerprintService the salted hash of the address
     * @param errorResponseWriter the writer for the refusal envelope
     * @param ipHashSalt         the per-deployment salt
     * @param submitLimit        how many submissions are allowed per period
     * @param submitPeriod       the period the submission limit applies over
     * @param loginLimit         how many sign-in attempts are allowed per period
     * @param loginPeriod        the period the login limit applies over
     */
    public RateLimitFilter(
            RateLimiterService limiter,
            ClientIpResolver ipResolver,
            FingerprintService fingerprintService,
            ErrorResponseWriter errorResponseWriter,
            @Value("${loveproof.security.ip-hash-salt:loveproof-local-salt}") String ipHashSalt,
            @Value("${loveproof.rate-limit.submit.limit:5}") int submitLimit,
            @Value("${loveproof.rate-limit.submit.period-seconds:3600}") long submitPeriodSeconds,
            @Value("${loveproof.rate-limit.login.limit:10}") int loginLimit,
            @Value("${loveproof.rate-limit.login.period-seconds:300}") long loginPeriodSeconds) {
        this.limiter = limiter;
        this.ipResolver = ipResolver;
        this.fingerprintService = fingerprintService;
        this.errorResponseWriter = errorResponseWriter;
        this.ipHashSalt = ipHashSalt;
        this.submitLimit = submitLimit;
        this.submitPeriod = Duration.ofSeconds(submitPeriodSeconds);
        this.loginLimit = loginLimit;
        this.loginPeriod = Duration.ofSeconds(loginPeriodSeconds);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String message = limitMessageFor(request);
        if (message == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimiterService.Decision decision = consume(request);
        if (!decision.allowed()) {
            errorResponseWriter.write(
                    response,
                    request,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "RATE_LIMITED",
                    message,
                    Map.of("Retry-After", Long.toString(decision.retryAfter())));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Consumes a token from the bucket that applies to this request.
     *
     * @param request the servlet request
     * @return the decision
     */
    private RateLimiterService.Decision consume(HttpServletRequest request) {
        String key = fingerprintService.hashIpAddress(ipResolver.resolve(request), ipHashSalt);
        if (isSubmission(request)) {
            return limiter.tryConsume(key, submitLimit, submitPeriod);
        }
        return limiter.tryConsume(key, loginLimit, loginPeriod);
    }

    /**
     * Whether this request is a public submission.
     *
     * @param request the servlet request
     * @return true for a submission
     */
    private boolean isSubmission(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        if ("/api/testimonials".equals(request.getRequestURI())) {
            return true;
        }
        // A submission into a Space. The path ends in `/testimonials`, which
        // is enough to tell it apart from the Space's read-only form endpoint.
        return request.getRequestURI().startsWith("/api/app/public/spaces/")
                && request.getRequestURI().endsWith("/testimonials");
    }

    /**
     * Whether this request is an admin sign-in.
     *
     * @param request the servlet request
     * @return true for a sign-in
     */
    private boolean isLogin(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/admin/session".equals(request.getRequestURI());
    }

    /**
     * The message shown when the limit is exceeded, or {@code null} when the request is not limited.
     *
     * @param request the servlet request
     * @return the message, or null
     */
    private String limitMessageFor(HttpServletRequest request) {
        if (isSubmission(request)) {
            return "Too many testimonials were submitted from this location. Please try again later.";
        }
        if (isLogin(request)) {
            // The same wording whatever the credentials were, so a refusal
            // never reveals whether the account exists.
            return "Too many sign-in attempts. Please try again later.";
        }
        return null;
    }

    /**
     * Whether the sign-in endpoint is being called.
     *
     * @param request the servlet request
     * @return true for a sign-in
     */
    boolean isLoginRequest(HttpServletRequest request) {
        return isLogin(request);
    }
}
