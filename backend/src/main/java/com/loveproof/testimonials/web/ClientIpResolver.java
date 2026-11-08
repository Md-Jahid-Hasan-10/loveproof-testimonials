package com.loveproof.testimonials.web;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Reads the client address for rate-limit bookkeeping.
 *
 * <p>The raw address is never stored or logged (NFR-SEC-6). It is handed to
 * {@link com.loveproof.testimonials.service.FingerprintService}, which salts and hashes it, and the
 * hash is what the rate limiter keys on.
 *
 * <p>The address comes from {@code X-Forwarded-For} when the deployment sits behind a reverse
 * proxy, and from the socket otherwise. The forwarded header is only honoured when the
 * deployment declares that it is behind a trusted proxy
 * ({@code loveproof.security.trust-forwarded-for}); otherwise a client could set the header
 * itself and walk straight past the limiter.
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedFor;

    /**
     * Creates the resolver.
     *
     * @param trustForwardedFor whether {@code X-Forwarded-For} may be trusted
     */
    public ClientIpResolver(
            @org.springframework.beans.factory.annotation.Value(
                    "${loveproof.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    /**
     * Reads the client address.
     *
     * @param request the servlet request
     * @return the client address, or {@code null} when it is unavailable
     */
    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // The left-most entry is the original client; the rest are proxies.
                String first = forwarded.split(",")[0].strip();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
