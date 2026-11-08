package com.loveproof.testimonials.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Stripe signature check.
 *
 * <p>This is a plain unit test: no Spring, no database and no network. The
 * signature is checked against a **pinned vector** -- a secret, a payload and
 * a signature computed independently -- so the check cannot pass by agreeing
 * with itself. A bug that changed the signed bytes, the separator or the
 * algorithm would produce a different signature and fail here.
 */
class StripeSignatureVerifierTest {

    /** The secret the pinned vector was computed with. */
    private static final String SECRET = "whsec_test_secret";

    /** The body the pinned vector was computed with. */
    private static final String BODY =
            "{\"id\":\"evt_test\",\"type\":\"customer.subscription.updated\"}";

    /** The timestamp the pinned vector was computed with. */
    private static final long TIMESTAMP = 1614556800L;

    /**
     * The signature of {@code TIMESTAMP}.{@link #BODY} under {@link #SECRET}.
     *
     * <p>Computed independently of the code under test, so the two have to
     * agree for the right reason.
     */
    private static final String SIGNATURE =
            "3896302e2209f78b75316eb8400327c638dd500f66e06691fb72ca5e8bca59c7";

    /**
     * A clock fixed at the pinned timestamp.
     *
     * @return the clock
     */
    private static Clock clockAtTheTimestamp() {
        return Clock.fixed(Instant.ofEpochSecond(TIMESTAMP), ZoneOffset.UTC);
    }

    /**
     * The verifier under test.
     *
     * @param clock the clock
     * @return the verifier
     */
    private static StripeSignatureVerifier verifier(Clock clock) {
        return new StripeSignatureVerifier(clock);
    }

    @Test
    @DisplayName("the pinned signature is accepted")
    void thePinnedSignatureIsAccepted() {
        StripeSignatureVerifier verifier = verifier(clockAtTheTimestamp());

        assertThat(verifier.isValid(
                BODY.getBytes(StandardCharsets.UTF_8),
                "t=" + TIMESTAMP + ",v1=" + SIGNATURE,
                SECRET)).isTrue();
    }

    @Test
    @DisplayName("a signature made with a different secret is refused")
    void aSignatureMadeWithADifferentSecretIsRefused() {
        StripeSignatureVerifier verifier = verifier(clockAtTheTimestamp());

        assertThat(verifier.isValid(
                BODY.getBytes(StandardCharsets.UTF_8),
                "t=" + TIMESTAMP + ",v1=" + SIGNATURE,
                "whsec_a_different_secret")).isFalse();
    }

    @Test
    @DisplayName("a tampered body is refused")
    void aTamperedBodyIsRefused() {
        StripeSignatureVerifier verifier = verifier(clockAtTheTimestamp());

        String tampered = BODY.replace("customer.subscription.updated", "customer.subscription.deleted");

        assertThat(verifier.isValid(
                tampered.getBytes(StandardCharsets.UTF_8),
                "t=" + TIMESTAMP + ",v1=" + SIGNATURE,
                SECRET)).isFalse();
    }

    @Test
    @DisplayName("a body that differs by one byte is refused")
    void aBodyThatDiffersByOneByteIsRefused() {
        StripeSignatureVerifier verifier = verifier(clockAtTheTimestamp());

        byte[] payload = BODY.getBytes(StandardCharsets.UTF_8);
        byte[] tampered = payload.clone();
        tampered[tampered.length - 1] ^= 0x01;

        assertThat(verifier.isValid(
                tampered,
                "t=" + TIMESTAMP + ",v1=" + SIGNATURE,
                SECRET)).isFalse();
    }

    @Test
    @DisplayName("an expired timestamp is refused")
    void anExpiredTimestampIsRefused() {
        // The clock is six minutes past the delivery.
        Clock clock = Clock.fixed(Instant.ofEpochSecond(TIMESTAMP + 360), ZoneOffset.UTC);
        StripeSignatureVerifier verifier = verifier(clock);

        assertThat(verifier.isValid(
                BODY.getBytes(StandardCharsets.UTF_8),
                "t=" + TIMESTAMP + ",v1=" + SIGNATURE,
                SECRET)).isFalse();
    }

    @Test
    @DisplayName("a timestamp from the future is refused")
    void aTimestampFromTheFutureIsRefused() {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(TIMESTAMP - 360), ZoneOffset.UTC);
        StripeSignatureVerifier verifier = verifier(clock);

        assertThat(verifier.isValid(
                BODY.getBytes(StandardCharsets.UTF_8),
                "t=" + TIMESTAMP + ",v1=" + SIGNATURE,
                SECRET)).isFalse();
    }

    @Test
    @DisplayName("a timestamp just inside the tolerance is accepted")
    void aTimestampJustInsideTheToleranceIsAccepted() {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(TIMESTAMP + 299), ZoneOffset.UTC);
        StripeSignatureVerifier verifier = verifier(clock);

        assertThat(verifier.isValid(
                BODY.getBytes(StandardCharsets.UTF_8),
                "t=" + TIMESTAMP + ",v1=" + SIGNATURE,
                SECRET)).isTrue();
    }

    @Test
    @DisplayName("a header with two signatures is accepted when the second matches")
    void aHeaderWithTwoSignaturesIsAcceptedWhenTheSecondMatches() {
        StripeSignatureVerifier verifier = verifier(clockAtTheTimestamp());

        // Stripe sends two signatures while it rolls its signing secret over:
        // the one for the old secret and the one for the new.
        String header = "t=" + TIMESTAMP + ",v1=0000000000000000000000000000000000000000000000000000000000000000"
                + ",v1=" + SIGNATURE;

        assertThat(verifier.isValid(BODY.getBytes(StandardCharsets.UTF_8), header, SECRET)).isTrue();
    }

    @Test
    @DisplayName("a header with two signatures is refused when neither matches")
    void aHeaderWithTwoSignaturesIsRefusedWhenNeitherMatches() {
        StripeSignatureVerifier verifier = verifier(clockAtTheTimestamp());

        String header = "t=" + TIMESTAMP + ",v1=0000000000000000000000000000000000000000000000000000000000000000"
                + ",v1=1111111111111111111111111111111111111111111111111111111111111111";

        assertThat(verifier.isValid(BODY.getBytes(StandardCharsets.UTF_8), header, SECRET)).isFalse();
    }

    @Test
    @DisplayName("a missing header is refused")
    void aMissingHeaderIsRefused() {
        StripeSignatureVerifier verifier = verifier(clockAtTheTimestamp());

        assertThat(verifier.isValid(BODY.getBytes(StandardCharsets.UTF_8), null, SECRET)).isFalse();
    }

    @Test
    @DisplayName("a header with no timestamp is refused")
    void aHeaderWithNoTimestampIsRefused() {
        StripeSignatureVerifier verifier = verifier(clockAtTheTimestamp());

        assertThat(verifier.isValid(
                BODY.getBytes(StandardCharsets.UTF_8),
                "v1=" + SIGNATURE,
                SECRET)).isFalse();
    }

    @Test
    @DisplayName("a header with no signature is refused")
    void aHeaderWithNoSignatureIsRefused() {
        StripeSignatureVerifier verifier = verifier(clockAtTheTimestamp());

        assertThat(verifier.isValid(
                BODY.getBytes(StandardCharsets.UTF_8),
                "t=" + TIMESTAMP,
                SECRET)).isFalse();
    }

    @Test
    @DisplayName("a blank secret is refused")
    void aBlankSecretIsRefused() {
        StripeSignatureVerifier verifier = verifier(clockAtTheTimestamp());

        assertThat(verifier.isValid(
                BODY.getBytes(StandardCharsets.UTF_8),
                "t=" + TIMESTAMP + ",v1=" + SIGNATURE,
                "  ")).isFalse();
    }

    @Test
    @DisplayName("a body with non-ASCII characters is signed over its exact bytes")
    void aBodyWithNonAsciiCharactersIsSignedOverItsExactBytes() {
        // A body that is not valid UTF-8 would be changed by a decode and
        // re-encode, which is why the controller takes the body as bytes.
        String body = "{\"name\":\"Café Straße\"}";
        Clock clock = clockAtTheTimestamp();
        StripeSignatureVerifier verifier = verifier(clock);

        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String signature = verifier.sign(payload, TIMESTAMP, SECRET);

        assertThat(verifier.isValid(payload, "t=" + TIMESTAMP + ",v1=" + signature, SECRET)).isTrue();
        assertThat(verifier.isValid(
                payload,
                "t=" + TIMESTAMP + ",v1=" + signature,
                SECRET)).isTrue();
    }
}
