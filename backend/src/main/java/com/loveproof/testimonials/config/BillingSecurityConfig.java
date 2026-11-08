package com.loveproof.testimonials.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The security configuration for the billing surface.
 *
 * <p>This is a **third filter chain**, matched on {@code /api/billing/**}. It
 * exists rather than riding on the admin chain because the trust model is
 * different in kind:
 *
 * <ul>
 *   <li>The admin chain trusts a **session**. The billing chain trusts
 *       nothing but the request's own signature.</li>
 *   <li>The admin chain CSRF-checks its writes, because a session riding in a
 *       cookie can be made to send a request the user did not intend. The
 *       billing chain has no session and no cookie for a forged request to
 *       ride on, so CSRF has nothing to protect.</li>
 *   <li>The billing chain is stateless. A webhook delivery creates no
 *       session and reads none, so there is nothing for it to inherit.</li>
 * </ul>
 *
 * <p>The signature is verified inside the controller rather than in a filter,
 * so the refusal is rendered as the shared error envelope and carries the
 * same shape as every other failure in the API.
 */
@Configuration
public class BillingSecurityConfig {

    /**
     * The filter chain for the billing surface.
     *
     * @param http the builder
     * @return the configured chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    @Order(2)
    public SecurityFilterChain billingSecurityFilterChain(HttpSecurity http) throws Exception {

        http
            .securityMatcher("/api/billing/**")
            // The delivery is authenticated by its Stripe signature, not by a
            // session, so there is no session to create and none to read.
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // A forged request has no session to ride on, so there is nothing
            // for CSRF to protect. The signature is what identifies a genuine
            // delivery.
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }
}
