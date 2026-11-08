package com.loveproof.testimonials.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * The security configuration for the customer surface.
 *
 * <p>This is a **second filter chain**, matched on {@code /api/app/**}, with
 * its own authentication manager and its own user store. It is deliberately
 * separate from the installation's own admin chain rather than another set of
 * rules inside it, for three reasons:
 *
 * <ul>
 *   <li>The two surfaces mean different things by "signed in". An
 *       administrator runs the installation; a customer owns their own
 *       Spaces. Sharing one chain would let a rule meant for one surface
 *       accidentally cover the other.</li>
 *   <li>They use different user stores -- {@code admin_user} and
 *       {@code app_user} -- with different tables, different columns and
 *       different lifecycles.</li>
 *   <li>A customer session and an administrator session are different things
 *       with different consequences. Keeping them apart makes it structurally
 *       impossible for one to be accepted where the other is expected.</li>
 * </ul>
 *
 * <p>The chain is ordered first, so it is consulted before the admin chain
 * for the paths it matches.
 */
@Configuration
public class AppSecurityConfig {

    /**
     * The security context repository for the customer surface.
     *
     * <p>It uses a different session attribute from the installation's own
     * repository. Two surfaces that share one attribute would share one
     * signed-in state, and a customer session would then be readable as an
     * administrator session. Separate attributes make that structurally
     * impossible rather than merely unlikely.
     *
     * @return the repository
     */
    @Bean
    public SecurityContextRepository appSecurityContextRepository() {
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        repository.setSpringSecurityContextKey("LOVEPROOF_APP_SECURITY_CONTEXT");
        return repository;
    }

    /**
     * The filter chain for the customer surface.
     *
     * @param http                     the builder
     * @param clock                    the clock, for the error timestamps
     * @param appUserDetailsService    the customer store
     * @param passwordEncoder          the password hasher
     * @param appSecurityContextRepository where the signed-in state is kept
     * @param appAuthenticationManager  the manager that checks customer credentials
     * @param secureCookies            whether the cookies carry {@code Secure}
     * @return the configured chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    @Order(1)
    public SecurityFilterChain appSecurityFilterChain(
            HttpSecurity http,
            Clock clock,
            AppUserDetailsService appUserDetailsService,
            PasswordEncoder passwordEncoder,
            SecurityContextRepository appSecurityContextRepository,
            AuthenticationManager appAuthenticationManager,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies)
            throws Exception {

        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secureCookies));
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http
            .securityMatcher("/api/app/**")
            // Customer credentials are checked against the `app_user` table,
            // never against the administrator accounts.
            .authenticationManager(appAuthenticationManager)
            .sessionManagement(session -> session
                    .sessionFixation(fixation -> fixation.migrateSession())
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .securityContext(context -> context
                    .securityContextRepository(appSecurityContextRepository))
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(csrfRequestHandler)
                    // The anonymous endpoints cannot carry a token: the browser
                    // has not been given one yet, and there is no session for
                    // a token to protect. This list must match the `permitAll`
                    // list below exactly -- an endpoint that is reachable
                    // without a session and is not exempt here would answer
                    // 403 to a legitimate first-time visitor.
                    //
                    // Sign-in and register are protected instead by the
                    // session-id regeneration on authentication. Forgot-password
                    // and reset-password carry a single-use token in the body,
                    // so there is nothing for a forged request to gain.
                    .ignoringRequestMatchers("/api/app/auth/sign-in")
                    .ignoringRequestMatchers("/api/app/auth/register")
                    .ignoringRequestMatchers("/api/app/auth/forgot-password")
                    .ignoringRequestMatchers("/api/app/auth/reset-password")
                    .ignoringRequestMatchers("/api/app/auth/verify-email")
                    // The public Space pages and the public submission endpoint
                    // are anonymous, so there are no credentials for CSRF to
                    // protect. They are guarded by rate limiting and the
                    // honeypot field instead.
                    .ignoringRequestMatchers("/api/app/public/**"))
            .authorizeHttpRequests(auth -> auth
                    // Reachable without a session. Every one of these is also
                    // exempt from CSRF above.
                    .requestMatchers(HttpMethod.POST, "/api/app/auth/register").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/app/auth/sign-in").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/app/auth/forgot-password").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/app/auth/reset-password").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/app/auth/verify-email").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/app/auth/verify-email").permitAll()
                    // The public Space surface: the form, the submission, the
                    // embed data.
                    .requestMatchers("/api/app/public/**").permitAll()
                    // Everything else needs a signed-in customer.
                    .anyRequest().hasRole("USER"))
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(new JsonAuthenticationEntryPoint(clock))
                    .accessDeniedHandler(new JsonAccessDeniedHandler(clock)))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .headers(headers -> headers
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(Customizer.withDefaults()))
            .addFilterBefore(new CsrfCookieFilter(csrfTokenRepository), BasicAuthenticationFilter.class);

        return http.build();
    }

    /**
     * The authentication manager for the customer surface.
     *
     * @param appUserDetailsService the customer store
     * @param passwordEncoder       the password hasher
     * @return the authentication manager
     */
    @Bean
    public AuthenticationManager appAuthenticationManager(
            AppUserDetailsService appUserDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(appUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // A missing account and a wrong password produce the same message, so
        // nothing leaks about which addresses are registered.
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }
}