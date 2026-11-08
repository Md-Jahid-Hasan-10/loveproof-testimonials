package com.loveproof.testimonials.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.domain.AdminUser;
import com.loveproof.testimonials.repository.AdminUserRepository;

/**
 * Administrator authentication.
 *
 * <p>The service owns the two things the security layer cannot do on its own: it records the
 * login timestamp, and it resolves the acting administrator's identity so the moderation actions
 * can be attributed to a real account in the audit log.
 */
@Service
public class AdminSessionService {

    private final AdminUserRepository adminUserRepository;
    private final AuthenticationManager authenticationManager;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param adminUserRepository the administrator store
     * @param authenticationManager the authentication manager
     * @param clock the clock, injected so tests are deterministic
     */
    public AdminSessionService(
            AdminUserRepository adminUserRepository,
            AuthenticationManager authenticationManager,
            Clock clock) {
        this.adminUserRepository = adminUserRepository;
        this.authenticationManager = authenticationManager;
        this.clock = clock;
    }

    /**
     * Authenticates an administrator.
     *
     * <p>On success the security context is populated and the login timestamp is recorded. The
     * caller persists the context into the HTTP session.
     *
     * @param username the submitted username
     * @param password the submitted password
     * @return the authenticated administrator
     * @throws AuthenticationException when the credentials are wrong or the account is disabled
     */
    @Transactional
    public AdminUser authenticate(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String authenticatedUsername = authentication.getName();
        Optional<AdminUser> admin = adminUserRepository.findByUsername(authenticatedUsername);
        admin.ifPresent(found -> found.recordLogin(clock.instant()));

        return admin.orElseThrow(() -> new IllegalStateException(
                "The authenticated administrator could not be loaded."));
    }

    /**
     * Reads the administrator behind the current security context.
     *
     * @return the acting administrator, or empty when the context is anonymous
     */
    @Transactional(readOnly = true)
    public Optional<AdminUser> currentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails details) {
            return adminUserRepository.findByUsername(details.getUsername());
        }
        return Optional.empty();
    }

    /**
     * The acting administrator's identifier.
     *
     * @return the identifier, or {@code null} when there is no authenticated administrator
     */
    @Transactional(readOnly = true)
    public UUID currentAdminId() {
        return currentAdmin().map(AdminUser::getId).orElse(null);
    }

    /**
     * The acting administrator's username, as recorded in the audit log.
     *
     * @return the username, or {@code null} when there is no authenticated administrator
     */
    @Transactional(readOnly = true)
    public String currentUsername() {
        return currentAdmin().map(AdminUser::getUsername).orElse(null);
    }
}
