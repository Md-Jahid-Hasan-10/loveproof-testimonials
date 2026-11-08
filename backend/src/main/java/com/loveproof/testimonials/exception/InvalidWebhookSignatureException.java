package com.loveproof.testimonials.exception;

/**
 * Thrown when a webhook delivery does not carry a valid Stripe signature.
 *
 * <p>An unverified delivery is never acted on. The signature is the only
 * thing that distinguishes a genuine event from a request anybody could
 * have forged, so a delivery that fails the check is refused outright.
 */
public class InvalidWebhookSignatureException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the message shown to the caller
     */
    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
