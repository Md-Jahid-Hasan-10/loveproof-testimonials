package com.loveproof.testimonials.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.AuthProvider;
import com.loveproof.testimonials.domain.AuthTokenPurpose;
import com.loveproof.testimonials.exception.ConflictException;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.AuthTokenRepository;
import com.loveproof.testimonials.service.mail.MailSender;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * The account rules.
 *
 * <p>These tests exercise the real database, so the unique email index and
 * the token constraints are tested as they are rather than as they are
 * described.
 */
class AuthServiceTest extends PostgresIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AuthTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * The mail messages that would have been sent.
     */
    private List<SentMail> sentMail;

    /**
     * A stand-in for the mail sender that records what it was asked to send.
     */
    private RecordingMailSender recordingMailSender;

    @BeforeEach
    void clearTheAccounts() {
        userRepository.deleteAll();
        tokenRepository.deleteAll();
        sentMail = new java.util.ArrayList<>();
        recordingMailSender = new RecordingMailSender(sentMail);
        // The service is built by Spring with the real mail sender, so the
        // recording one is injected for the duration of this test.
        authService = new AuthService(userRepository, tokenService, recordingMailSender, passwordEncoder,
                java.time.Clock.systemUTC(), "http://localhost:5173");
    }

    @Test
    @DisplayName("registering creates an unverified account and sends a verification email")
    void registeringCreatesAnUnverifiedAccount() {
        AppUser user = authService.register("Ada@example.com", "a-good-password", "Ada");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("ada@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Ada");
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getPlan().name()).isEqualTo("FREE");
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.PASSWORD);

        assertThat(sentMail).hasSize(1);
        assertThat(sentMail.get(0).to()).isEqualTo("ada@example.com");
        assertThat(sentMail.get(0).body()).contains("/verify-email?token=");
    }

    @Test
    @DisplayName("the password is stored as a hash, never as the password itself")
    void thePasswordIsStoredAsAHash() {
        AppUser user = authService.register("ada@example.com", "a-good-password", "Ada");

        assertThat(user.getPasswordHash()).isNotEqualTo("a-good-password");
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("a-good-password", user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("registering with an email that is already taken is rejected")
    void registeringWithATakenEmailIsRejected() {
        authService.register("ada@example.com", "a-good-password", "Ada");

        assertThatThrownBy(() -> authService.register("ada@example.com", "another-password", "Ada"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        // The email is matched without regard to case, so this is the same
        // account and not a second one.
        assertThatThrownBy(() -> authService.register("ADA@EXAMPLE.COM", "another-password", "Ada"))
                .isInstanceOf(ConflictException.class);

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("signing in with the right password succeeds")
    void signingInWithTheRightPasswordSucceeds() {
        authService.register("ada@example.com", "a-good-password", "Ada");

        AppUser signedIn = authService.signIn("ada@example.com", "a-good-password");

        assertThat(signedIn.getEmail()).isEqualTo("ada@example.com");
        assertThat(signedIn.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("signing in is not case sensitive about the email")
    void signingInIsNotCaseSensitiveAboutTheEmail() {
        authService.register("ada@example.com", "a-good-password", "Ada");

        AppUser signedIn = authService.signIn("ADA@Example.com", "a-good-password");

        assertThat(signedIn.getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("a wrong password and an unknown email give the same message")
    void aWrongPasswordAndAnUnknownEmailGiveTheSameMessage() {
        authService.register("ada@example.com", "a-good-password", "Ada");

        Throwable wrongPassword = catchThrowable(() -> authService.signIn("ada@example.com", "not-the-password"));
        Throwable unknownEmail = catchThrowable(() -> authService.signIn("nobody@example.com", "not-the-password"));

        assertThat(wrongPassword).isInstanceOf(NotFoundException.class);
        assertThat(unknownEmail).isInstanceOf(NotFoundException.class);
        // The messages are identical, so the endpoint cannot be used to find
        // out which addresses are registered.
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage());
    }

    @Test
    @DisplayName("verifying an email with the emailed token marks the account verified")
    void verifyingWithTheTokenMarksTheAccountVerified() {
        AppUser user = authService.register("ada@example.com", "a-good-password", "Ada");
        String token = tokenFromTheLastEmail();

        authService.verifyEmail(token);

        AppUser verified = userRepository.findById(user.getId()).orElseThrow();
        assertThat(verified.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("a verification token cannot be used twice")
    void aVerificationTokenCannotBeUsedTwice() {
        authService.register("ada@example.com", "a-good-password", "Ada");
        String token = tokenFromTheLastEmail();

        authService.verifyEmail(token);

        assertThatThrownBy(() -> authService.verifyEmail(token))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not valid or has expired");
    }

    @Test
    @DisplayName("an unknown verification token is rejected")
    void anUnknownVerificationTokenIsRejected() {
        assertThatThrownBy(() -> authService.verifyEmail("this-token-was-never-issued"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("an expired verification token is rejected")
    void anExpiredVerificationTokenIsRejected() {
        AppUser user = authService.register("ada@example.com", "a-good-password", "Ada");

        // Issue the token, then age it past its deadline.
        String token = tokenService.issue(user.getId(), AuthTokenPurpose.VERIFY_EMAIL,
                Instant.now().minus(java.time.Duration.ofHours(48)));
        tokenRepository.saveAll(tokenRepository.findAll());

        assertThatThrownBy(() -> authService.verifyEmail(token))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not valid or has expired");
    }

    @Test
    @DisplayName("a reset token cannot be used to verify an email")
    void aResetTokenCannotBeUsedToVerifyAnEmail() {
        AppUser user = authService.register("ada@example.com", "a-good-password", "Ada");
        String resetToken = tokenService.issue(user.getId(), AuthTokenPurpose.RESET_PASSWORD, Instant.now());

        assertThatThrownBy(() -> authService.verifyEmail(resetToken))
                .isInstanceOf(NotFoundException.class);

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("requesting a reset sends an email with a reset link")
    void requestingAResetSendsAnEmail() {
        authService.register("ada@example.com", "a-good-password", "Ada");
        sentMail.clear();

        authService.requestPasswordReset("ada@example.com");

        assertThat(sentMail).hasSize(1);
        assertThat(sentMail.get(0).body()).contains("/reset-password?token=");
    }

    @Test
    @DisplayName("requesting a reset for an unknown email sends nothing and does not fail")
    void requestingAResetForAnUnknownEmailSendsNothing() {
        authService.requestPasswordReset("nobody@example.com");

        assertThat(sentMail).isEmpty();
    }

    @Test
    @DisplayName("resetting the password with a valid token changes it")
    void resettingWithAValidTokenChangesThePassword() {
        AppUser user = authService.register("ada@example.com", "a-good-password", "Ada");
        authService.requestPasswordReset("ada@example.com");
        String token = tokenFromTheLastEmail();

        authService.resetPassword(token, "a-brand-new-password");

        AppUser updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("a-brand-new-password", updated.getPasswordHash())).isTrue();
        assertThat(authService.signIn("ada@example.com", "a-brand-new-password")).isNotNull();
    }

    @Test
    @DisplayName("a reset token cannot be used twice")
    void aResetTokenCannotBeUsedTwice() {
        authService.register("ada@example.com", "a-good-password", "Ada");
        authService.requestPasswordReset("ada@example.com");
        String token = tokenFromTheLastEmail();

        authService.resetPassword(token, "a-brand-new-password");

        assertThatThrownBy(() -> authService.resetPassword(token, "another-password"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("changing the password requires the current one")
    void changingThePasswordRequiresTheCurrentOne() {
        AppUser user = authService.register("ada@example.com", "a-good-password", "Ada");

        assertThatThrownBy(() -> authService.changePassword(user.getId(), "not-the-password", "a-new-password"))
                .isInstanceOf(NotFoundException.class);

        authService.changePassword(user.getId(), "a-good-password", "a-new-password");
        assertThat(passwordEncoder.matches("a-new-password",
                userRepository.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("a first Google sign-in creates a verified account")
    void aFirstGoogleSignInCreatesAVerifiedAccount() {
        AppUser user = authService.signInWithGoogle("grace@example.com", "Grace", "google-subject-1");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.getGoogleSubject()).isEqualTo("google-subject-1");
        assertThat(user.getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("Google sign-in with an email that already has a password account joins that account")
    void googleSignInJoinsAnExistingAccount() {
        AppUser existing = authService.register("ada@example.com", "a-good-password", "Ada");

        AppUser signedIn = authService.signInWithGoogle("ada@example.com", "Ada Lovelace", "google-subject-2");

        // One account, not two.
        assertThat(signedIn.getId()).isEqualTo(existing.getId());
        assertThat(signedIn.getGoogleSubject()).isEqualTo("google-subject-2");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a Google account cannot sign in with a password")
    void aGoogleAccountCannotSignInWithAPassword() {
        authService.signInWithGoogle("grace@example.com", "Grace", "google-subject-1");

        assertThatThrownBy(() -> authService.signIn("grace@example.com", "anything-at-all"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("the display name can be changed")
    void theDisplayNameCanBeChanged() {
        AppUser user = authService.register("ada@example.com", "a-good-password", "Ada");

        authService.changeDisplayName(user.getId(), "Ada Lovelace");

        assertThat(userRepository.findById(user.getId()).orElseThrow().getDisplayName()).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("a display name defaults to the part of the email before the at sign")
    void aDisplayNameDefaultsToTheEmailLocalPart() {
        AppUser user = authService.register("ada@example.com", "a-good-password", null);

        assertThat(user.getDisplayName()).isEqualTo("Ada");
    }

    @Test
    @DisplayName("the token is stored as a hash, never as the token itself")
    void theTokenIsStoredAsAHash() {
        AppUser user = authService.register("ada@example.com", "a-good-password", "Ada");
        String token = tokenFromTheLastEmail();

        String stored = tokenRepository.findAll().stream()
                .filter(candidate -> candidate.getUserId().equals(user.getId()))
                .findFirst()
                .orElseThrow()
                .getTokenHash();

        assertThat(stored).isNotEqualTo(token);
        assertThat(stored).hasSize(64);
    }

    /**
     * Extracts the token from the link in the last email that was sent.
     *
     * @return the token
     */
    private String tokenFromTheLastEmail() {
        String body = sentMail.get(sentMail.size() - 1).body();
        int marker = body.indexOf("token=");
        int end = body.indexOf('\n', marker);
        if (end < 0) {
            end = body.length();
        }
        return body.substring(marker + "token=".length(), end).trim();
    }

    /**
     * Runs an action and returns whatever it threw.
     *
     * @param action the action
     * @return the exception
     */
    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected the action to throw.");
        } catch (AssertionError failure) {
            throw failure;
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    /**
     * A mail message that would have been sent.
     *
     * @param to      the recipient
     * @param subject the subject
     * @param body    the body
     */
    private record SentMail(String to, String subject, String body) {
    }

    /**
     * A mail sender that records instead of sending.
     */
    private static final class RecordingMailSender implements MailSender {

        private final List<SentMail> sent;

        RecordingMailSender(List<SentMail> sent) {
            this.sent = sent;
        }

        @Override
        public void send(String to, String subject, String body) {
            sent.add(new SentMail(to, subject, body));
        }
    }
}
