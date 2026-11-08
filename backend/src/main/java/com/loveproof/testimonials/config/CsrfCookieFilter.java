package com.loveproof.testimonials.config;

import java.io.IOException;

import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ensures the CSRF token cookie is present on every response.
 *
 * <p>Spring Security only writes the {@code XSRF-TOKEN} cookie when the token is actually
 * resolved, which for a deferred token happens late in the request. A SPA that loads a page and
 * then performs a write would otherwise have no token to echo — and would receive a {@code 403}
 * it cannot explain. This filter resolves the token early so the cookie is always there.
 *
 * <p>The token is deliberately readable by JavaScript: the browser-based CSRF defence is that a
 * third-party site cannot read the cookie, so the SPA can echo it in the {@code X-XSRF-TOKEN}
 * header while an attacker's page cannot.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    private final CookieCsrfTokenRepository tokenRepository;

    /**
     * Creates the filter.
     *
     * @param tokenRepository the repository holding the token
     */
    public CsrfCookieFilter(CookieCsrfTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        DeferredCsrfToken deferred = tokenRepository.loadDeferredToken(request, response);
        if (deferred != null) {
            // Resolving the token writes the cookie.
            deferred.get();
        }
        filterChain.doFilter(request, response);
    }
}
