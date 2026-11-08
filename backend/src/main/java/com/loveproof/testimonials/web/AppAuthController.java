package com.loveproof.testimonials.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.config.CurrentUser;
import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.service.AuthService;
import com.loveproof.testimonials.service.AuthTokenService;
import com.loveproof.testimonials.web.dto.auth.ChangePasswordRequest;
import com.loveproof.testimonials.web.dto.auth.ForgotPasswordRequest;
import com.loveproof.testimonials.web.dto.auth.RegisterRequest;
import com.loveproof.testimonials.web.dto.auth.ResetPasswordRequest;
import com.loveproof.testimonials.web.dto.auth.SignInRequest;
import com.loveproof.testimonials.web.dto.auth.UpdateProfileRequest;
import com.loveproof.testimonials.web.dto.auth.UserResponse;
import com.loveproof.testimonials.web.dto.auth.VerifyEmailRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * The customer authentication endpoints.
 *
 * <p>The path is {@code /api/app/auth} -- deliberately distinct from the
 * installation's own {@code /api/admin/session}, so the two surfaces never
 * share a session or an authority.
 */
@RestController
@RequestMapping("/api/app/auth")
public class AppAuthController {

    private final AuthService authService;

    private final AuthTokenService tokenService;

    private final AppUserRepository userRepository;

    private final SecurityContextRepository appSecurityContextRepository;

    /**
     * Creates the controller.
     *
     * @param authService                   the account rules
     * @param tokenService                  the token service
     * @param userRepository                the account store
     * @param appSecurityContextRepository  where the signed-in state is written
     */
    public AppAuthController(
            AuthService authService,
            AuthTokenService tokenService,
            AppUserRepository userRepository,
            @org.springframework.beans.factory.annotation.Qualifier("appSecurityContextRepository")
                    SecurityContextRepository appSecurityContextRepository) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.appSecurityContextRepository = appSecurityContextRepository;
    }

    /**
     * Registers an account.
     *
     * @param request the registration
     * @return the account
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = authService.register(request.email(), request.password(), request.displayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    /**
     * Signs in.
     *
     * @param request the credentials
     * @param httpRequest the request, for the session
     * @return the account
     */
    @PostMapping("/sign-in")
    public UserResponse signIn(@Valid @RequestBody SignInRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AppUser user = authService.signIn(request.email(), request.password());

        // The session is written by hand rather than through Spring's form
        // login, because the API is JSON: there is no form to submit and no
        // success handler to run.
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user.getId().toString(), null, java.util.List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        appSecurityContextRepository.saveContext(SecurityContextHolder.getContext(), httpRequest, httpResponse);

        return UserResponse.from(user);
    }

    /**
     * Signs out.
     *
     * @param httpRequest the request, for the session
     * @return no content
     */
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(HttpServletRequest httpRequest) {
        // Invalidating the session and clearing the context is what actually
        // ends the session; the cookie is expired by the session manager.
        if (httpRequest.getSession(false) != null) {
            httpRequest.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /**
     * The signed-in customer.
     *
     * @return the account
     */
    @GetMapping("/me")
    public UserResponse me() {
        AppUser user = userRepository.findById(CurrentUser.id())
                .orElseThrow(() -> new IllegalStateException("The signed-in account no longer exists."));
        return UserResponse.from(user);
    }

    /**
     * Verifies an email address.
     *
     * @param request the token from the link
     * @return no content
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    /**
     * Starts a password reset.
     *
     * @param request the email address
     * @return no content -- always, so the endpoint does not reveal whether
     *         the address is registered
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }

    /**
     * Completes a password reset.
     *
     * @param request the token and the new password
     * @return no content
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * Changes the password of the signed-in customer.
     *
     * @param request the current and new passwords
     * @return no content
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * Changes the display name of the signed-in customer.
     *
     * @param request the new name
     * @return the updated account
     */
    @PostMapping("/profile")
    public UserResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        authService.changeDisplayName(CurrentUser.id(), request.displayName());
        AppUser user = userRepository.findById(CurrentUser.id())
                .orElseThrow(() -> new IllegalStateException("The signed-in account no longer exists."));
        return UserResponse.from(user);
    }

    /**
     * Whether a verification link is still good.
     *
     * <p>Used by the frontend to tell the user the link has expired before
     * they submit the form.
     *
     * @param token the token from the link
     * @return whether the token can still be used
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, Boolean>> checkVerificationToken(@org.springframework.web.bind.annotation.RequestParam String token) {
        boolean usable = tokenService.isUsable(
                token,
                com.loveproof.testimonials.domain.AuthTokenPurpose.VERIFY_EMAIL,
                java.time.Instant.now());
        return ResponseEntity.ok(Map.of("valid", usable));
    }
}
