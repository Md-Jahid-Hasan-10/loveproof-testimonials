package com.loveproof.testimonials.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * The security configuration.
 *
 * <p>Three rules decide what is reachable:
 *
 * <ol>
 *   <li>Everything under {@code /api/admin/**} requires an authenticated session with the
 *       {@code ADMIN} role. A request without a session gets {@code 401}; a session without the
 *       role gets {@code 403}.</li>
 *   <li>Every state-changing request on the admin surface requires a CSRF token. The token is
 *       issued in a readable {@code XSRF-TOKEN} cookie and must be echoed in the
 *       {@code X-XSRF-TOKEN} header — the contract in {@code CLAUDE.md} §6.3. The login and the
 *       public submission endpoints are exempt: neither rides on an authenticated session, so
 *       neither has anything for CSRF to protect.</li>
 *   <li>The public API is open. It never exposes pending, rejected or deleted content.</li>
 * </ol>
 *
 * <p>Before Phase 3 the admin surface was gated behind {@code loveproof.admin.api.enabled} because
 * nothing protected it. That gate is now gone: this configuration is what protects it, and the
 * endpoints are always registered.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * The bcrypt encoder used for every stored password.
     *
     * <p>Cost 10 is the default and the minimum the data model allows.
     *
     * @return the encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The authentication manager.
     *
     * <p>Spring Security's {@code DaoAuthenticationProvider} is wired to the
     * {@link AdminUserDetailsService}, so credentials are checked against the BCrypt hash in the
     * {@code admin_user} table.
     *
     * @param userDetailsService the administrator store
     * @param passwordEncoder the bcrypt encoder
     * @return the authentication manager
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AdminUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // The default is "hide" for a missing user; the login endpoint returns one
        // generic message either way, so no distinction leaks to the client.
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    /**
     * The security context repository.
     *
     * <p>The login endpoint writes the authenticated context into the HTTP session through this
     * repository, so the browser stays signed in across requests.
     *
     * @return the repository
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * The filter chain.
     *
     * @param http the builder
     * @param clock the clock, used for the error timestamps
     * @param securityContextRepository the repository holding the security context
     * @param secureCookies whether the cookies carry the {@code Secure} attribute; the
     *                      {@code prod} profile sets this to {@code true}
     * @return the configured chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Clock clock,
            SecurityContextRepository securityContextRepository,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies)
            throws Exception {

        // The token is written to a readable cookie and echoed back in a header. The
        // "deferred" handler renders it eagerly, so the cookie is present on the very
        // first response rather than only after a form post.
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secureCookies));
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http
            // The API is stateless for the public surface and session-based for the
            // admin surface; the session is created on demand, never eagerly.
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation(fixation -> fixation.migrateSession()))

            // The security context lives in the HTTP session, so the login endpoint
            // can write it there and later requests find it.
            .securityContext(context -> context
                    .securityContextRepository(securityContextRepository))

            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(csrfRequestHandler)
                    // Two endpoints are exempt from the CSRF check.
                    //
                    // The login request cannot carry a token: the browser has not
                    // been given one yet. It is protected against login-CSRF by
                    // the session-id regeneration on authentication and by the
                    // token being issued on the response.
                    //
                    // The public submission endpoint is anonymous — there are no
                    // credentials for an attacker to ride on, so there is nothing
                    // for CSRF to protect. It is guarded instead by rate limiting
                    // and the duplicate-submission fingerprint.
                    .ignoringRequestMatchers("/api/admin/session")
                    .ignoringRequestMatchers(
                            new AntPathRequestMatcher("/api/testimonials", "POST")))

            .authorizeHttpRequests(auth -> auth
                    // The session endpoints come first: login must be reachable
                    // without a session, and logout must work for the session it
                    // is ending.
                    .requestMatchers(HttpMethod.POST, "/api/admin/session").permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/api/admin/session").hasRole("ADMIN")
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/health", "/actuator/**").permitAll()
                    .requestMatchers("/api/**").permitAll()
                    .anyRequest().permitAll())

            // An unauthenticated admin request answers 401 with the shared error
            // envelope rather than Spring's default HTML login redirect.
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(new JsonAuthenticationEntryPoint(clock))
                    .accessDeniedHandler(new JsonAccessDeniedHandler(clock)))

            // The admin API is a JSON API, not a browser form target: there is no
            // login page to redirect to and no HTTP Basic fallback.
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .headers(headers -> headers
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(Customizer.withDefaults()))
            .addFilterBefore(new CsrfCookieFilter(csrfTokenRepository), BasicAuthenticationFilter.class);

        return http.build();
    }
}
