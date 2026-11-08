package com.loveproof.testimonials.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loveproof.testimonials.config.StripeProperties;
import com.loveproof.testimonials.exception.BillingNotConfiguredException;

import jakarta.annotation.PostConstruct;

/**
 * The Stripe REST API, spoken over plain HTTP.
 *
 * <p>Stripe's API is a JSON-over-HTTP API with a form-encoded request body.
 * Six calls are needed for the whole billing feature, and implementing them
 * directly is smaller and clearer than an SDK: the request shapes are
 * written out where they can be read, and there is no unmanaged transitive
 * dependency to keep up to date.
 *
 * <p>Every method checks the configuration first. That is what makes "billing
 * is not configured" a behaviour of the configuration rather than of the
 * absence of a bean, so the answer is the same whether or not a test swaps
 * in a different implementation.
 */
@Component
public class HttpStripeGateway implements StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpStripeGateway.class);

    /** How long to wait for Stripe before giving up. */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    /** The form encoding Stripe expects. */
    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    private final StripeProperties properties;

    private final ObjectMapper mapper;

    private final HttpClient httpClient;

    /**
     * Creates the gateway.
     *
     * @param properties the Stripe configuration
     * @param mapper     the JSON reader
     */
    @Autowired
    public HttpStripeGateway(StripeProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /**
     * Creates the gateway with a given HTTP client.
     *
     * <p>Separated out so a test can supply a client that answers locally
     * instead of reaching Stripe.
     *
     * @param properties the Stripe configuration
     * @param mapper     the JSON reader
     * @param httpClient the HTTP client
     */
    HttpStripeGateway(StripeProperties properties, ObjectMapper mapper, HttpClient httpClient) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    /**
     * Checks the configuration once the properties have been bound.
     *
     * <p>The application starts either way. An installation without Stripe
     * keys is a working installation with no billing, and refusing to start
     * would take the whole product down for a missing optional feature.
     */
    @PostConstruct
    void reportConfiguration() {
        if (properties.isConfigured()) {
            log.info("Stripe is configured; the billing endpoints are live.");
        } else {
            log.warn(
                    "Stripe is not configured, so the billing endpoints will answer "
                            + "BILLING_NOT_CONFIGURED. Everything else works as usual.");
        }
    }

    @Override
    public String createCustomer(String email, String name) {
        requireConfigured();

        JsonNode response = post("/v1/customers", form(
                "email", email,
                "name", name));

        return requireText(response, "id");
    }

    @Override
    public String createCheckoutSession(
            String customerId,
            String priceId,
            String accountId,
            String successUrl,
            String cancelUrl) {

        requireConfigured();

        JsonNode response = post("/v1/checkout/sessions", form(
                "mode", "subscription",
                "customer", customerId,
                "line_items[0][price]", priceId,
                "line_items[0][quantity]", "1",
                "success_url", successUrl,
                "cancel_url", cancelUrl,
                // The account's identifier travels with the subscription, so a
                // webhook for it can find its way home even before the
                // subscription identifier has been stored.
                "subscription_data[metadata][account_id]", accountId));

        return requireText(response, "url");
    }

    @Override
    public String createPortalSession(String customerId, String returnUrl) {
        requireConfigured();

        JsonNode response = post("/v1/billing_portal/sessions", form(
                "customer", customerId,
                "return_url", returnUrl));

        return requireText(response, "url");
    }

    @Override
    public SubscriptionSnapshot retrieveSubscription(String subscriptionId) {
        requireConfigured();

        JsonNode response = get("/v1/subscriptions/" + encode(subscriptionId));

        String id = text(response, "id");
        String status = text(response, "status");
        boolean cancelAtPeriodEnd = response.path("cancel_at_period_end").asBoolean(false);
        Instant periodEnd = null;
        long epoch = response.path("current_period_end").asLong(0L);
        if (epoch > 0) {
            periodEnd = Instant.ofEpochSecond(epoch);
        }

        return new SubscriptionSnapshot(id, status, cancelAtPeriodEnd, periodEnd);
    }

    @Override
    public void cancelSubscription(String subscriptionId) {
        requireConfigured();

        post("/v1/subscriptions/" + encode(subscriptionId),
                form("cancel_at_period_end", "true"));
    }

    @Override
    public void resumeSubscription(String subscriptionId) {
        requireConfigured();

        post("/v1/subscriptions/" + encode(subscriptionId),
                form("cancel_at_period_end", "false"));
    }

    /**
     * Refuses the call when Stripe is not configured.
     */
    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new BillingNotConfiguredException(
                    "Billing is not configured on this installation, so the plan cannot be changed.");
        }
    }

    /**
     * Sends a POST request with a form body.
     *
     * @param path the path, relative to the API's base URL
     * @param body the form fields
     * @return the parsed response
     */
    private JsonNode post(String path, String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getApiBaseUrl() + path))
                .header("Authorization", "Bearer " + properties.getSecretKey())
                .header("Content-Type", CONTENT_TYPE)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return send(request);
    }

    /**
     * Sends a GET request.
     *
     * @param path the path, relative to the API's base URL
     * @return the parsed response
     */
    private JsonNode get(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getApiBaseUrl() + path))
                .header("Authorization", "Bearer " + properties.getSecretKey())
                .timeout(TIMEOUT)
                .GET()
                .build();

        return send(request);
    }

    /**
     * Sends a request and reads the response.
     *
     * @param request the request
     * @return the parsed response
     */
    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Stripe could not be reached.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The call to Stripe was interrupted.", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // The body is logged rather than returned: it can contain the
            // secret key in an error message, and the caller has no use for
            // Stripe's wording.
            log.error("Stripe answered {} for {}. The body was: {}",
                    response.statusCode(), request.uri().getPath(), response.body());
            throw new IllegalStateException(
                    "Stripe refused the request with status " + response.statusCode() + ".");
        }

        try {
            return mapper.readTree(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Stripe's answer could not be read.", ex);
        }
    }

    /**
     * Builds a form-encoded body.
     *
     * @param fields the fields, in pairs
     * @return the encoded body
     */
    private static String form(String... fields) {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < fields.length; index += 2) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(encode(fields[index])).append('=').append(encode(fields[index + 1]));
        }
        return body.toString();
    }

    /**
     * Percent-encodes a value for a form body or a path segment.
     *
     * @param value the value
     * @return the encoded value
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Reads a required field from a response.
     *
     * @param response the parsed response
     * @param field    the field's name
     * @return the field's value
     */
    private static String requireText(JsonNode response, String field) {
        String value = text(response, field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Stripe's answer did not include '" + field + "'.");
        }
        return value;
    }

    /**
     * Reads a field from a response.
     *
     * @param response the parsed response
     * @param field    the field's name
     * @return the field's value, or {@code null}
     */
    private static String text(JsonNode response, String field) {
        JsonNode value = response.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
