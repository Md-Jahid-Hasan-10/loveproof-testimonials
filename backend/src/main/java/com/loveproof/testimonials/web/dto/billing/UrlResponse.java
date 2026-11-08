package com.loveproof.testimonials.web.dto.billing;

/**
 * A URL the client is to go to.
 *
 * <p>Used for the Checkout Session and the billing portal: in both cases the
 * browser is sent to Stripe, so the server's answer is a URL rather than a
 * redirect it performs itself.
 *
 * @param url the URL
 */
public record UrlResponse(String url) {
}
