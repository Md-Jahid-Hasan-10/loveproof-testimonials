package com.loveproof.testimonials.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Verifies a Stripe webhook signature.
 *
 * <p>Stripe signs each delivery and puts the signature in the
 * {@code Stripe-Signature} header, which looks like
 * {@code t=1614556800,v1=abcdef...}. The signed payload is the timestamp, a
 * dot, and the exact bytes of the body -- {@code 1614556800.<body>}. The
 * signature is the HMAC-SHA256 of that, keyed with the webhook's signing
 * secret.
 *
 * <p>Three things are checked:
 *
 * <ul>
 *   <li>The signature matches. The comparison is constant-time, so a caller
 *       cannot learn the expected signature one byte at a time.</li>
 *   <li>The timestamp is recent. A delivery captured months ago and replayed
 *       would otherwise still carry a valid signature, because the signature
 *       covers the timestamp rather than the age of the delivery.</li>
 *   <li>The signature covers the body that actually arrived. The body is
 *       compared byte for byte, because a body that is decoded and
 *       re-encoded is not necessarily the same bytes.</li>
 * </ul>
 *
 * <p>This is deliberately a plain object with no Spring annotations on the
 * verification itself: it takes the secret and the clock, and answers a
 * question. That makes it testable against a pinned signature with no
 * network and nothing to mock.
 */
@Component
public class StripeSignatureVerifier {

    /** How old a delivery may be before it is refused. */
    static final Duration TOLERANCE = Duration.ofMinutes(5);

    /** The algorithm the signature is computed with. */
    private static final String ALGORITHM = "HmacSHA256";

    /** The name of the parameter carrying the timestamp. */
    private static final String TIMESTAMP_PARAMETER = "t";

    /** The name of the parameter carrying the signatures. */
    private static final String SIGNATURE_PARAMETER = "v1";

    private final Clock clock;

    /**
     * Creates the verifier.
     *
     * @param clock the clock, used to judge how old a delivery is
     */
    public StripeSignatureVerifier(Clock clock) {
        this.clock = clock;
    }

    /**
     * Whether a delivery carries a valid Stripe signature.
     *
     * @param payload the exact bytes of the request body
     * @param header  the {@code Stripe-Signature} header
     * @param secret  the webhook's signing secret
     * @return whether the delivery may be acted on
     */
    public boolean isValid(byte[] payload, String header, String secret) {
        if (payload == null || header == null || secret == null || secret.isBlank()) {
            return false;
        }

        Long timestamp = readTimestamp(header);
        if (timestamp == null) {
            return false;
        }

        List<String> signatures = readSignatures(header);
        if (signatures.isEmpty()) {
            return false;
        }

        if (isExpired(timestamp)) {
            return false;
        }

        String expected = sign(payload, timestamp, secret);
        // Any one matching signature is enough: Stripe sends more than one
        // while it rolls its signing secret over, and the old one stays valid
        // for a while.
        for (String candidate : signatures) {
            if (constantTimeEquals(expected, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a delivery is too old to act on.
     *
     * @param timestamp the delivery's timestamp, in seconds
     * @return whether the delivery has expired
     */
    boolean isExpired(long timestamp) {
        Instant delivered = Instant.ofEpochSecond(timestamp);
        Instant now = clock.instant();

        // A timestamp from the future is refused too. It cannot have been
        // signed by Stripe, and accepting it would let a caller choose the
        // timestamp that the signature covers.
        return delivered.isBefore(now.minus(TOLERANCE)) || delivered.isAfter(now.plus(TOLERANCE));
    }

    /**
     * Signs a payload the way Stripe does.
     *
     * @param payload   the exact bytes of the body
     * @param timestamp the delivery's timestamp, in seconds
     * @param secret    the webhook's signing secret
     * @return the lowercase hexadecimal signature
     */
    String sign(byte[] payload, long timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));

            mac.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(payload);

            return toHex(mac.doFinal());
        } catch (java.security.GeneralSecurityException ex) {
            // HMAC-SHA256 is required of every JRE, so this cannot happen.
            // It is wrapped rather than allowed to escape as a checked
            // exception the callers would have to handle for no reason.
            throw new IllegalStateException("The platform cannot compute an HMAC-SHA256.", ex);
        }
    }

    /**
     * Reads the timestamp out of the header.
     *
     * @param header the {@code Stripe-Signature} header
     * @return the timestamp in seconds, or {@code null} when there is none
     */
    private Long readTimestamp(String header) {
        for (String part : header.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(TIMESTAMP_PARAMETER + "=")) {
                try {
                    return Long.parseLong(trimmed.substring(trimmed.indexOf('=') + 1).trim());
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Reads the signatures out of the header.
     *
     * @param header the {@code Stripe-Signature} header
     * @return the signatures
     */
    private List<String> readSignatures(String header) {
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(SIGNATURE_PARAMETER + "=")) {
                String value = trimmed.substring(trimmed.indexOf('=') + 1).trim();
                if (!value.isEmpty()) {
                    signatures.add(value);
                }
            }
        }
        return signatures;
    }

    /**
     * Compares two signatures without leaking how far they match.
     *
     * @param expected the signature this installation computed
     * @param candidate the signature that arrived
     * @return whether they are equal
     */
    private boolean constantTimeEquals(String expected, String candidate) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Renders bytes as lowercase hexadecimal.
     *
     * @param bytes the bytes
     * @return the hexadecimal text
     */
    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
