package com.loveproof.testimonials.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The security headers every response carries.
 *
 * <p>Spring Security already adds {@code X-Content-Type-Options} and {@code X-Frame-Options}. The
 * headers configured here are the ones it does not add on its own.
 *
 * <p>The content security policy is deliberately strict. The application ships no inline scripts
 * and no inline event handlers, so {@code script-src 'self'} holds. The one concession is
 * {@code 'unsafe-inline'} for styles: the stylesheet Tailwind produces is a single static file,
 * but React sets a handful of element styles inline, and a strict style policy would break them.
 */
@Configuration
public class SecurityHeadersConfig {

    /**
     * The content security policy.
     *
     * <p>{@code connect-src 'self'} allows the SPA to call the API on its own origin, which is the
     * only thing it ever calls.
     */
    private static final String CONTENT_SECURITY_POLICY = String.join(
            "; ",
            "default-src 'self'",
            "script-src 'self'",
            // React sets element styles inline, so the style policy has to allow it.
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self' data:",
            // The API is same-origin in production. 'self' also covers the Vite
            // dev proxy, where the SPA and the API share the origin.
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /**
     * The strict content security policy used in production.
     *
     * <p>It adds {@code upgrade-insecure-requests}, so a browser refuses to load any sub-resource
     * over plain HTTP even if the page itself was served over HTTPS.
     */
    private static final String CONTENT_SECURITY_POLICY_STRICT = CONTENT_SECURITY_POLICY
            + "; upgrade-insecure-requests";

    /**
     * The referrer policy.
     *
     * <p>{@code same-origin} sends the full referrer to our own origin and only the origin
     * elsewhere. The wall links out to nothing today, but a testimonial may carry a company name
     * that becomes a link later, and this keeps the path out of third-party logs.
     */
    private static final String REFERRER_POLICY = "same-origin";

    /**
     * How long a browser should remember that this host is HTTPS-only.
     *
     * <p>Two years is the value the OWASP secure headers project recommends.
     */
    private static final long HSTS_MAX_AGE_SECONDS = 63072000L;

    /**
     * Adds the headers to every response.
     *
     * @param secureCookies whether the deployment is served over HTTPS; the
     *                      {@code prod} profile sets this to {@code true}
     * @return the filter
     */
    @Bean
    public SecurityHeadersFilter securityHeadersFilter(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) {
        return new SecurityHeadersFilter(
                secureCookies ? CONTENT_SECURITY_POLICY_STRICT : CONTENT_SECURITY_POLICY,
                secureCookies);
    }

    /**
     * Registers the header filter on the servlet chain.
     *
     * <p>Registering it here rather than letting it be picked up as a component
     * keeps it out of Spring Boot's automatic filter registration, which would
     * otherwise register it twice — once for the dispatcher and once for the
     * error dispatch — and let it run twice on some paths.
     *
     * @param filter the filter
     * @return the registration
     */
    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration(
            SecurityHeadersFilter filter) {
        FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * The filter that writes the headers.
     *
     * <p>It is a separate class so it can be registered on the servlet filter chain, which covers
     * the static resources the SPA is served from as well as the API.
     */
    public static class SecurityHeadersFilter extends OncePerRequestFilter {

        private final String contentSecurityPolicy;
        private final boolean httpsOnly;

        SecurityHeadersFilter(String contentSecurityPolicy, boolean httpsOnly) {
            this.contentSecurityPolicy = contentSecurityPolicy;
            this.httpsOnly = httpsOnly;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {

            response.setHeader("Content-Security-Policy", contentSecurityPolicy);
            response.setHeader("Referrer-Policy", REFERRER_POLICY);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");

            // The HSTS header only means anything over HTTPS, and sending it
            // over plain HTTP would let an attacker strip it.
            if (httpsOnly) {
                response.setHeader(
                        "Strict-Transport-Security",
                        "max-age=" + HSTS_MAX_AGE_SECONDS + "; includeSubDomains");
            }

            filterChain.doFilter(request, response);
        }
    }
}
