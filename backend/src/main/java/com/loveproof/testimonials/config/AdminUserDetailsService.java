package com.loveproof.testimonials.config;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.domain.AdminUser;
import com.loveproof.testimonials.repository.AdminUserRepository;

/**
 * Loads administrators from the {@code admin_user} table.
 *
 * <p>The password is returned only as its BCrypt hash, which Spring Security's
 * {@code DaoAuthenticationProvider} compares against the submitted password. The hash is never
 * logged and never returned by an endpoint.
 */
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    /**
     * Creates the service.
     *
     * @param adminUserRepository the administrator store
     */
    public AdminUserDetailsService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser admin = adminUserRepository.findByUsername(username.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown administrator."));

        return User.withUsername(admin.getUsername())
                .password(admin.getPasswordHash())
                .roles(admin.getRole().name())
                .disabled(!admin.isEnabled())
                .build();
    }
}
