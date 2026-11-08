package com.loveproof.testimonials.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Stripe configuration.
 *
 * <p>Nothing here is a constant that could be baked in: the secret key, the
 * webhook secret and the price identifier are all specific to one Stripe
 * account, so they are configuration rather than code.
 *
 * <p>The properties are deliberately allowed to be blank. An installation
 * without Stripe keys is a working installation -- everything except the
 * billing endpoints still works -- and the billing endpoints say so rather
 * than failing obscurely.
 */
@ConfigurationProperties(prefix = "loveproof.stripe")
public class StripeProperties {

    /** The Stripe secret API key. Never reaches the browser. */
    private String secretKey = "";

    /** The signing secret of the webhook endpoint. */
    private String webhookSecret = "";

    /** The price identifier of the Premium plan. */
    private String premiumPriceId = "";

    /** The Stripe API's base URL. */
    private String apiBaseUrl = "https://api.stripe.com";

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getPremiumPriceId() {
        return premiumPriceId;
    }

    public void setPremiumPriceId(String premiumPriceId) {
        this.premiumPriceId = premiumPriceId;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    /**
     * Whether the billing endpoints can do anything at all.
     *
     * <p>All three keys are required: the secret key to call Stripe, the
     * webhook secret to verify what Stripe sends, and the price identifier
     * to know what is being bought. A partial configuration is treated as no
     * configuration, because half a billing integration is a billing
     * integration that fails in a confusing way.
     *
     * @return whether Stripe is configured
     */
    public boolean isConfigured() {
        return !secretKey.isBlank()
                && !webhookSecret.isBlank()
                && !premiumPriceId.isBlank();
    }
}
