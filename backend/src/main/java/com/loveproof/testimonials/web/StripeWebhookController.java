package com.loveproof.testimonials.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.service.WebhookService;

/**
 * The Stripe webhook endpoint.
 *
 * <p>The body is taken as {@code byte[]} rather than as a string, because the
 * signature is computed over the exact bytes Stripe sent. A body that is
 * decoded and re-encoded is byte-identical only for valid UTF-8, so a
 * string body would reject a delivery whose payload happened to contain
 * something else.
 */
@RestController
public class StripeWebhookController {

    private final WebhookService webhookService;

    /**
     * Creates the controller.
     *
     * @param webhookService the webhook rules
     */
    public StripeWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Receives a delivery.
     *
     * <p>The answer is 200 for anything that was processed, including a
     * replay and an event this installation does not act on: Stripe should
     * stop retrying an event that has already been dealt with. A delivery
     * whose signature does not verify is a 400, and nothing is written.
     *
     * @param payload   the exact bytes of the body
     * @param signature the {@code Stripe-Signature} header
     * @return a 200
     */
    @PostMapping("/api/billing/webhook")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {

        webhookService.process(payload, signature);
        return ResponseEntity.ok().build();
    }
}
