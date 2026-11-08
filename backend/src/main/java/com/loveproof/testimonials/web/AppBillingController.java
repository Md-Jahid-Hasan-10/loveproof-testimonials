package com.loveproof.testimonials.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.config.CurrentUser;
import com.loveproof.testimonials.service.BillingService;
import com.loveproof.testimonials.service.CheckoutService;
import com.loveproof.testimonials.web.dto.billing.BillingResponse;
import com.loveproof.testimonials.web.dto.billing.UrlResponse;

/**
 * The customer's own billing.
 *
 * <p>None of these endpoints changes a plan. The checkout creates a session
 * and the cancel and resume ask Stripe to do something; in every case the
 * plan changes only when the webhook says so. That is the whole point of the
 * design: a customer who reaches the success URL has not paid anything yet
 * as far as this server is concerned, so treating the redirect as a purchase
 * would let anybody upgrade themselves by visiting a URL.
 */
@RestController
@RequestMapping("/api/app/billing")
public class AppBillingController {

    private final BillingService billingService;

    private final CheckoutService checkoutService;

    /**
     * Creates the controller.
     *
     * @param billingService  the billing read model
     * @param checkoutService the subscription actions
     */
    public AppBillingController(BillingService billingService, CheckoutService checkoutService) {
        this.billingService = billingService;
        this.checkoutService = checkoutService;
    }

    /**
     * Reads the signed-in account's billing state.
     *
     * @return the state
     */
    @GetMapping
    public BillingResponse read() {
        return BillingResponse.from(billingService.stateFor(CurrentUser.id()));
    }

    /**
     * Creates a Checkout Session for the Premium plan.
     *
     * @return the URL of the Checkout Session
     */
    @PostMapping("/checkout")
    public UrlResponse checkout() {
        return new UrlResponse(checkoutService.createCheckoutSession(CurrentUser.id()));
    }

    /**
     * Asks Stripe to cancel the subscription at the close of the paid period.
     *
     * <p>The plan stays Premium until Stripe reports the subscription as
     * ended. Nothing is written locally.
     *
     * @return a 204
     */
    @PostMapping("/cancel")
    public ResponseEntity<Void> cancel() {
        checkoutService.cancel(CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * Asks Stripe to keep the subscription running.
     *
     * @return a 204
     */
    @PostMapping("/resume")
    public ResponseEntity<Void> resume() {
        checkoutService.resume(CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * Opens the Stripe billing portal, where a customer manages their card.
     *
     * @return the URL of the portal session
     */
    @PostMapping("/portal")
    public UrlResponse portal() {
        return new UrlResponse(checkoutService.createPortalSession(CurrentUser.id()));
    }
}
