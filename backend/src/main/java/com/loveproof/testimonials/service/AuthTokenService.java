package com.loveproof.testimonials.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.domain.AuthToken;
import com.loveproof.testimonials.domain.AuthTokenPurpose;
import com.loveproof.testimonials.repository.AuthTokenRepository;

/**
 * Issues and redeems the single-use tokens behind email verification and
 * password reset.
 *
 * <p>Two properties matter and both are enforced here. The token is stored as
 * a SHA-256 hash, so the database never holds a working link. And it is
 * single-use and expiring, so a link that has been forwarded is useless and a
 * link that sits in an inbox for a month does not work.
 */
@Service
public class AuthTokenService {

    /** How long a verification link stays valid. */
    private static final Duration VERIFICATION_LIFETIME = Duration.ofHours(24);

    /** How long a reset link stays valid. */
    private static final Duration RESET_LIFETIME = Duration.ofMinutes(30);

    private final AuthTokenRepository tokenRepository;

    private final SecureRandom random = new SecureRandom();

    /**
     * Creates the service.
     *
     * @param tokenRepository the token store
     */
    public AuthTokenService(AuthTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Issues a token and returns its plaintext.
     *
     * <p>The plaintext exists only here and in the link that is emailed; only
     * its hash is stored.
     *
     * @param userId  the account the token belongs to
     * @param purpose what the token is for
     * @param now     the current time
     * @return the plaintext token
     */
    @Transactional
    public String issue(UUID userId, AuthTokenPurpose purpose, Instant now) {
        Duration lifetime = purpose == AuthTokenPurpose.VERIFY_EMAIL
                ? VERIFICATION_LIFETIME
                : RESET_LIFETIME;

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        tokenRepository.save(new AuthToken(userId, purpose, hash(plaintext), now.plus(lifetime)));
        return plaintext;
    }

    /**
     * Redeems a token.
     *
     * @param plaintext the token from the link
     * @param purpose   the purpose the token must have
     * @param now       the current time
     * @return the account the token belongs to, or empty when the token is
     *         unknown, already used, expired, or of the wrong purpose
     */
    @Transactional
    public Optional<UUID> redeem(String plaintext, AuthTokenPurpose purpose, Instant now) {
        if (plaintext == null || plaintext.isBlank()) {
            return Optional.empty();
        }

        Optional<AuthToken> found = tokenRepository.findByTokenHash(hash(plaintext));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        AuthToken token = found.get();
        if (token.getPurpose() != purpose || !token.isUsableAt(now)) {
            return Optional.empty();
        }

        // Marked used before the caller acts on it, so the same token cannot
        // be redeemed twice even if two requests arrive together.
        token.setUsedAt(now);
        tokenRepository.save(token);
        return Optional.of(token.getUserId());
    }

    /**
     * Whether a token is currently usable.
     *
     * @param plaintext the token from the link
     * @param purpose   the purpose the token must have
     * @param now       the current time
     * @return {@code true} when the token would be accepted
     */
    @Transactional(readOnly = true)
    public boolean isUsable(String plaintext, AuthTokenPurpose purpose, Instant now) {
        if (plaintext == null || plaintext.isBlank()) {
            return false;
        }
        return tokenRepository.findByTokenHash(hash(plaintext))
                .filter(token -> token.getPurpose() == purpose)
                .filter(token -> token.isUsableAt(now))
                .isPresent();
    }

    /**
     * Hashes a token.
     *
     * @param plaintext the token
     * @return the hexadecimal SHA-256 hash, 64 characters
     */
    public static String hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is required of every Java platform.
            throw new IllegalStateException("SHA-256 is not available on this platform.", impossible);
        }
    }
}
