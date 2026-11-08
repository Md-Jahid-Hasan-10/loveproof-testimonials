package com.loveproof.testimonials.config;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.repository.AppUserRepository;

/**
 * Loads customers from the {@code app_user} table.
 *
 * <p>This is a separate service from the one that loads administrators, and
 * it is used by a separate filter chain. The two surfaces have different
 * meanings for "signed in": an administrator runs the installation, a customer
 * owns their own Spaces. Sharing one {@code UserDetailsService} would force
 * both into the same authority names and make it easy for one surface to
 * accept the other's session.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    /**
     * Creates the service.
     *
     * @param userRepository the account store
     */
    public AppUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // The password is the BCrypt hash. A Google-only account has none, so
        // it cannot sign in with a password -- the hash is a value that cannot
        // match any input.
        String storedHash = userRepository.findByEmailIgnoreCase(email.trim())
                .map(user -> user.getPasswordHash() == null
                        ? "{noop}no-password-for-this-account"
                        : user.getPasswordHash())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account."));

        return User.withUsername(email.trim().toLowerCase())
                .password(storedHash)
                .roles("USER")
                .disabled(false)
                .build();
    }
}
