package com.loveproof.testimonials.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Builds signed Stripe deliveries for the webhook tests.
 *
 * <p>The tests need to post a body that carries a signature Stripe would
 * have produced. Rather than reaching into the production verifier -- which
 * would make the tests agree with the code by construction -- this computes
 * the signature independently, from the published rule: the signed payload is
 * {@code <timestamp>.<body>}, and the signature is its HMAC-SHA256 under the
 * signing secret.
 *
 * <p>That independence is the point. A bug in the verifier cannot hide
 * behind a test that used the verifier to build its input.
 */
public final class WebhookDelivery {

    /** The signing secret the tests sign with. */
    public static final String SECRET = "whsec_test_secret";

    private WebhookDelivery() {
        // A holder for the static helpers.
    }

    /**
     * Signs a body the way Stripe does.
     *
     * @param body the exact bytes of the body
     * @return the {@code Stripe-Signature} header value
     */
    public static String sign(byte[] body) {
        return sign(body, Instant.now().getEpochSecond());
    }

    /**
     * Signs a body with an explicit timestamp.
     *
     * @param body      the exact bytes of the body
     * @param timestamp the delivery's timestamp, in seconds
     * @return the {@code Stripe-Signature} header value
     */
    public static String sign(byte[] body, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);

            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return "t=" + timestamp + ",v1=" + hex;
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("The platform cannot compute an HMAC-SHA256.", ex);
        }
    }

    /**
     * Signs a body with a different secret, to forge a delivery.
     *
     * @param body   the exact bytes of the body
     * @param secret the secret to sign with
     * @return the {@code Stripe-Signature} header value
     */
    public static String signWith(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(Long.toString(Instant.now().getEpochSecond()).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);

            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return "t=" + Instant.now().getEpochSecond() + ",v1=" + hex;
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("The platform cannot compute an HMAC-SHA256.", ex);
        }
    }

    /**
     * Builds a subscription event body.
     *
     * @param eventId          the event's identifier
     * @param subscriptionId   the subscription the event is about
     * @param status           Stripe's subscription status
     * @param cancelAtPeriodEnd whether the subscription ends at the close of
     *                          the paid period
     * @param periodEnd        when the paid period ends, in seconds
     * @return the body
     */
    public static byte[] subscriptionEvent(
            String eventId,
            String subscriptionId,
            String status,
            boolean cancelAtPeriodEnd,
            long periodEnd) {

        String body = "{"
                + "\"id\":\"" + eventId + "\","
                + "\"type\":\"customer.subscription.updated\","
                + "\"data\":{\"object\":{"
                + "\"id\":\"" + subscriptionId + "\","
                + "\"status\":\"" + status + "\","
                + "\"cancel_at_period_end\":" + cancelAtPeriodEnd + ","
                + "\"current_period_end\":" + periodEnd
                + "}}}";
        return body.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Builds a Checkout Session completed event body.
     *
     * @param eventId        the event's identifier
     * @param subscriptionId the subscription the session names
     * @return the body
     */
    public static byte[] checkoutEvent(String eventId, String subscriptionId) {
        String body = "{"
                + "\"id\":\"" + eventId + "\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{"
                + "\"id\":\"cs_test\","
                + "\"subscription\":\"" + subscriptionId + "\""
                + "}}}";
        return body.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Builds a deleted-subscription event body.
     *
     * @param eventId        the event's identifier
     * @param subscriptionId the subscription that ended
     * @return the body
     */
    public static byte[] deletedEvent(String eventId, String subscriptionId) {
        String body = "{"
                + "\"id\":\"" + eventId + "\","
                + "\"type\":\"customer.subscription.deleted\","
                + "\"data\":{\"object\":{"
                + "\"id\":\"" + subscriptionId + "\","
                + "\"status\":\"canceled\""
                + "}}}";
        return body.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Builds an event body of an arbitrary type.
     *
     * @param eventId   the event's identifier
     * @param eventType the event's type
     * @return the body
     */
    public static byte[] eventOfType(String eventId, String eventType) {
        String body = "{"
                + "\"id\":\"" + eventId + "\","
                + "\"type\":\"" + eventType + "\","
                + "\"data\":{\"object\":{}}}";
        return body.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Whether two byte arrays are equal.
     *
     * @param left  the first array
     * @param right the second array
     * @return whether they are equal
     */
    public static boolean sameBytes(byte[] left, byte[] right) {
        return MessageDigest.isEqual(left, right);
    }
}
