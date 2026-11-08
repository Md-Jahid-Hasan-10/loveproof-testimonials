package com.loveproof.testimonials.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.AuthProvider;
import com.loveproof.testimonials.domain.AuthTokenPurpose;
import com.loveproof.testimonials.exception.ConflictException;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.service.mail.MailSender;

/**
 * The account rules: registering, signing in, verifying an email, resetting a
 * password.
 *
 * <p>Two rules run through everything here. The email is the identity, and it
 * is matched without regard to case, so {@code Ada@example.com} and
 * {@code ada@example.com} are one account. And a failure never says which part
 * was wrong -- a wrong email and a wrong password produce the same message, so
 * the sign-in form cannot be used to find out whether an address is registered.
 */
@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    /** The message every failed sign-in produces. */
    static final String GENERIC_LOGIN_FAILURE = "That email and password combination is not correct.";

    private final AppUserRepository userRepository;

    private final AuthTokenService tokenService;

    private final MailSender mailSender;

    private final PasswordEncoder passwordEncoder;

    private final Clock clock;

    private final String appBaseUrl;

    /**
     * Creates the service.
     *
     * @param userRepository  the account store
     * @param tokenService    the token service
     * @param mailSender      the mail sender
     * @param passwordEncoder the password hasher
     * @param clock           the clock
     * @param appBaseUrl      the public base URL, used to build the links in
     *                        the emails
     */
    public AuthService(
            AppUserRepository userRepository,
            AuthTokenService tokenService,
            MailSender mailSender,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${loveproof.app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.appBaseUrl = appBaseUrl.replaceAll("/+$", "");
    }

    /**
     * Registers an account with an email and a password.
     *
     * <p>The account starts unverified. A verification email goes out; until
     * the link is clicked the user can sign in but cannot create a Space.
     *
     * @param email       the email address
     * @param password    the password
     * @param displayName the name to show
     * @return the new account
     * @throws ConflictException when the email is already registered
     */
    @Transactional
    public AppUser register(String email, String password, String displayName) {
        String normalisedEmail = normalise(email);

        if (userRepository.existsByEmailIgnoreCase(normalisedEmail)) {
            throw new ConflictException("An account with that email already exists.");
        }

        AppUser user = new AppUser(
                normalisedEmail,
                passwordEncoder.encode(password),
                displayName == null || displayName.isBlank() ? defaultNameFrom(normalisedEmail) : displayName.trim());

        user.setEmailVerified(false);
        user.setAuthProvider(AuthProvider.PASSWORD);
        AppUser saved = userRepository.save(user);

        sendVerificationEmail(saved);
        return saved;
    }

    /**
     * Signs in with an email and a password.
     *
     * @param email    the email address
     * @param password the password
     * @return the account
     * @throws NotFoundException when the credentials are wrong -- the message
     *                           is the same either way
     */
    @Transactional
    public AppUser signIn(String email, String password) {
        String normalisedEmail = normalise(email);

        Optional<AppUser> found = userRepository.findByEmailIgnoreCase(normalisedEmail);
        if (found.isEmpty()) {
            // Hash anyway, so a missing account and a wrong password take the
            // same time and cannot be told apart by timing.
            passwordEncoder.encode(password);
            throw new NotFoundException(GENERIC_LOGIN_FAILURE);
        }

        AppUser user = found.get();
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new NotFoundException(GENERIC_LOGIN_FAILURE);
        }

        if (!user.isEnabled()) {
            throw new NotFoundException(GENERIC_LOGIN_FAILURE);
        }

        user.setLastLoginAt(clock.instant());
        return userRepository.save(user);
    }

    /**
     * Signs in with Google, or creates the account.
     *
     * <p>When the email already belongs to a password account, the Google
     * identity is linked to that account and it is signed in -- one account,
     * not two (OPEN-A).
     *
     * @param email         the verified email from Google
     * @param displayName   the name from Google
     * @param googleSubject the subject identifier from Google
     * @return the account
     */
    @Transactional
    public AppUser signInWithGoogle(String email, String displayName, String googleSubject) {
        String normalisedEmail = normalise(email);

        Optional<AppUser> byGoogle = userRepository.findByGoogleSubject(googleSubject);
        if (byGoogle.isPresent()) {
            AppUser user = byGoogle.get();
            user.setLastLoginAt(clock.instant());
            return userRepository.save(user);
        }

        Optional<AppUser> byEmail = userRepository.findByEmailIgnoreCase(normalisedEmail);
        if (byEmail.isPresent()) {
            // The same person, arriving by a different door. Link the Google
            // identity to the existing account rather than making a second one.
            AppUser user = byEmail.get();
            user.setGoogleSubject(googleSubject);
            user.setLastLoginAt(clock.instant());
            return userRepository.save(user);
        }

        AppUser user = new AppUser(
                normalisedEmail,
                null,
                displayName == null || displayName.isBlank() ? defaultNameFrom(normalisedEmail) : displayName.trim());
        user.setAuthProvider(AuthProvider.GOOGLE);
        // Google has already verified the address.
        user.setEmailVerified(true);
        user.setGoogleSubject(googleSubject);
        user.setLastLoginAt(clock.instant());
        return userRepository.save(user);
    }

    /**
     * Sends a new verification email.
     *
     * @param userId the account to verify
     * @throws NotFoundException when there is no such account
     */
    @Transactional
    public void resendVerificationEmail(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No such account."));
        sendVerificationEmail(user);
    }

    /**
     * Verifies an email address using the token from the emailed link.
     *
     * @param plaintext the token from the link
     * @throws NotFoundException when the token is unknown, used, expired, or
     *                           of the wrong purpose
     */
    @Transactional
    public void verifyEmail(String plaintext) {
        Instant now = clock.instant();
        UUID userId = tokenService.redeem(plaintext, AuthTokenPurpose.VERIFY_EMAIL, now)
                .orElseThrow(() -> new NotFoundException("That verification link is not valid or has expired."));

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No such account."));
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    /**
     * Starts a password reset.
     *
     * <p>The method always succeeds, whatever the email is. Telling the caller
     * that an address is unknown would turn this endpoint into a way of finding
     * out who has an account.
     *
     * @param email the email address
     */
    @Transactional
    public void requestPasswordReset(String email) {
        String normalisedEmail = normalise(email);

        userRepository.findByEmailIgnoreCase(normalisedEmail).ifPresent(user -> {
            String token = tokenService.issue(user.getId(), AuthTokenPurpose.RESET_PASSWORD, clock.instant());
            String link = appBaseUrl + "/reset-password?token=" + token;
            mailSender.send(
                    user.getEmail(),
                    "Reset your LoveProof password",
                    "Someone asked to reset the password for this account.\n\n"
                            + "Open this link to choose a new password:\n"
                            + link
                            + "\n\nThe link works once and expires in 30 minutes. If this was not you, "
                            + "ignore this message: nothing has changed.");
        });
    }

    /**
     * Completes a password reset.
     *
     * @param plaintext the token from the emailed link
     * @param password the new password
     * @throws NotFoundException when the token is unknown, used or expired
     */
    @Transactional
    public void resetPassword(String plaintext, String password) {
        Instant now = clock.instant();
        UUID userId = tokenService.redeem(plaintext, AuthTokenPurpose.RESET_PASSWORD, now)
                .orElseThrow(() -> new NotFoundException("That reset link is not valid or has expired."));

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No such account."));
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
    }

    /**
     * Changes the password of a signed-in account.
     *
     * @param userId      the account
     * @param oldPassword the current password
     * @param newPassword the new password
     * @throws NotFoundException when the current password is wrong
     */
    @Transactional
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No such account."));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new NotFoundException("That password is not correct.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Changes the display name of a signed-in account.
     *
     * @param userId      the account
     * @param displayName the new name
     */
    @Transactional
    public void changeDisplayName(UUID userId, String displayName) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No such account."));
        user.setDisplayName(displayName.trim());
        userRepository.save(user);
    }

    /**
     * Sends the verification email for an account.
     *
     * @param user the account
     */
    private void sendVerificationEmail(AppUser user) {
        String token = tokenService.issue(user.getId(), AuthTokenPurpose.VERIFY_EMAIL, clock.instant());
        String link = appBaseUrl + "/verify-email?token=" + token;
        mailSender.send(
                user.getEmail(),
                "Verify your LoveProof email address",
                "Welcome to LoveProof.\n\n"
                        + "Confirm this email address to finish setting up your account:\n"
                        + link
                        + "\n\nThe link works once and expires in 24 hours.");
        LOGGER.info("Verification email queued for account {}.", user.getId());
    }

    /**
     * Normalises an email address.
     *
     * @param email the raw address
     * @return the trimmed, lower-cased address
     */
    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Derives a display name from an email address.
     *
     * @param email the normalised address
     * @return the part before the {@code @}, with the first letter capitalised
     */
    private static String defaultNameFrom(String email) {
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        if (local.isBlank()) {
            return "Account";
        }
        return Character.toUpperCase(local.charAt(0)) + local.substring(1);
    }
}
