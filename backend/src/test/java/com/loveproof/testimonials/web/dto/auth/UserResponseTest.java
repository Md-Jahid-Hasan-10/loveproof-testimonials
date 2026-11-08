package com.loveproof.testimonials.web.dto.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.AuthProvider;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the account projection sent to the client.
 *
 * <p>The field that matters here is {@code hasPassword}. The account settings
 * page offers the password form only when there is one, so a Google-only
 * account must not be shown a form that could not work. The password hash is
 * never sent either way.
 */
class UserResponseTest extends PostgresIntegrationTest {

    @Autowired
    private AppUserRepository userRepository;

    @BeforeEach
    void clearTheAccounts() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("a password account reports that it has a password")
    void aPasswordAccountHasAPassword() {
        AppUser user = userRepository.save(
                new AppUser("ada@example.com", "a-hash", "Ada"));

        assertThat(UserResponse.from(user).hasPassword()).isTrue();
    }

    @Test
    @DisplayName("a Google-only account reports that it has no password")
    void aGoogleOnlyAccountHasNoPassword() {
        AppUser user = new AppUser("grace@example.com", null, "Grace");
        user.setAuthProvider(AuthProvider.GOOGLE);
        user = userRepository.save(user);

        assertThat(UserResponse.from(user).hasPassword()).isFalse();
    }

    @Test
    @DisplayName("an account that joined Google later keeps its password")
    void anAccountThatJoinedGoogleLaterKeepsItsPassword() {
        // OPEN-A: signing in with Google using an address that already has a
        // password account joins that account rather than replacing it, so
        // the account still has the password it registered with.
        AppUser user = userRepository.save(
                new AppUser("ada@example.com", "a-hash", "Ada"));
        user.setGoogleSubject("google-subject-2");
        user = userRepository.save(user);

        assertThat(UserResponse.from(user).hasPassword()).isTrue();
    }

    @Test
    @DisplayName("the password hash is never sent to the client")
    void thePasswordHashIsNeverSent() {
        AppUser user = userRepository.save(
                new AppUser("ada@example.com", "a-hash", "Ada"));

        // The hash is not one of the record's fields, so it cannot leak
        // through the projection.
        assertThat(UserResponse.from(user).toString())
                .doesNotContain("a-hash")
                .doesNotContain("passwordHash");
    }
}
